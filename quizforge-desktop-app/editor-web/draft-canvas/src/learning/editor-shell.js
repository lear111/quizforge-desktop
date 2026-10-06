/** Shared browser shell. Platform adapters exchange JSON only; type pages own their inner layout. */
export function createEditorShell(root, { request, mountEditor, flushEditor, clearEditor, heightChanged = () => {} }) {
  let state = null, questionId = null, busy = false, disposed = false, mounting = false, updateSequence = 0;
  let pendingUpdate=Promise.resolve();
  const listeners = [];
  const $ = selector => root.querySelector(selector);
  function on(node, event, fn) { node.addEventListener(event, fn); listeners.push(() => node.removeEventListener(event, fn)); }
  function error(message = '') { $('#qbank-editor-error').textContent = message; $('#qbank-editor-error').hidden = !message || state?.ui?.errors===false; heightChanged(); }
  function applyUi() {
    const ui=state?.ui || {};
    const selectors={title:'.qf-editor-toolbar > span',save:'#qbank-save',position:'#qbank-editor-position',
      typeLabel:'#qbank-question-type',add:'#qbank-add-question',duplicate:'#qbank-duplicate-question',delete:'#qbank-delete-question',sources:'#qbank-editor-sources'};
    for(const [name,selector] of Object.entries(selectors)) $(selector).hidden=ui[name]===false || (name==='sources'&&!state.question);
    $('.qf-editor-toolbar').hidden=ui.title===false && ui.save===false;
    if(ui.delete===false) $('#qbank-delete-confirm').hidden=true;
    $('#qbank-editor-error').hidden=ui.errors===false || !$('#qbank-editor-error').textContent;
    heightChanged();
  }
  function controls() {
    const locked=busy||mounting;
    const empty = !state?.question;
    root.setAttribute('aria-busy', String(locked));
    root.querySelectorAll('button,select').forEach(node => {
      if (!node.closest('#extension-editor')) node.disabled = locked || Boolean(node.dataset.unavailable);
    });
    $('#qbank-editor-previous').disabled = locked || empty || state.index === 0;
    $('#qbank-editor-next').disabled = locked || empty || state.index >= state.count - 1;
    $('#qbank-delete-question').disabled = locked || empty;
    $('#qbank-duplicate-question').disabled = locked || empty || !state.editable;
    $('#qbank-use-source-link').disabled = locked || empty;
    $('#qbank-source-link').disabled = locked || empty;
    // A command flushes before crossing the bridge. Prevent new edits until its reply arrives.
    $('#extension-editor').inert = locked;
    $('#extension-editor').style.pointerEvents = locked ? 'none' : '';
  }
  function update(next) {
    if (disposed) return Promise.resolve();
    state = next; root.hidden = false;
    const id=state.question?.id ?? '';
    if(questionId!==id){
      questionId=id;const sequence=++updateSequence;mounting=true;controls();
      pendingUpdate=Promise.resolve().then(()=>{
        if(disposed||sequence!==updateSequence)return;
        return next.editable?mountEditor(next.question.type,next.question):clearEditor();
      }).then(()=>{
        if(disposed||sequence!==updateSequence)return;
        mounting=false;$('#qbank-delete-confirm').hidden=true;$('#qbank-source-link').value='';error();paint();
      },failure=>{
        if(!disposed&&sequence===updateSequence){mounting=false;paint();error(failure.message);}
        throw failure;
      });
      pendingUpdate.catch(()=>{});return pendingUpdate;
    }
    // UI preference messages may arrive while this editor is staging. Paint only the final state.
    if(mounting)return pendingUpdate;
    paint();return Promise.resolve();
  }
  function paint(){
    $('#qbank-editor-position').textContent = state.count ? `${state.index + 1} / ${state.count}` : '0 / 0';
    $('#qbank-question-type').textContent = state.label || '';
    const add = $('#qbank-add-question'); add.replaceChildren(new Option('＋ 添加题目', ''));
    for (const type of state.types) add.append(new Option(type.label, type.id));
    $('#qbank-editor-empty').hidden = Boolean(state.question);
    $('#qbank-editor-missing').hidden = !state.question || state.editable;
    $('#qbank-editor-missing').textContent = state.question && !state.editable
      ? `缺少 ${state.question.type} 对应的题型扩展。安装后可编辑，原始数据和资源已保留。` : '';
    $('#qbank-editor-sources').hidden = !state.question;
    const rows = $('#qbank-source-list'); rows.replaceChildren();
    state.sources.forEach((source, index) => {
      const row = document.createElement('div'); row.className = 'qf-source-row';
      const open = document.createElement('button'); open.type = 'button'; open.className = 'qf-source-open';
      open.textContent = source.label; open.dataset.sourceIndex = index; open.dataset.sourceAction = 'open';
      if (!source.navigable) open.dataset.unavailable = 'true';
      const message = document.createElement('span'); message.className = 'qf-source-message'; message.textContent = source.message;
      const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '×'; remove.setAttribute('aria-label','移除引用');
      remove.dataset.sourceIndex = index; remove.dataset.sourceAction = 'remove';
      row.append(open, message, remove); rows.append(row);
    });
    controls(); applyUi();
  }
  async function command(action, argument = null) {
    if (busy || mounting || disposed || !state) return { ok:false, error:{code:'BUSY',message:'正在处理编辑操作',retryable:false} };
    busy = true; error(); controls();
    try {
      const id = questionId;
      const draft = state.editable ? JSON.parse(await flushEditor()) : null;
      if (id !== questionId || disposed) throw new Error('题目已切换，请重新操作');
      const reply = await request({ action, argument, questionId:id, draft });
      if (disposed) return reply;
      if (reply.ok) {
        await update(reply.data);
        if (action === 'source.add') $('#qbank-source-link').value = '';
      } else {error(reply.error.message);applyUi();}
      return reply;
    } catch (failure) {
      error(failure.message || '编辑操作失败');applyUi();
      return {ok:false,error:{code:'EDITOR_COMMAND_FAILED',message:failure.message || '编辑操作失败',retryable:false}};
    } finally { busy = false; if (!disposed) controls(); }
  }
  on($('#qbank-save'),'click',()=>command('save'));
  on($('#qbank-editor-previous'),'click',()=>command('navigate',state.index - 1));
  on($('#qbank-editor-next'),'click',()=>command('navigate',state.index + 1));
  on($('#qbank-add-question'),'change',()=>{const type=$('#qbank-add-question').value;if(type)command('add',type);});
  on($('#qbank-duplicate-question'),'click',()=>command('duplicate'));
  on($('#qbank-delete-question'),'click',()=>{if(!busy){$('#qbank-delete-confirm').hidden=false;heightChanged();}});
  on($('[data-cancel-delete]'),'click',()=>{$('#qbank-delete-confirm').hidden=true;heightChanged();});
  on($('[data-confirm-delete]'),'click',()=>command('delete'));
  on($('#qbank-use-source-link'),'click',()=>command('source.add',$('#qbank-source-link').value));
  on($('#qbank-source-link'),'keydown',event=>{if(event.key==='Enter'){event.preventDefault();command('source.add',event.target.value);}});
  on($('#qbank-source-list'),'click',event=>{const node=event.target.closest('[data-source-action]');if(node && !node.dataset.unavailable)command('source.'+node.dataset.sourceAction,Number(node.dataset.sourceIndex));});
  on(document,'keydown',event=>{if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='s'){event.preventDefault();command('save');}});
  return Object.freeze({ update, command, error, getState:()=>state && JSON.parse(JSON.stringify(state)),
    configureUi(preferences){if(state){state={...state,ui:preferences};if(!mounting)applyUi();}},
    destroy(){disposed=true;updateSequence++;listeners.forEach(remove=>remove());} });
}
