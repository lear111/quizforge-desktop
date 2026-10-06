import { compareVersions } from '../../extensions/version.js';
import {requireCompatibleManifest} from '../../extensions/compatibility.js';
const renderers = new Map(), extensions = new Map(), editors = new Map(), owners = new Map(), active = new Map();

/** The host ships no question renderer. Installed HTML packages populate this registry. */
export function registerQuestionExtension(manifest, definitions, editorDefinitions = [], { replace = false } = {}) {
  requireCompatibleManifest(manifest);
  if (typeof manifest.id !== 'string'
      || typeof manifest.version !== 'string' || !Array.isArray(manifest.types) || !manifest.types.length)
    throw new TypeError('Unsupported question extension manifest: HTML SDK 2 required');
  const key = `${manifest.id}@${manifest.version}`;
  if (extensions.has(key) && !replace) return false;
  if (replace && !extensions.has(key)) throw new TypeError('Development preview requires an installed extension version');
  if (replace && JSON.stringify(extensions.get(key)) !== JSON.stringify(manifest))
    throw new TypeError('Development preview cannot change the installed manifest');
  const ids = new Set();
  for (const type of manifest.types) {
    if (typeof type.id !== 'string' || !type.id.trim() || ids.has(type.id)
        || (owners.has(type.id) && owners.get(type.id) !== manifest.id))
      throw new TypeError(`Question type is already registered: ${type.id}`);
    ids.add(type.id);
    const renderer = definitions.find(d => d.questionType === type.id);
    if (!renderer || typeof renderer.parse !== 'function' || typeof renderer.mount !== 'function')
      throw new TypeError(`Missing renderer for ${type.id}`);
  }
  if (definitions.some(d => !ids.has(d.questionType))) throw new TypeError('Undeclared renderer');
  for (const editor of editorDefinitions)
    if (!ids.has(editor.questionType) || typeof editor.mount !== 'function') throw new TypeError('Invalid extension editor');
  for (const type of manifest.types) {
    const definition = definitions.find(d => d.questionType === type.id);
    renderers.set(`${type.id}@${manifest.version}`, Object.freeze({ ...definition, label: type.label, extensionId: manifest.id, extensionVersion: manifest.version }));
    owners.set(type.id, manifest.id);
    if (!active.has(type.id) || compareVersions(manifest.version, active.get(type.id)) > 0) active.set(type.id, manifest.version);
  }
  for (const editor of editorDefinitions) editors.set(`${editor.questionType}@${manifest.version}`, Object.freeze(editor));
  extensions.set(key, JSON.parse(JSON.stringify(manifest)));
  return true;
}
export const QuestionRendererRegistry = Object.freeze({
  get types() { return Object.freeze([...owners.keys()]); },
  require(type, version) {
    const renderer = renderers.get(`${type}@${version || active.get(type)}`);
    if (!renderer) throw new TypeError(`缺少对应题型拓展：${type}${version ? `（${version}）` : ''}。请安装新版题型拓展。`);
    return renderer;
  },
  editor(type, version) { this.require(type, version); return editors.get(`${type}@${version || active.get(type)}`) ?? null; },
  extensions() { return [...extensions.values()].map(m => JSON.parse(JSON.stringify(m))); }
});
