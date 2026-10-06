import './rules-runtime.js';
import { registerQuestionExtension, QuestionRendererRegistry } from '../shared/renderer/registry.js';
import { element, RendererMode } from '../shared/renderer/contract.js';
import { readContent, renderContent } from '../shared/renderer/content.js';
import { resultSection } from '../shared/renderer/result.js';
import {createHtmlRenderer,createHtmlEditor} from './html-ui.js';
import {isolatedRules} from './isolated-rules.js';
import {replaceChildrenRetainingFrames} from './protocol.js';
import {stageEditor} from './editor-transition.js';
import {createDomLayout,defaultLayout} from '../shared/ui/layout.js';
import {readPermissions,createPermissionPolicy} from './permissions.js';
const authorizations=new Map();
import {requireCompatibleManifest} from './compatibility.js';
import {compileDataValidation,checkedRules} from './data-validation.js';

export function readExtensionQuestion(question) {
  const p = question.presentation;
  if (!p || typeof p.extensionId !== 'string' || !Number.isInteger(p.dataVersion) || !p.question
      || typeof p.question.payload !== 'object' || !p.answer || Array.isArray(p.answer) || typeof p.answer !== 'object')
    throw new TypeError('Missing extension presentation');
  if (question.state !== 'SUBMITTED' && p.reference != null) throw new TypeError('Unsubmitted extension must not expose answers');
  return { options: [], available: new Set(), selectedOptionIds: [], presentation: JSON.parse(JSON.stringify(p)) };
}

/** Compile only SDK 2 HTML packages; no built-in registration or legacy fallback. */
export function compileQuestionExtension(value,sharedPolicies=null) {
  const bundle = typeof value === 'string' ? JSON.parse(value) : value;
  const manifest = bundle?.manifest;
  requireCompatibleManifest(manifest);
  if (!Array.isArray(manifest.types) || !manifest.types.length)
    throw new TypeError('Unsupported question extension manifest: HTML SDK 2 required');
  const assets = bundle.assets || manifest.types.map(type => ({ typeId: type.id, ...bundle }));
  const renderers = [], editors = [], checks=[], permissionPolicies=new Map(), validators=new Map(), rules = typeof window==='undefined'?globalThis.QuestionRules.createRegistry():isolatedRules({...bundle,assets}), types = new Set();
  const QF = Object.freeze({ defineQuestionType: definition => rules.defineQuestionType(definition),
    installDefaultQuestion: (type, template) => rules.installDefaultQuestion(type, template) });
  try {for (const type of manifest.types) {
    readPermissions(type.permissions);
    if (types.has(type.id)) throw new TypeError('Duplicate extension type');
    types.add(type.id);
    const asset = assets.find(a => a.typeId === type.id);
    if (!asset || typeof asset.rulesSource !== 'string' || typeof asset.editorSource !== 'string' || typeof asset.rendererSource !== 'string')
      throw new TypeError(`Missing HTML package assets for ${type.id}`);
    // Reject incomplete saves before replacing the last usable page definitions.
    for (const source of [asset.editorSource, asset.rendererSource])
      new Function('QF', '"use strict"; return (async()=>{\n' + source + '\n})();');
    const template = typeof asset.defaultQuestion === 'string' ? JSON.parse(asset.defaultQuestion) : asset.defaultQuestion;
    const validation=compileDataValidation(type,asset);validators.set(type.id,validation);
    const check=validation.question(template);if(check?.then){check.catch(()=>{});checks.push(check);}
    new Function('QF','"use strict";\n'+asset.rulesSource); // Syntax check only; never execute package rules in the host document.
    if(typeof window==='undefined'){
      rules.installDefaultQuestion(type.id, template);
      new Function('QF', '"use strict";\n' + asset.rulesSource)(QF);
      if (!rules.has(type.id)) throw new TypeError(`Missing rules for ${type.id}`);
    }
    const granted=readPermissions(bundle.grantedPermissions?.[type.id]);
    const policy=sharedPolicies?.get(type.id)||createPermissionPolicy(type.permissions,granted);
    permissionPolicies.set(type.id,policy);
    renderers.push(createHtmlRenderer(type, asset,granted,policy,validation)); editors.push(createHtmlEditor(type, asset,granted,policy,validation));
  }}catch(error){rules.destroy?.();for(const validation of validators.values())validation.destroy?.();throw error;}
  const checked=checkedRules(rules,validators);
  const ready=checks.length?Promise.all(checks).catch(error=>{checked.destroy();throw error;}):null;
  ready?.catch(()=>{});
  return Object.freeze({ manifest: JSON.parse(JSON.stringify(manifest)), renderers, editors, rules:checked, ready,
    assets, permissionPolicies, stylesSource: '' });
}
export function installQuestionExtension(value) {
  const bundle = typeof value === 'string' ? JSON.parse(value) : value;
  const manifest = bundle?.manifest;
  if (QuestionRendererRegistry.extensions().some(m => m.id === manifest?.id && m.version === manifest?.version))
    return { installed: false, id: manifest.id, version: manifest.version };
  const compiled = compileQuestionExtension(bundle);
  const commit=()=>{
    try {registerQuestionExtension(manifest, compiled.renderers, compiled.editors);}
    catch(error){compiled.rules.destroy();throw error;}
    authorizations.set(`${manifest.id}@${manifest.version}`,{sha256:bundle.sha256,policies:compiled.permissionPolicies,rules:compiled.rules});
    return { installed: true, id: manifest.id, version: manifest.version };
  };
  return compiled.ready?compiled.ready.then(commit):commit();
}

