import './sdk.js';
import '../practice/style.css';
import './editor-layout.css';
import '../learning/editor-shell.css';
import shellHtml from '../learning/editor-shell.html';
import {createEditorShell} from '../learning/editor-shell.js';

const legacyRoot = document.querySelector('#extension-editor');
legacyRoot.insertAdjacentHTML('beforebegin', shellHtml.replace('<main id="extension-editor"></main>', '<main data-shell-editor></main>'));
const shellRoot = document.querySelector('#qf-editor-shell');
let shell = null, sequence = 0;
const requests = new Map();
function createShell() {
  if (shell) return shell;
  legacyRoot.remove(); shellRoot.querySelector('[data-shell-editor]').id = 'extension-editor';
  shell = createEditorShell(shellRoot, {
    request(value) {
      return new Promise((resolve,reject)=>{
        const id = String(++sequence);
        const timeout = setTimeout(()=>{requests.delete(id);reject(new Error('编辑操作超时，请重试'));},15000);
        requests.set(id,{resolve,reject,timeout});
        try { window.bankEditorHost.request(id,JSON.stringify(value)); }
        catch(error){clearTimeout(timeout);requests.delete(id);reject(error);}
      });
    },
    mountEditor:(type,question)=>window.questionExtensions.mountEditorFromJson(type,question),
    flushEditor:async()=>{await window.__qfEditorReady;return window.extensionEditorFlush();},
    clearEditor:()=>window.questionExtensions.clearEditor(),
    heightChanged:reportHeight
  });
  return shell;
}
window.qfEditorShell = Object.freeze({
  command:(action,argument)=>createShell().command(action,argument),
  getState:()=>shell?.getState() ?? null,
  configureUi:preferences=>createShell().configureUi(preferences),
  updateFromJson(value){return createShell().update(JSON.parse(value));},
  errorFromJson(value){createShell().error(JSON.parse(value).message);},
  replyFromJson(value){const {requestId,reply}=JSON.parse(value),request=requests.get(requestId);if(request){clearTimeout(request.timeout);requests.delete(requestId);request.resolve(reply);}}
});

// The surrounding JavaFX page owns scrolling; this fragment reports its natural height.
const root = document.body;
let frame = null;
function reportHeight() {
  if (frame !== null) return;
  frame = requestAnimationFrame(() => {
    frame = null;
    const content = shell ? shellRoot : legacyRoot;
    const style=getComputedStyle(content);
    window.extensionEditorHost?.contentHeight?.(Math.ceil(content.getBoundingClientRect().height+parseFloat(style.marginTop)+parseFloat(style.marginBottom)));
  });
}
const resize = new ResizeObserver(reportHeight);
resize.observe(root);
const mutations = new MutationObserver(reportHeight);
mutations.observe(root, { childList: true, subtree: true, attributes: true, characterData: true });
window.addEventListener('resize', reportHeight);
document.fonts?.ready.then(reportHeight);
window.addEventListener('pagehide', () => {
  shell?.destroy();
  window.questionExtensions.clearEditor();
  for(const request of requests.values()){clearTimeout(request.timeout);request.reject(new Error('编辑页面已关闭'));}
  requests.clear();
  resize.disconnect(); mutations.disconnect();
  if (frame !== null) cancelAnimationFrame(frame);
});
reportHeight();
