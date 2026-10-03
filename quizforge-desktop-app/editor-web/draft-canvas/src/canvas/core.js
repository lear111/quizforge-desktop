import { DraftModel, screenToWorld } from '../model.js';

// This host owns world geometry and tools only. The live DOM object is supplied by its caller.
export function mountDraftCanvas(object, { accessMode = 'EDITABLE' } = {}) {
if (!(object instanceof HTMLElement)) throw new TypeError('A live World object is required');
if (!['EDITABLE', 'READ_ONLY'].includes(accessMode)) throw new TypeError('Unknown Canvas access mode');
const readOnly = accessMode === 'READ_ONLY';

const $ = selector => document.querySelector(selector);
const root = $('#draft-canvas-root'), viewport = $('#viewport'), world = $('#world');
const strokes = $('#strokes'), activeStroke = $('#active-stroke');
const model = new DraftModel();
const ns = 'http://www.w3.org/2000/svg';
const listeners = [], counts = {}, events = [];
let mode = 'INTERACT', gesture = null, destroyed = false, sequence = 0, editable = true;
const changeListeners = new Set(); let lastNotified = JSON.stringify(model.getDraft());
function changed() {
  const value = JSON.stringify(model.getDraft());
  if (value === lastNotified) return; lastNotified = value;
  if (!readOnly) changeListeners.forEach(listener => listener(value));
}
const helps = { INTERACT: '交互：操作题卡内容。', PEN: '画笔：在空白区域或题卡上书写。',
  ERASER: '橡皮：划过笔迹，删除整笔。', PAN: '平移：拖动白板，题卡与笔迹一起移动。' };

function listen(target, type, fn, options) {
  target.addEventListener(type, fn, options);
  listeners.push(() => target.removeEventListener(type, fn, options));
}
function alive() { if (destroyed) throw new Error('Draft Canvas has been destroyed'); }
function drawStroke(parent, stroke) {
  const line = document.createElementNS(ns, stroke.points.length === 1 ? 'circle' : 'polyline');
  line.setAttribute('data-stroke-id', stroke.id);
  if (stroke.points.length === 1) {
    line.setAttribute('cx', stroke.points[0].x); line.setAttribute('cy', stroke.points[0].y);
    line.setAttribute('r', stroke.width / 2); line.setAttribute('fill', stroke.color);
  } else {
    line.setAttribute('points', stroke.points.map(p => `${p.x},${p.y}`).join(' '));
    line.setAttribute('fill', 'none'); line.setAttribute('stroke', stroke.color);
    line.setAttribute('stroke-width', stroke.width);
    line.setAttribute('stroke-linecap', 'round'); line.setAttribute('stroke-linejoin', 'round');
  }
  parent.appendChild(line);
}
function renderTransform(draft = model.getDraft()) {
  const v = draft.viewport, c = draft.questionCard;
  world.style.transform = `translate(${v.x * v.zoom}px, ${v.y * v.zoom}px) scale(${v.zoom})`;
  object.style.left = `${c.x}px`; object.style.top = `${c.y}px`; object.style.width = `${c.width}px`;
  // Width belongs to this document's logical layout, never to the viewport or device width.
  object.style.minWidth = `${c.width}px`; object.style.maxWidth = `${c.width}px`;
  viewport.style.backgroundPosition = `${v.x * v.zoom}px ${v.y * v.zoom}px`;
}
function render() {
  const draft = model.getDraft(); renderTransform(draft);
  strokes.replaceChildren(); draft.strokes.forEach(s => drawStroke(strokes, s));
  $('#undo').disabled = readOnly || !model.canUndo; $('#redo').disabled = readOnly || !model.canRedo;
  $('#clear').disabled = readOnly || !draft.strokes.length;
  $('#status').textContent = `${draft.strokes.length} 笔 · 视口 ${Math.round(draft.viewport.x)}, ${Math.round(draft.viewport.y)} · ${Math.round(draft.viewport.zoom * 100)}%`;
}
function screenPoint(event) {
  const bounds = viewport.getBoundingClientRect();
  return { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
}
function worldPoint(event) {
  return { ...screenToWorld(screenPoint(event), model.getDraft().viewport),
    pressure: Number.isFinite(event.pressure) && event.pressure >= 0 && event.pressure <= 1 ? event.pressure : 0.5 };
}
function observe(event) {
  counts[event.type] = (counts[event.type] || 0) + 1;
  events.push({ type: event.type, pointerType: event.pointerType, pressure: event.pressure, isTrusted: event.isTrusted });
  if (events.length > 128) events.shift();
}
function finish(commit) {
  if (!gesture) return;
  const current = gesture; gesture = null;
  if (current.mode === 'PEN' && commit) model.addStroke(current.stroke);
  if (current.mode === 'ERASER') model.endEdit();
  activeStroke.replaceChildren(); viewport.classList.remove('dragging');
  if (viewport.hasPointerCapture?.(current.pointerId)) viewport.releasePointerCapture(current.pointerId);
  render(); changed();
}
function setMode(next) {
  alive(); if (!Object.prototype.hasOwnProperty.call(helps, next)) throw new Error('Unknown tool mode');
  if (readOnly && next !== 'PAN') throw new Error('History Canvas permits Pan only');
  finish(true); mode = next; root.dataset.mode = next;
  // Drawing never keeps a focused card input receiving keyboard events.
  if (next !== 'INTERACT' && object.contains(document.activeElement)) document.activeElement.blur();
  document.querySelectorAll('button[data-mode]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.mode === next)));
  $('#mode-help').textContent = helps[next];
}
listen(viewport, 'pointerdown', event => {
  observe(event);
  if (!editable || mode === 'INTERACT' || gesture || !event.isPrimary || event.button !== 0) return;
  event.preventDefault();
  gesture = { mode, pointerId: event.pointerId, last: screenPoint(event) };
  viewport.setPointerCapture?.(event.pointerId); viewport.classList.add('dragging');
  if (mode === 'PEN') {
    gesture.stroke = { id: `stroke-${Date.now()}-${++sequence}-${Math.random().toString(36).slice(2)}`, tool: 'PEN', color: '#7054a5', width: 2.4, points: [worldPoint(event)] };
    drawStroke(activeStroke, gesture.stroke);
  } else if (mode === 'ERASER') {
    model.beginEdit(); gesture.lastWorld = worldPoint(event);
    model.eraseAt(gesture.lastWorld, 10 / model.getDraft().viewport.zoom); render();
  }
});
// Document listeners also finish drags outside the viewport if capture is unavailable.
listen(document, 'pointermove', event => {
  if (viewport.contains(event.target) || gesture?.pointerId === event.pointerId) observe(event);
  if (!gesture || event.pointerId !== gesture.pointerId) return;
  event.preventDefault();
  if (gesture.mode === 'PEN') {
    gesture.stroke.points.push(worldPoint(event));
    activeStroke.replaceChildren(); drawStroke(activeStroke, gesture.stroke);
  } else if (gesture.mode === 'PAN') {
    const next = screenPoint(event); model.pan(next.x - gesture.last.x, next.y - gesture.last.y);
    gesture.last = next; renderTransform();
  } else if (gesture.mode === 'ERASER') {
    const next = worldPoint(event);
    model.eraseAlong(gesture.lastWorld, next, 10 / model.getDraft().viewport.zoom);
    gesture.lastWorld = next; render();
  }
});
listen(document, 'pointerup', event => {
  if (viewport.contains(event.target) || gesture?.pointerId === event.pointerId) observe(event);
  if (event.pointerId !== gesture?.pointerId) return;
  if (gesture.mode === 'PEN') gesture.stroke.points.push(worldPoint(event));
  else if (gesture.mode === 'ERASER') model.eraseAlong(gesture.lastWorld, worldPoint(event), 10 / model.getDraft().viewport.zoom);
  else if (gesture.mode === 'PAN') {
    const next = screenPoint(event); model.pan(next.x - gesture.last.x, next.y - gesture.last.y);
  }
  finish(true);
});
listen(document, 'pointercancel', event => { if (event.pointerId === gesture?.pointerId) finish(false); });
listen(viewport, 'lostpointercapture', event => { if (event.pointerId === gesture?.pointerId) finish(false); });
listen(window, 'blur', () => finish(false));
document.querySelectorAll('button[data-mode]').forEach(b => listen(b, 'click', () => setMode(b.dataset.mode)));
for (const action of ['undo', 'redo', 'clear']) listen($(`#${action}`), 'click', () => { if (!editable || readOnly) return; finish(true); model[action](); render(); changed(); });
listen(document, 'keydown', event => {
  if (!editable || readOnly || event.target.matches('input, textarea') || !(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'z') return;
  event.preventDefault(); finish(true); event.shiftKey ? model.redo() : model.undo(); render(); changed();
});
function showJson(exporting) {
  if (readOnly) return;
  finish(true); $('#json-error').textContent = '';
  if (exporting || !$('#draft-json').value) $('#draft-json').value = JSON.stringify(model.getDraft(), null, 2);
  $('#draft-panel').hidden = false; $('#draft-json').focus();
}
listen($('#export'), 'click', () => showJson(true));
listen($('#import'), 'click', () => showJson(false));
listen($('#close-panel'), 'click', () => { $('#draft-panel').hidden = true; });
function loadDraft(json) {
  alive(); finish(false); model.loadDraft(json); render(); changed();
}
function setZoom(zoom, screenAnchor = { x: 0, y: 0 }) {
  alive(); if (!editable) return; finish(true); model.setZoom(zoom, screenAnchor); render(); changed();
}
function zoomBy(factor) {
  const zoom = Math.min(4, Math.max(0.1, model.getDraft().viewport.zoom * factor));
  setZoom(zoom, { x: viewport.clientWidth / 2, y: viewport.clientHeight / 2 });
}
function fitCard() {
  alive(); if (!editable) return; finish(true);
  const card = model.getDraft().questionCard;
  const zoom = Math.min(1, Math.max(0.1, (viewport.clientWidth - 40) / card.width));
  model.setZoom(zoom);
  const view = model.getDraft().viewport;
  model.pan((viewport.clientWidth - card.width * zoom) / 2 - (card.x + view.x) * zoom,
    20 - (card.y + view.y) * zoom);
  render(); changed();
}
listen($('#zoom-out'), 'click', () => zoomBy(1 / 1.2));
listen($('#zoom-in'), 'click', () => zoomBy(1.2));
listen($('#zoom-fit'), 'click', fitCard);
listen($('#load-json'), 'click', () => {
  if (!editable || readOnly) return;
  try { loadDraft($('#draft-json').value); $('#draft-panel').hidden = true; }
  catch (error) { $('#json-error').textContent = error.message; }
});
const api = Object.freeze({
  getDraft() { alive(); finish(true); return JSON.stringify(model.getDraft()); },
  loadDraft, setMode, setZoom, fitCard,
  onChange(listener) { alive(); changeListeners.add(listener); return () => changeListeners.delete(listener); },
  setEditable(value) { alive(); finish(true); editable = Boolean(value); },
  diagnostics() { return { supportedPointerEvents: typeof PointerEvent !== 'undefined', counts: { ...counts }, events: events.map(e => ({ ...e })), accessMode, mode, destroyed }; },
  destroy() {
    if (destroyed) return;
    finish(false); destroyed = true; changeListeners.clear(); listeners.splice(0).forEach(remove => remove());
    if (root.contains(document.activeElement)) document.activeElement.blur();
    root.inert = true; root.style.pointerEvents = 'none';
    root.querySelectorAll('input, button, textarea').forEach(element => { element.disabled = true; });
    $('#status').textContent = '已销毁';
  }
});
render();
root.dataset.accessMode = accessMode;
if (readOnly) {
  document.querySelectorAll('button[data-mode]').forEach(button => {
    if (button.dataset.mode !== 'PAN') { button.hidden = true; button.disabled = true; }
  });
  for (const id of ['undo', 'redo', 'clear', 'export', 'import', 'load-json']) {
    $(`#${id}`).hidden = true; $(`#${id}`).disabled = true;
  }
  setMode('PAN');
}
if (typeof PointerEvent === 'undefined') $('#mode-help').textContent = '当前运行环境缺少 Pointer Events。';

return Object.freeze({ ...api, addDisposer(dispose) { alive(); listeners.push(dispose); } });
}
