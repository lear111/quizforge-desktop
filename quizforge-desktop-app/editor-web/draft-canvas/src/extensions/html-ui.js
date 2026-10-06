import { element, RendererMode } from '../shared/renderer/contract.js';
import {connectFrame} from './frame-host.js';
import {defaultUi,configureUi} from '../shared/ui/preferences.js';
import {defaultLayout,configureLayout,createDomLayout} from '../shared/ui/layout.js';
import {validateMethodArguments} from './protocol.js';
import {createPermissionPolicy} from './permissions.js';
import {hostSdk} from './compatibility.js';
import {compileDataValidation,validationFailure} from './data-validation.js';

const clone = value => value == null ? value : JSON.parse(JSON.stringify(value));
const ok = data => ({ ok: true, data: clone(data) });
const failure = (code, message) => ({ ok: false, error: { code, message, retryable: false } });
const operationFailure = (error, fallback) => error.code==='DATA_VALIDATION_FAILED'?validationFailure(error):failure(
  ['EXTENSION_TIMEOUT','EXTENSION_FAILED','EXTENSION_UNAVAILABLE'].includes(error.code)?error.code:fallback,error.message);

/** HTML is page structure only; scripts and styles are separate declared package assets. */
export function validatePageHtml(html) {
  if (typeof html !== 'string' || !html.trim()) throw new TypeError('HTML page is required');
  if (/<\s*(script|iframe|frame|object|embed|base|link|meta)\b/i.test(html)
      || /\s(?:on\w+|src|srcset|href|action|formaction)\s*=/i.test(html))
    throw new TypeError('HTML may not embed scripts or external resources; use manifest assets');
  return html;
}
function mountPage(root, html, styles) {
  validatePageHtml(html);
  const page = element('section', 'qf-extension-page');
  page.__qfAssets={html,styles};
  root.append(page);
  return page;
}
function optionIds(question) {
  const ids = new Set();
  function visit(node) {
    if (Array.isArray(node)) { node.forEach(visit); return; }
    if (!node || typeof node !== 'object') return;
    if (typeof node.id === 'string' && node.id.startsWith('opt_')) ids.add(node.id);
    Object.values(node).forEach(visit);
  }
  visit(question.payload); return ids;
}
export function parseHtmlPresentation(q) {
  const p = q.presentation;
  if (!p || typeof p.extensionId !== 'string' || !p.question || typeof p.question.payload !== 'object'
      || !p.answer || typeof p.answer !== 'object' || Array.isArray(p.answer))
    throw new TypeError('Missing HTML extension presentation');
  if (q.state !== 'SUBMITTED' && p.reference != null) throw new TypeError('Unsubmitted question exposes its answer');
  const available = optionIds(p.question);
  const selected = p.answer.selectedOptionIds || q.selectedOptionIds || [];
  if (!Array.isArray(selected) || new Set(selected).size !== selected.length || selected.some(id => !available.has(id))) throw new TypeError('Invalid selected option IDs');
  return { presentation: clone(p), options: [], available, selectedOptionIds: [...selected] };
}

