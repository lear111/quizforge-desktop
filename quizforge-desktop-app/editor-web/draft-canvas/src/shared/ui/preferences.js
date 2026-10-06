const fields = Object.freeze({
  EDITOR: ['title','save','position','typeLabel','add','duplicate','delete','sources','outline','errors'],
  PRACTICE: ['card','typeLabel','position','score','state','submit','retry','confirmation','note','sources','outline','draftToggle','draftToolbar','draftZoom','errors']
});
export function defaultUi(mode) {
  return Object.freeze(Object.fromEntries(fields[mode === 'EDITOR' ? 'EDITOR' : 'PRACTICE'].map(name=>[name,true])));
}
/** Visibility is presentation only. Navigation and mutation permissions are outside this configuration. */
export function configureUi(current, patch, mode) {
  if (!patch || typeof patch !== 'object' || Array.isArray(patch)) throw new TypeError('公共 UI 配置必须是对象');
  const allowed = fields[mode === 'EDITOR' ? 'EDITOR' : 'PRACTICE'];
  for (const [name,value] of Object.entries(patch)) {
    if (!allowed.includes(name)) throw new TypeError(name === 'navigation' ? '上一题、下一题由宿主保留，不能关闭' : `未知公共 UI 选项：${name}`);
    if (typeof value !== 'boolean') throw new TypeError(`公共 UI 选项 ${name} 必须为布尔值`);
  }
  return Object.freeze({...defaultUi(mode),...current,...patch});
}
export function isolatedUiSource(){return `const fields=${JSON.stringify(fields)};\n${defaultUi.toString()}\n${configureUi.toString()}`;}
export function applyPracticeUi(root, preferences) {
  const hide = (selector, visible) => root.querySelectorAll(selector).forEach(node=>{node.hidden=!visible;});
  root.classList.toggle('qf-bare-card',!preferences.card);
  hide('.card-heading .tag',preferences.typeLabel);
  hide('.card-heading .question-position',preferences.position);
  hide('.card-heading',Boolean(root.querySelector('.card-heading .tag:not([hidden]),.card-heading .question-position:not([hidden])')));
  hide('.practice-score',preferences.score); hide('.practice-state',preferences.state);
  hide('.practice-meta',preferences.score || preferences.state);
  hide('#practice-submit',preferences.submit);hide('#practice-retry',preferences.retry);
  hide('.practice-stage-note',preferences.note);
}
export function applyCanvasUi(root, preferences) {
  const host = root.closest('#draft-canvas-root');
  if (!host) return;
  host.classList.toggle('qf-hide-draft-toolbar',!preferences.draftToolbar);
  host.classList.toggle('qf-hide-draft-zoom',!preferences.draftZoom);
}
