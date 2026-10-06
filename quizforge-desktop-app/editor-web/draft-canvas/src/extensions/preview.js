import './sdk.js';
import { QuestionRendererRegistry } from '../shared/renderer/registry.js';
import { RendererMode, element } from '../shared/renderer/contract.js';
import '../practice/style.css';
import { projectQuestionPreview } from './preview-projection.js';
import {defaultUi,applyPracticeUi} from '../shared/ui/preferences.js';
import {replaceChildrenRetainingFrames} from './protocol.js';
export { projectQuestionPreview } from './preview-projection.js';

/** Hub preview receives a public presentation DTO, never a desktop Java bridge. */
export function mountQuestionPreview(root, question, { showAnswers = false, reloadPage } = {}) {
  const q = JSON.parse(JSON.stringify(question));
  const definition = QuestionRendererRegistry.require(q.type,q.rendererVersion||q.presentation?.extensionVersion);
  const data = definition.parse(q);
  Object.assign(q, data);
  if (!showAnswers) {
    q.result = null;
    if (q.presentation?.reference) q.presentation.reference = null;
  }
  replaceChildrenRetainingFrames(root);
  const heading = element('div', 'card-heading'); heading.append(element('span', 'tag', definition.label));
  const contentRoot = document.createDocumentFragment(), form = element('form');
  contentRoot.append(heading);
  let preferences=defaultUi('PRACTICE');
  const instance = definition.mount(form, q, { mode: RendererMode.READ_ONLY_HISTORY, contentRoot,
    reloadPage,
    uiChanged(next){preferences=next;applyPracticeUi(root,next);},
    layoutRoot:root,preview: true, canInteract: () => false, answerChanged: () => { throw new Error('Preview is read-only'); } });
  instance.setReadOnly(true); instance.setInteractionMode('DISABLED');
  contentRoot.append(form, instance.renderResult()); root.append(contentRoot);
  applyPracticeUi(root,preferences);
  const preventSubmit = event => event.preventDefault(); form.addEventListener('submit', preventSubmit);
  return Object.freeze({ destroy() { form.removeEventListener('submit', preventSubmit); const completion=instance.destroy();replaceChildrenRetainingFrames(root);return completion; } });
}
if (typeof window !== 'undefined') {
let displayed = null;
window.questionPreview = Object.freeze({
  mount(root, question, options) { return mountQuestionPreview(root, typeof question === 'string' ? JSON.parse(question) : question, options); },
  project: projectQuestionPreview,
  async showQuestion(question,options={}) {
    await window.__qfExtensionsReady;
    this.show(await projectQuestionPreview(typeof question==='string'?JSON.parse(question):question,options),options.showAnswers===true);
  },
  show(question, showAnswers = false) {
    displayed = { question: typeof question === 'string' ? JSON.parse(question) : question, showAnswers };
    const current=displayed;
    window.questionPreviewInstance?.destroy();
    window.questionPreviewInstance = mountQuestionPreview(document.querySelector('#question-preview'),
      typeof question === 'string' ? JSON.parse(question) : question, { showAnswers,reloadPage(){if(displayed===current)window.questionPreview.show(current.question,current.showAnswers);} });
  }
});
window.addEventListener('qf-extension-updated', event => {
  if (displayed && event.detail.types.includes(displayed.question.type)) window.questionPreview.show(displayed.question, displayed.showAnswers);
});
}