/** Lifecycle stays inside the adapter, so extension authors write ordinary page event handlers. */
function pageRuntime(page, source, { editor, initial, caps, root, policy, validation, initialValidation }) {
  let current = clone(initial), draft = editor ? clone(initial) : null, destroyed = false;
  let readOnly = !editor && caps.mode === RendererMode.READ_ONLY_HISTORY, interaction = 'INTERACT';
  let presentationSuspended=false, presentationWritable=false;
  const listeners = [], subscriptions = new Set(), pending = new Set(), disposers=[];
  let lastError = null, initializationFailed = false, frameHost=null;
  validation.retain?.();
  let preferences = defaultUi(editor ? 'EDITOR' : 'PRACTICE');
  let layout=defaultLayout(editor?'EDITOR':'PRACTICE'),pageReady=false;
  const layoutHost=caps.layoutHost || createDomLayout(caps.layoutRoot || (editor?root.closest('#qf-editor-shell'):null) || root,
    {mode:editor?'EDITOR':'PRACTICE',getHeight:caps.layoutHeight});
  if(!caps.layoutHost)disposers.push(()=>layoutHost.destroy());
  function publishLayout(){if(!destroyed)layoutHost.configure(layout,{ready:pageReady});}
  publishLayout();
  function publishUi() {
    if (destroyed) return;
    caps.uiChanged?.(preferences);
    if (editor) caps.configureUi?.(preferences);
  }
  const mode = () => editor ? 'EDITOR' : caps.preview ? 'PREVIEW' : readOnly ? 'HISTORY' : 'PRACTICE';
  const writable = () => !destroyed && !readOnly && interaction === 'INTERACT' && current.state !== 'SUBMITTED' && (caps.canInteract?.() ?? true);
  function track(promise) {
    const value = Promise.resolve(promise); pending.add(value);
    value.catch(error => { if(error.code!=='DATA_VALIDATION_FAILED')lastError = error; showError(error.message); }).finally(() => pending.delete(value));
    return value;
  }
  function showError(message) {
    let node = page.querySelector('[data-qf-error]');
    if (!node) { node = element('p', 'practice-error'); node.dataset.qfError = ''; page.append(node); }
    node.textContent = message || ''; node.hidden = !message;
  }
  // A retained card is a frozen presentation while navigation saves and prepares its replacement.
  // Its real write guards remain live; pending UI reads must not collapse the visible old card.
  function notify() { if(!destroyed&&!presentationSuspended)frameHost?.state(); }
  disposers.push(policy.subscribe(notify));
  if(caps.subscribeHost)disposers.push(caps.subscribeHost(notify));
  const dom = Object.freeze({ root: page,
    $(selector) { return page.querySelector(selector); },
    on(node, event, listener) {
      node.addEventListener(event, listener); listeners.push([node, event, listener]);
      return () => node.removeEventListener(event, listener);
    }
  });
  const questionData = () => editor ? draft : current.presentation.question;
  function resultData() {
    if (!current.result) return null;
    return { ...clone(current.result), reference: clone(current.presentation.reference) };
  }
  async function shellCommand(action,argument) {
    if (!editor || destroyed) return failure('CAPABILITY_DENIED','此页不能操作题库编辑会话');
    if (!caps.shellCommand) return failure('CAPABILITY_UNAVAILABLE','此宿主未提供题库编辑操作');
    const reply=await caps.shellCommand(action,argument);if(!destroyed)notify();return reply;
  }
  async function pageState() {
    if(destroyed)return failure('PAGE_CLOSED','题型页面已关闭');
    if(editor)return caps.shellState?ok(caps.shellState()):failure('CAPABILITY_UNAVAILABLE','此宿主未提供题库会话');
    return caps.pageState?caps.pageState():failure('CAPABILITY_UNAVAILABLE','此宿主未提供页面会话');
  }
  async function pageCommand(action,argument) {
    if(destroyed)return failure('PAGE_CLOSED','题型页面已关闭');
    if(editor)return action==='learning.mode'?failure('CAPABILITY_DENIED','编辑页不能切换学习模式'):shellCommand(action,argument);
    if(!caps.pageCommand)return failure('CAPABILITY_UNAVAILABLE','此宿主未提供页面操作');
    const reply=await caps.pageCommand(action,argument);if(!destroyed)notify();return reply;
  }
  async function boardCommand(action,argument) {
    if(destroyed)return failure('PAGE_CLOSED','题型页面已关闭');
    if(editor||!caps.boardCommand)return failure('CAPABILITY_UNAVAILABLE','此页没有白板');
    try {return ok(await caps.boardCommand(action,argument));}
    catch(error){return failure(error.code||'WHITEBOARD_FAILED',error.message);}
  }
  const QF = Object.freeze({
    dom,
    host: Object.freeze({
      async getContext() { const available=(await pageState()).ok;
        const presentedWritable=presentationSuspended?presentationWritable&&!destroyed&&!readOnly&&current.state!=='SUBMITTED':writable();
        return ok({ mode: mode(), state: current.state || null,
        sdk:hostSdk,
        permissions:{declared:policy.declared,granted:policy.granted},
        capabilities: { editQuestion: editor&&policy.can('editor.update'), editAnswer: !editor&&presentedWritable&&policy.can('answer.update'), submit: !editor&&presentedWritable&&Boolean(caps.requestSubmit)&&policy.can('practice.submit'), retry: !editor&&!readOnly&&current.state==='SUBMITTED'&&Boolean(caps.requestRetry)&&policy.can('practice.retry'), ai: false,
          saveBank:editor&&available&&policy.can('bank.save'), navigate:available&&policy.can('navigation.goTo'), manageSources:editor&&available&&policy.can('sources.add'),
          viewSources:available&&policy.can('sources.open'),whiteboard:!editor&&Boolean(caps.boardState)&&policy.granted.some(name=>name.startsWith('whiteboard.')),changeLearningMode:!editor&&available&&policy.can('learning.setMode'),layout:true } }); },
      subscribe(listener) { subscriptions.add(listener); return () => subscriptions.delete(listener); }
    }),
    ids: Object.freeze({ create(prefix = 'opt_') { return caps.newId?.(prefix) || prefix + (globalThis.crypto?.randomUUID?.() || Math.random().toString(36).slice(2)).replace(/-/g, '_'); } }),
    editor: Object.freeze({
      async getData() { return editor ? ok(draft) : failure('CAPABILITY_DENIED', '此页不能编辑题目'); },
      async update(patch) {
        if (!editor || destroyed) return failure('CAPABILITY_DENIED', '此页不能编辑题目');
        if (!patch || typeof patch !== 'object' || Array.isArray(patch)) return failure('INVALID_DATA', '题目更新必须是对象');
        if (Object.prototype.hasOwnProperty.call(patch,'id') && patch.id !== draft.id || Object.prototype.hasOwnProperty.call(patch,'type') && patch.type !== draft.type) return failure('INVALID_DATA', '不能更改题目身份');
        const candidate = { ...draft, ...clone(patch) };
        try {
          await validation.question(candidate);
          if(destroyed)return failure('PAGE_CLOSED','题型页面已关闭');
          await caps.changed?.(clone(candidate));
          draft = candidate;return ok(draft);
        } catch(error) {return operationFailure(error,'EDITOR_UPDATE_FAILED');}
      },
      async save() { if (!editor) return failure('CAPABILITY_DENIED', '此页不能保存题目'); await flush(); await caps.changed?.(clone(draft)); return ok({ savedToDraft: true }); }
    }),
    bank: Object.freeze({
      getState:()=>editor?pageState():Promise.resolve(failure('CAPABILITY_DENIED','此页不能编辑题库')),
      save:()=>shellCommand('save'),addQuestion:type=>shellCommand('add',type),
      duplicateQuestion:()=>shellCommand('duplicate'),deleteQuestion:()=>shellCommand('delete')
    }),
    navigation: Object.freeze({
      getState:pageState,
      goTo:index=>pageCommand('navigate',index),
      async previous(){const state=await pageState();return state.ok?pageCommand('navigate',state.data.index-1):state;},
      async next(){const state=await pageState();return state.ok?pageCommand('navigate',state.data.index+1):state;}
    }),
    sources: Object.freeze({
      async list() { const state=await pageState();return state.ok?ok(state.data.sources):state; },
      add:link=>shellCommand('source.add',link),
      remove:index=>shellCommand('source.remove',index),
      open:index=>pageCommand('source.open',index)
    }),
    learning: Object.freeze({
      async getMode(){const state=await pageState();return state.ok?ok(state.data.learningMode||'EDITOR'):state;},
      setMode:mode=>pageCommand('learning.mode',mode),
      async toggleMode(){const state=await pageState();return state.ok?pageCommand('learning.mode',state.data.learningMode==='DRAFT'?'PRACTICE':'DRAFT'):state;}
    }),
    whiteboard: Object.freeze({
      async getState(){if(destroyed)return failure('PAGE_CLOSED','题型页面已关闭');return !editor&&caps.boardState?ok(caps.boardState()):failure('CAPABILITY_UNAVAILABLE','此页没有白板');},
      setTool:tool=>boardCommand('tool',tool),undo:()=>boardCommand('undo'),redo:()=>boardCommand('redo'),clear:()=>boardCommand('clear'),
      setAppearance:patch=>boardCommand('paper',patch),setZoom:value=>boardCommand('zoom',value),zoomBy:factor=>boardCommand('zoomBy',factor)
    }),
    practice: Object.freeze({
      async getState(){return editor?failure('CAPABILITY_DENIED','编辑页没有练习状态'):ok({index:current.index,total:current.total,type:current.type,state:current.state,maxScore:current.maxScore,result:resultData()});},
      async getQuestion() { return editor ? failure('CAPABILITY_DENIED', '编辑页请读取题目草稿') : ok(questionData()); },
      async getResult() { return editor ? failure('CAPABILITY_DENIED', '编辑页没有练习结果') : ok(resultData()); },
      async submit() {
        if (editor || !writable()) return failure('READ_ONLY', '当前状态不可提交');
        if (!caps.requestSubmit) return failure('CAPABILITY_UNAVAILABLE', '此宿主提供独立的提交按钮');
        try { const receipt=await caps.requestSubmit();return ok({confirmationRequired:receipt?.confirmationRequired===true,result:resultData()}); }
        catch (error) { return operationFailure(error,'SUBMIT_FAILED'); }
      },
      async retry() {
        if (editor || readOnly || current.state !== 'SUBMITTED' || !(caps.canInteract?.() ?? true)) return failure('READ_ONLY', '当前状态不可重试');
        if (!caps.requestRetry) return failure('CAPABILITY_UNAVAILABLE', '此宿主提供独立的重试按钮');
        try { await caps.requestRetry(); return ok(null); }
        catch (error) { return failure('RETRY_FAILED', error.message); }
      }
    }),
    answer: Object.freeze({
      async get() { return editor ? failure('CAPABILITY_DENIED', '编辑页没有用户作答') : ok(current.presentation.answer); },
      async update(answer) {
        if (editor || !writable()) return failure('READ_ONLY', '此页或当前状态不可作答');
        try {await validation.answer(answer);}catch(error){return validationFailure(error);}
        if(!writable())return failure('READ_ONLY','此页或当前状态不可作答');
        try {
          await track(caps.answerChanged({ answer: clone(answer), ...(answer.selectedOptionIds ? { selectedOptionIds: clone(answer.selectedOptionIds) } : {}) }));
          lastError=null;showError('');
          return ok(current.presentation.answer);
        } catch (error) { return operationFailure(error,'ANSWER_SAVE_FAILED'); }
      },
      async flush() { await flush(); return ok(null); }
    }),
    content: Object.freeze({
      async resolve(content){if(destroyed)return failure('PAGE_CLOSED','题型页面已关闭');if(!content||!['TEXT','RICH','DOCUMENT'].includes(content.kind))return failure('INVALID_DATA','无效富文本内容');return ok(await caps.resolveContent?.(content)||content);},
      async edit(content){if(!editor||destroyed)return failure('CAPABILITY_DENIED','此页不能编辑富文本');if(!content||!['TEXT','RICH','DOCUMENT'].includes(content.kind))return failure('INVALID_DATA','无效富文本内容');return ok(await caps.editContent?.(clone(content))??content);}
    }),
    layout: Object.freeze({
      configure(patch){
        if(destroyed)return failure('PAGE_CLOSED','题型页面已关闭');
        try {const next=configureLayout(layout,patch);layoutHost.configure(next,{ready:pageReady});layout=next;return ok(layout);}
        catch(error){return failure('INVALID_LAYOUT',error.message);}
      },
      getConfiguration(){return destroyed?failure('PAGE_CLOSED','题型页面已关闭'):ok(layout);},
      getState(){return destroyed?failure('PAGE_CLOSED','题型页面已关闭'):ok(layoutHost.getState());}
    }),
    ui: Object.freeze({
      configure(patch) {
        if (destroyed) return failure('PAGE_CLOSED','题型页面已关闭');
        try { preferences=configureUi(preferences,patch,editor?'EDITOR':'PRACTICE');publishUi();return ok(preferences); }
        catch(error){return failure('INVALID_UI',error.message);}
      },
      getConfiguration() { return ok(preferences); },
      mountControls(node) {if(!page.contains(node))throw new TypeError('Controls must belong to this type page');node.dataset.qfHostControls='';},
      mountActions(node) { node.dataset.qfActions = ''; },
      notify(message) { showError(message); }
    })
  });
  let resolveReady,rejectReady;
  const ready=new Promise((resolve,reject)=>{resolveReady=resolve;rejectReady=reject;});ready.catch(()=>{});
  const names=['host.getContext','editor.getData','editor.update','editor.save','bank.getState','bank.save','bank.addQuestion','bank.duplicateQuestion','bank.deleteQuestion','navigation.getState','navigation.goTo','navigation.previous','navigation.next','sources.list','sources.add','sources.remove','sources.open','learning.getMode','learning.setMode','learning.toggleMode','whiteboard.getState','whiteboard.setTool','whiteboard.undo','whiteboard.redo','whiteboard.clear','whiteboard.setAppearance','whiteboard.setZoom','whiteboard.zoomBy','practice.getState','practice.getQuestion','practice.getResult','practice.submit','practice.retry','answer.get','answer.update','answer.flush','content.resolve','content.edit','ui.configure','layout.configure'];
  const methods=new Map(names.map(name=>{const [group,method]=name.split('.');return [name,QF[group][method]];}));
  const bytes=new Uint32Array(4);crypto.getRandomValues(bytes);
  const pageAssets=page.__qfAssets;
  const startFrame=()=>connectFrame(page,source,{
    ...pageAssets,
    boot:{session:Array.from(bytes,n=>n.toString(16)).join('-'),mode:editor?'EDITOR':'PRACTICE',questionId:initial.id||initial.questionId,ui:preferences,layout,layoutState:layoutHost.getState(),relayWheel:Boolean(caps.boardState)},
    invoke(name,args){
      if(destroyed)return failure('PAGE_CLOSED','题型页面已关闭');
      if(caps.isCurrent && !caps.isCurrent())return failure('PAGE_CLOSED','题目已切换');
      if(caps.isReady&&!caps.isReady()&&['answer.update','practice.submit','practice.retry','editor.update','editor.save','bank.save','bank.addQuestion','bank.duplicateQuestion','bank.deleteQuestion','navigation.goTo','navigation.previous','navigation.next','sources.add','sources.remove','sources.open','content.edit'].includes(name))
        return failure('CAPABILITY_UNAVAILABLE','题卡正在准备');
      const method=methods.get(name);if(!method)return failure('UNKNOWN_METHOD','未开放的题型接口');
      try{validateMethodArguments(name,args);}catch(error){return failure('INVALID_ARGUMENT',error.message);}
      if(!policy.can(name))return failure('PERMISSION_DENIED','拓展未声明或未获授权使用该接口：'+name);
      return method(...args);
    },
    getState:()=>({layoutState:layoutHost.getState(),interaction}),interaction:()=>interaction,
    onReady(){if(destroyed)return;pageReady=true;page.dataset.qfReady='true';publishLayout();publishUi();resolveReady();notify();},
    onFailure(error){
      if(destroyed)return;initializationFailed=true;lastError=error;showError(error.message);rejectReady(error);
      if(caps.reloadPage&&!page.querySelector('[data-qf-reload]')){
        const button=element('button','qf-page-reload','重新加载题卡');button.type='button';button.dataset.qfReload='';page.append(button);
        dom.on(button,'click',async()=>{button.disabled=true;try{await caps.reloadPage();}catch(failure){showError(failure.message);button.disabled=false;}});
      }
    }
  });
  if(initialValidation?.then)initialValidation.then(()=>{
    if(!destroyed)frameHost=startFrame();
  }).catch(error=>{if(!destroyed){initializationFailed=true;lastError=error;showError(error.message);rejectReady(error);}});
  else frameHost=startFrame();
  delete page.__qfAssets;
  async function flush() { await ready; await frameHost.flush();while (pending.size) await Promise.all([...pending]); if (lastError) throw lastError; }

  return {
    ready,
    getDraft:()=>clone(draft),getUiPreferences:()=>preferences,hasInitializationError:()=>initializationFailed,flush,
    update(next){current=clone(next);notify();},
    setReadOnly(value){if(readOnly&&!value)throw new TypeError('Read-only capability cannot be upgraded');if(readOnly!==value){readOnly=value;notify();}},
    setInteractionMode(value){if(interaction!==value){interaction=value;notify();}},
    suspendPresentation(){if(!presentationSuspended){presentationWritable=writable();presentationSuspended=true;}},
    resumePresentation(){if(presentationSuspended){presentationSuspended=false;notify();}},
    getAnswerIntent:()=>({answer:clone(current.presentation?.answer||{}),...(current.presentation?.answer?.selectedOptionIds?{selectedOptionIds:clone(current.presentation.answer.selectedOptionIds)}:{})}),
    hasAnswer(){return Object.values(current.presentation?.answer||{}).some(value=>Array.isArray(value)?value.length>0:value!=null&&value!==''&&(typeof value!=='object'||Object.keys(value).length>0));},
    flushAnswer:flush,
    renderResult(){const node=element('section');node.hidden=true;return node;},
    focusTarget(id){return frameHost?.focus(id)||page;},
    destroy(){
      if(destroyed)return;destroyed=true;
      const completion=frameHost?.destroy()||Promise.resolve();rejectReady(new Error('题型页面已关闭'));subscriptions.clear();
      disposers.forEach(dispose=>dispose());listeners.forEach(([node,event,fn])=>node.removeEventListener(event,fn));
      return completion.then(()=>{validation.release?.();const retired=page.closest('[data-qf-retired-root]');page.remove();if(retired&&!retired.querySelector('.qf-type-frame'))retired.remove();});
    }
  };
}
export function createHtmlRenderer(type,asset,granted=[],policy=createPermissionPolicy(type.permissions,granted),validation=compileDataValidation(type,asset)){
  validatePageHtml(asset.rendererHtml);
  return {id:`html.${type.id}.v2`,questionType:type.id,label:type.label,selectionMode:'EXTENSION',parse:parseHtmlPresentation,
    validateQuestion:validation.question,
    mount(root,question,caps){const initialValidation=validation.answer(question.presentation.answer);return pageRuntime(mountPage(root,asset.rendererHtml,asset.stylesSource),asset.rendererSource,{editor:false,initial:question,caps,root,policy,validation,initialValidation});}};
}
export function createHtmlEditor(type,asset,granted=[],policy=createPermissionPolicy(type.permissions,granted),validation=compileDataValidation(type,asset)){
  validatePageHtml(asset.editorHtml);
  return {questionType:type.id,mount(root,question,caps){const initialValidation=validation.question(question);return pageRuntime(mountPage(root,asset.editorHtml,asset.stylesSource),asset.editorSource,{editor:true,initial:question,caps,root,policy,validation,initialValidation});}};
}
