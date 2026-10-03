/** Internal renderer capability contract; unrelated to Core QuestionTypeDefinition. */
/**
 * @typedef {Object} QuestionRendererInstance
 * @property {function(Object):void} update Apply the authoritative question DTO.
 * @property {function():{selectedOptionIds:string[]}} getAnswerIntent Semantic choice answer only.
 * @property {function('INTERACT'|'DISABLED'):void} setInteractionMode
 * @property {function(boolean):void} setReadOnly History capability cannot be upgraded.
 * @property {function():HTMLElement} renderResult Display the supplied Core result.
 * @property {function():void} destroy Idempotent listener and control cleanup.
 *
 * @typedef {Object} QuestionRendererDefinition
 * @property {string} id Stable internal renderer identity.
 * @property {string} questionType Core type identity; never a fallback type.
 * @property {'SINGLE'|'MULTIPLE'} selectionMode Choice family presentation mode.
 * @property {function(Object):Object} parse Validate supported structured content.
 * @property {function(HTMLFormElement,Object,Object):QuestionRendererInstance} mount
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