/** Development replaces page assets for the exact installed version; Core rules remain pinned. */
export function replaceDevelopmentExtension(value) {
  const bundle = typeof value === 'string' ? JSON.parse(value) : value;
  const authorization=authorizations.get(`${bundle.manifest?.id}@${bundle.manifest?.version}`);
  if(authorization&&authorization.sha256!==bundle.sha256)throw new TypeError('Development preview cannot change the installed package hash');
  const compiled = compileQuestionExtension(bundle,authorization?.policies);
  const commit=()=>{
    try {registerQuestionExtension(compiled.manifest, compiled.renderers, compiled.editors, { replace: true });}
    catch(error){compiled.rules.destroy();throw error;}
    // Retiring frames finish their accepted operations before releasing the old validation worker.
    if(authorization){authorization.rules?.retire?.();authorization.rules=compiled.rules;}
    if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('qf-extension-updated', {
      detail: { types: compiled.manifest.types.map(type => type.id), revision: bundle.revision || bundle.sha256 }
    }));
    return true;
  };
  return compiled.ready?compiled.ready.then(commit):commit();
}
/** Trusted parent host only; the sandbox has no bridge to change its own authorization. */
export function updateQuestionExtensionPermissions({id,version,sha256,grantedPermissions}) {
  const authorization=authorizations.get(`${id}@${version}`);
  if(!authorization)return false;
  if(authorization.sha256!==sha256)throw new TypeError('Permission update does not match the installed package hash');
  if(!grantedPermissions||typeof grantedPermissions!=='object'||Array.isArray(grantedPermissions))throw new TypeError('Invalid permission update');
  const checked=new Map();
  for(const type of Object.keys(grantedPermissions))if(!authorization.policies.has(type))throw new TypeError('Undeclared permission type');
  for(const [type,policy] of authorization.policies){
    const values=readPermissions(grantedPermissions[type]);
    if(values.some(name=>!policy.declared.includes(name)))throw new TypeError('Cannot grant an undeclared permission');
    checked.set(type,values);
  }
  // Validate the complete update before mutating any policy.
  checked.forEach((values,type)=>authorization.policies.get(type).update(values));return true;
}
export function denyAllQuestionExtensionPermissions(){
  authorizations.forEach(record=>record.policies.forEach(policy=>policy.update([])));
}

export function mountExtensionEditor(root, type, question, onChange = () => {}, overrides = {}) {
  const definition = QuestionRendererRegistry.editor(type);
  if (!definition) throw new TypeError(`Missing editor for ${type}`);
  return definition.mount(root, JSON.parse(JSON.stringify(question)), Object.freeze({
    element, renderContent, readContent,
    layoutHeight:()=>window.extensionEditorHost?.viewportHeight?.() || window.innerHeight,
    configureUi:preferences=>window.extensionEditorHost?.configureUi?.(question.id,JSON.stringify(preferences)),
    ...(window.bankEditorHost ? {
      shellCommand:(action,argument)=>window.qfEditorShell.command(action,argument),
      shellState:()=>window.qfEditorShell.getState()
    } : {}),
    async editContent(content) {
      const encoded = await window.editorHost?.editContent?.(JSON.stringify(content));
      return encoded ? JSON.parse(encoded) : content;
    },
    async resolveContent(content) {
      const encoded = await window.editorHost?.resolveContent?.(JSON.stringify(content));
      return encoded ? JSON.parse(encoded) : readContent(content,'editor content');
    },
    newId(prefix='item_') {
      return window.editorHost?.newId?.(prefix) || prefix + (globalThis.crypto?.randomUUID?.() || Math.random().toString(36).slice(2)+Date.now().toString(36)).replace(/-/g,'');
    },
    changed(value) { return onChange(JSON.parse(JSON.stringify(value))); }, ...overrides
  }));
}
export async function flushExtensionEditor(instance, snapshot) {
  if(!instance?.hasInitializationError?.())await instance?.flush?.();
  return snapshot();
}

