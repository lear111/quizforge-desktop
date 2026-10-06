/** Renderer capability contract used by bundled and independently installed extensions. */
/**
 * @typedef {Object} QuestionRendererInstance
 * @property {function(Object):void} update Apply the authoritative question DTO.
 * @property {function():Object} getAnswerIntent Type-specific semantic answer only; no DOM or grading.
 * @property {function('INTERACT'|'DISABLED'):void} setInteractionMode
 * @property {function(boolean):void} setReadOnly History capability cannot be upgraded.
 * @property {function():HTMLElement} renderResult Display the supplied Core result.
 * @property {function():boolean} hasAnswer Whether there is a formal answer to submit.
 * @property {function(string):HTMLElement|null} focusTarget Resolve stable child target within this parent.
 * @property {function():Promise<void>} [flushAnswer] Drain pending TEXT edits before leave/submit.
 * @property {function():Object|null} [pendingAnswerIntent] Synchronous host close-save guard.
 * @property {function():void} destroy Idempotent listener and control cleanup.
 *
 * @typedef {Object} QuestionRendererDefinition
 * @property {string} id Stable internal renderer identity.
 * @property {string} questionType Core type identity; never a fallback type.
 * @property {'SINGLE'|'MULTIPLE'|'COMPOSITE_SINGLE'|'ASSIGNMENT'|'TEXT_FIELDS'|'LONG_TEXT'|'EXTENSION'} selectionMode Presentation family.
 * @property {function(Object):Object} parse Validate supported structured content.
 * @property {function(HTMLFormElement,Object,Object):QuestionRendererInstance} mount
 * @property {function(Object,Object):Object} [projectPreview] Convert owned question JSON to a public preview DTO.
 */
export const RendererMode = Object.freeze({ ACTIVE: 'ACTIVE', READ_ONLY_HISTORY: 'READ_ONLY_HISTORY' });
export function requireRendererMode(mode) {
  if (!Object.values(RendererMode).includes(mode)) throw new TypeError('Unsupported renderer capability mode');
  return mode;
}
export function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