if (typeof window !== 'undefined') {
  let editorMountSequence=0,editorPresentation=null,pendingEditor=null,editorLayout=null;
  window.questionExtensions = Object.freeze({ install: installQuestionExtension, loadFromSource: installQuestionExtension,
    updatePermissions:updateQuestionExtensionPermissions,
    denyAllPermissions:denyAllQuestionExtensionPermissions,
    replaceDevelopment: replaceDevelopmentExtension,
    list: () => QuestionRendererRegistry.extensions(),
    mountEditor(type, container, question, overrides) {
      return mountExtensionEditor(container, type, typeof question === 'string' ? JSON.parse(question) : question,
        next => window.editorHost?.changed?.(JSON.stringify(next)), overrides);
    },
    mountEditorFromJson(type, value) {
      const next=typeof value==='string'?JSON.parse(value):value,sequence=++editorMountSequence;
      const mount=()=>{
      if(sequence!==editorMountSequence)return false;
      const root = document.querySelector('#extension-editor');
      if (!root) throw new Error('Extension editor root is missing');
      let current = next;
      pendingEditor?.cancel();
      if(!editorLayout){editorLayout=createDomLayout(root.closest('#qf-editor-shell')||root,{mode:'EDITOR',getHeight:()=>window.extensionEditorHost?.viewportHeight?.()||window.innerHeight});editorLayout.configure(defaultLayout('EDITOR'));}
      let stagedUi=null,stagedLayout=null;
      const transition=stageEditor(root,editorPresentation,(body,isReady)=>mountExtensionEditor(body,type,current,async next=>{
        if(sequence!==editorMountSequence)throw new Error('题目已切换');
        await window.extensionEditorHost?.changed?.(JSON.stringify(next));current=next;
      },{
        isCurrent:()=>sequence===editorMountSequence,isReady,
        layoutHost:{getState:editorLayout.getState,configure(...args){
          if(sequence!==editorMountSequence)return;
          if(isReady())editorLayout.configure(...args);else stagedLayout=args;
        }},
        configureUi(preferences){
          if(sequence!==editorMountSequence)return;
          if(isReady()){
            window.qfEditorShell?.configureUi?.(preferences);
            window.extensionEditorHost?.configureUi?.(current.id,JSON.stringify(preferences));
          }else stagedUi=preferences;
        },async reloadPage(){
        const instance=window.extensionEditorInstance;
        const draft=instance?.getDraft?.()??current;
        await window.questionExtensions.mountEditorFromJson(type,draft);
      }}),presentation=>{
        if(sequence!==editorMountSequence)return;
        editorPresentation=presentation;pendingEditor=null;
        if(stagedLayout)editorLayout.configure(...stagedLayout);
        if(stagedUi){
          window.qfEditorShell?.configureUi?.(stagedUi);
          window.extensionEditorHost?.configureUi?.(current.id,JSON.stringify(stagedUi));
        }
      });
      pendingEditor=transition;
      window.extensionEditorInstance = transition.instance;
      window.extensionEditorType = type;
      window.extensionEditorSnapshot = () => JSON.stringify(window.extensionEditorInstance?.getDraft?.() ?? current);
      window.extensionEditorFlush = async () => {
        const instance = window.extensionEditorInstance;
        return flushExtensionEditor(instance, () => {
          if (instance !== window.extensionEditorInstance) throw new Error('Editor changed during save');
          return window.extensionEditorSnapshot();
        });
      };
      window.extensionEditorFlushToHost = id => window.extensionEditorFlush().then(
        draft => window.extensionEditorHost.flushed(id, draft),
        failure => window.extensionEditorHost.flushFailed(id, failure.message || String(failure)));
      return transition.ready.then(()=>sequence===editorMountSequence);
      };
      const ready=Promise.resolve(window.__qfExtensionsReady).then(mount);
      window.__qfEditorReady=ready;
      ready.catch(error=>{window.qfEditorShell?.errorFromJson(JSON.stringify({message:error.message}));window.extensionEditorHost?.reloadFailed?.(error.message);});
      return ready;
    },
    clearEditor(){
      editorMountSequence++;pendingEditor?.cancel();pendingEditor=null;
      editorPresentation?.instance.destroy();editorPresentation=null;
      window.extensionEditorInstance=null;window.extensionEditorType=null;
      const root=document.querySelector('#extension-editor');if(root)replaceChildrenRetainingFrames(root);
      editorLayout?.destroy();editorLayout=null;window.__qfEditorReady=Promise.resolve();
    } });
  let editorReload = Promise.resolve();
  window.addEventListener('qf-extension-updated', event => {
    if (!event.detail.types.includes(window.extensionEditorType) || !window.extensionEditorInstance) return;
    editorReload = editorReload.then(async () => {
      const instance = window.extensionEditorInstance, type = window.extensionEditorType;
      const draft = instance.hasInitializationError?.() ? window.extensionEditorSnapshot() : await window.extensionEditorFlush();
      if (instance === window.extensionEditorInstance) await window.questionExtensions.mountEditorFromJson(type, draft);
    }).catch(error => window.extensionEditorHost?.reloadFailed?.('实时预览更新失败：' + error.message));
  });
}
