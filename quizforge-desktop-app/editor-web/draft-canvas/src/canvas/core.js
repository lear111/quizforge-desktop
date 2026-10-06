import { DraftModel, screenToWorld } from '../model.js';
import { modePolicy, practiceViewport } from '../learning/mode-policy.js';
import { mountFloatingTools } from './floating-tools.js';
import { DEFAULT_PAPER, normalizePaper } from './document.js';
import {defaultLayout,configureLayout as validateLayout} from '../shared/ui/layout.js';
import './floating-tools.css';

// This host owns world geometry and tools only. The live DOM object is supplied by its caller.
export function mountDraftCanvas(object, { accessMode = 'EDITABLE' } = {}) {
if (!(object instanceof HTMLElement)) throw new TypeError('A live World object is required');
if (!['EDITABLE', 'READ_ONLY'].includes(accessMode)) throw new TypeError('Unknown Canvas access mode');
const readOnly = accessMode === 'READ_ONLY';

const $ = selector => document.querySelector(selector);
const root = $('#draft-canvas-root'), viewport = $('#viewport'), world = $('#world');
mountFloatingTools(root);
const strokes = $('#strokes'), activeStroke = $('#active-stroke');
const texts=document.createElement('div');texts.className='whiteboard-texts';world.append(texts);
const model = new DraftModel();
const ns = 'http://www.w3.org/2000/svg';
const listeners = [], counts = {}, events = [];
let mode = 'INTERACT', gesture = null, destroyed = false, sequence = 0, editable = true;
let learningMode = null, practiceScroll = 0, navigationLocked = false;
let layout=defaultLayout(),layoutReady=false,allowInitialLayout=false,hasSavedGeometry=true;
let textEditor=null,panFrame=null;
const canEdit = () => editable && !readOnly && learningMode !== 'PRACTICE';
const canNavigate = () => learningMode !== 'PRACTICE' && !navigationLocked;
const displayViewport = () => learningMode === 'PRACTICE'
  ? practiceViewport(model.getQuestionCard(), viewport.clientWidth, practiceScroll, viewport.clientHeight, object.offsetHeight,layout) : model.getViewport();
const changeListeners = new Set(); let lastNotified = JSON.stringify(model.getDraft());
const modeListeners = new Set(), uiListeners=new Set();
function notifyUi(){uiListeners.forEach(listener=>listener());}
function changed() {
  const value = JSON.stringify(model.getDraft());
  if (value === lastNotified) return; lastNotified = value;
  if (!readOnly) changeListeners.forEach(listener => listener(value));
}
const helps = { INTERACT: '交互：操作题卡内容。', PEN: '画笔：在空白区域或题卡上书写。',
  ERASER: '橡皮：划过笔迹，删除整笔。', PAN: '拖动：按住鼠标左键，题卡、笔迹和文本一起移动。',
  LINE:'直线：拖动绘制直线。',RECT:'矩形：拖动绘制矩形。',TEXT:'文本：点击白板输入文字，Ctrl+Enter 完成。' };

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
function renderTransform(draft = {questionCard:model.getQuestionCard(),paper:model.getPaper()}) {
  const v = displayViewport(), c = draft.questionCard;
  world.style.transform = `translate(${v.x * v.zoom}px, ${v.y * v.zoom}px) scale(${v.zoom})`;
  object.style.left = `${c.x}px`; object.style.top = `${c.y}px`; object.style.width = `${c.width}px`;
  // Width belongs to this document's logical layout, never to the viewport or device width.
  object.style.minWidth = `${c.width}px`; object.style.maxWidth = `${c.width}px`;
  viewport.style.backgroundPosition = `${v.x * v.zoom}px ${v.y * v.zoom}px`;
  const paper=draft.paper||DEFAULT_PAPER;
  const size=(paper.pattern==='LINES'?32:24)*v.zoom;
  viewport.dataset.paperPattern=paper.pattern;viewport.style.backgroundSize=`${size}px ${size}px`;
  viewport.style.backgroundColor=paper.color;
  root.style.backgroundColor=paper.color;
  $('#zoom-value').textContent=`${Math.round(v.zoom*100)}%`;
}
function renderPanSoon(){
  if(panFrame!==null)return;
  panFrame=requestAnimationFrame(()=>{panFrame=null;if(!destroyed)renderTransform();});
}
function applyInitialLayout() {
  if(!allowInitialLayout || !layoutReady || readOnly || viewport.clientWidth<=0)return false;
  const draft=model.getDraft();
  draft.questionCard.width=Math.min(layout.cardWidth,typeof layout.maxCardWidth==='number'?layout.maxCardWidth:layout.cardWidth);
  model.loadDraft(draft);allowInitialLayout=false;
  // Establish width before measuring a naturally sized HTML card.
  renderTransform();
  // Save the initial camera too, so reopening before the first Draft visit uses the declared position.
  draft.viewport=practiceViewport(draft.questionCard,viewport.clientWidth,0,viewport.clientHeight,object.offsetHeight,layout);
  model.loadDraft(draft);
  return true;
}
function renderTexts(draft){
  const existing=new Map(Array.from(texts.querySelectorAll('.whiteboard-text'),node=>[node.dataset.textId,node]));
  for(const text of draft.texts||[]){
    if(textEditor?.value.id===text.id)continue;
    const node=existing.get(text.id)||document.createElement('div');existing.delete(text.id);node.className='whiteboard-text';node.dataset.textId=text.id;node.tabIndex=canEdit()?0:-1;
    node.textContent=text.text;node.style.cssText=`left:${text.x}px;top:${text.y}px;width:${text.width}px;font-size:${text.size}px;color:${text.color}`;
    node.title=canEdit()?'双击编辑，拖动移动，Delete 删除':'';if(!node.parentElement)texts.append(node);
  }
  existing.forEach(node=>node.remove());
}
function finishText(commit){
  if(!textEditor)return;
  const editing=textEditor;textEditor=null;editing.node.onblur=null;
  if(commit){const value={...editing.value,text:editing.node.value};if(value.text.trim())model.putText(value);else model.removeText(value.id);}
  editing.node.remove();render();if(commit)changed();
}
function editText(value){
  if(!canEdit())return;finishText(true);
  const node=document.createElement('textarea');node.className='whiteboard-text-editor';node.setAttribute('aria-label','白板文本');node.maxLength=10000;node.placeholder='输入文本';node.value=value.text;
  node.style.cssText=`left:${value.x}px;top:${value.y}px;width:${value.width}px;font-size:${value.size}px;color:${value.color}`;
  textEditor={node,value};texts.append(node);renderTexts(model.getDraft());
  node.onpointerdown=event=>event.stopPropagation();node.onblur=()=>finishText(true);
  node.onkeydown=event=>{if(event.key==='Escape'||((event.ctrlKey||event.metaKey)&&event.key==='Enter')){event.preventDefault();event.stopPropagation();finishText(event.key!=='Escape');}};
  node.focus();
}
function shapePoints(start,end,shape){
  return shape==='LINE'?[start,end]:[start,{...end,y:start.y},end,{...start,y:end.y},start];
}
function uniqueId(prefix){return `${prefix}-${Date.now()}-${++sequence}-${Math.random().toString(36).slice(2)}`;}
function capturePointer(id){try{viewport.setPointerCapture?.(id);}catch{/* Document listeners also handle environments without usable pointer capture. */}}
function render() {
  const draft = model.getDraft(); renderTransform(draft);
  strokes.replaceChildren(); draft.strokes.forEach(s => drawStroke(strokes, s));
  renderTexts(draft);
  $('#undo').disabled = !canEdit() || !model.canUndo; $('#redo').disabled = !canEdit() || !model.canRedo;
  $('#clear').disabled = !canEdit() || (!draft.strokes.length&&!(draft.texts||[]).length);
  root.querySelectorAll('button[data-mode]').forEach(button=>{button.disabled=button.dataset.mode==='PAN'?!canNavigate():button.dataset.mode==='INTERACT'?readOnly:!canEdit();});
  for(const id of ['zoom-out','zoom-in','zoom-value'])$(`#${id}`).disabled=!canNavigate();
  $('#board-more').disabled=!canEdit();
  root.querySelectorAll('[data-paper-color]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.paperColor===(draft.paper?.color||DEFAULT_PAPER.color))));
  root.querySelectorAll('button[data-paper-pattern]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.paperPattern===(draft.paper?.pattern||DEFAULT_PAPER.pattern))));
  $('#status').textContent = `${draft.strokes.length} 笔 · 视口 ${Math.round(draft.viewport.x)}, ${Math.round(draft.viewport.y)} · ${Math.round(draft.viewport.zoom * 100)}%`;
  notifyUi();
}
function screenPoint(event) {
  const bounds = viewport.getBoundingClientRect();
  return { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
}
function worldPoint(event) {
  return { ...screenToWorld(screenPoint(event), model.getViewport()),
    pressure: Number.isFinite(event.pressure) && event.pressure >= 0 && event.pressure <= 1 ? event.pressure : 0.5 };
}
function observe(event) {
  counts[event.type] = (counts[event.type] || 0) + 1;
  events.push({ type: event.type, pointerType: event.pointerType, pressure: event.pressure, isTrusted: event.isTrusted });
  if (events.length > 128) events.shift();
}
function finish(commit) {
  if (!gesture) return;
  if(panFrame!==null){cancelAnimationFrame(panFrame);panFrame=null;}
  const current = gesture; gesture = null;
  if (['PEN','LINE','RECT'].includes(current.mode) && commit) model.addStroke(current.stroke);
  if (current.mode === 'ERASER'||current.mode==='MOVE_TEXT') model.endEdit();
  activeStroke.replaceChildren(); viewport.classList.remove('dragging');
  if (viewport.hasPointerCapture?.(current.pointerId)) viewport.releasePointerCapture(current.pointerId);
  render(); changed();
}
function setMode(next) {
  alive(); if (!Object.prototype.hasOwnProperty.call(helps, next)) throw new Error('Unknown tool mode');
  if (learningMode === 'PRACTICE' && next !== 'INTERACT') throw new Error('Practice annotations and viewport are locked');
  if (readOnly && next !== 'PAN' && !(learningMode === 'PRACTICE' && next === 'INTERACT')) throw new Error('History Canvas permits viewing only');
  finishText(true);finish(true); mode = next; root.dataset.mode = next;
  // Drawing never keeps a focused card input receiving keyboard events.
  if (next !== 'INTERACT' && world.contains(document.activeElement)) document.activeElement.blur();
  document.querySelectorAll('button[data-mode]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.mode === next)));
  $('#mode-help').textContent = helps[next];
  modeListeners.forEach(listener => listener(next));
  notifyUi();
}
listen(viewport, 'pointerdown', event => {
  observe(event);
  if(event.target.closest('.whiteboard-text-editor,[data-qf-host-controls]'))return;
  const textNode=event.target.closest('.whiteboard-text');
  if(mode==='INTERACT'&&textNode&&canEdit()&&!gesture&&event.isPrimary&&event.button===0){
    finishText(true);event.preventDefault();textNode.focus();model.beginEdit();gesture={mode:'MOVE_TEXT',pointerId:event.pointerId,start:worldPoint(event),text:model.getDraft().texts.find(t=>t.id===textNode.dataset.textId)};capturePointer(event.pointerId);return;
  }
  if (learningMode === 'PRACTICE' || (mode === 'PAN' ? !canNavigate() : !canEdit()) || mode === 'INTERACT' || gesture || !event.isPrimary || event.button !== 0) return;
  allowInitialLayout=false; // A user gesture outranks a late page declaration.
  if(mode==='TEXT'){event.preventDefault();const at=worldPoint(event);editText({id:uniqueId('text'),x:at.x,y:at.y,width:260,size:20,color:'#34313b',text:''});return;}
  event.preventDefault();
  gesture = { mode, pointerId: event.pointerId, last: screenPoint(event) };
  capturePointer(event.pointerId); viewport.classList.add('dragging');
  if (['PEN','LINE','RECT'].includes(mode)) {
    gesture.start=worldPoint(event);
    gesture.stroke = { id: uniqueId('stroke'), tool: 'PEN', color: '#7054a5', width: 2.4, points: [gesture.start] };
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
  if (['PEN','LINE','RECT'].includes(gesture.mode)) {
    if(gesture.mode==='PEN')gesture.stroke.points.push(worldPoint(event));else gesture.stroke.points=shapePoints(gesture.start,worldPoint(event),gesture.mode);
    activeStroke.replaceChildren(); drawStroke(activeStroke, gesture.stroke);
  } else if(gesture.mode==='MOVE_TEXT'){
    const at=worldPoint(event);model.putText({...gesture.text,x:gesture.text.x+at.x-gesture.start.x,y:gesture.text.y+at.y-gesture.start.y});renderTexts(model.getDraft());
  } else if (gesture.mode === 'PAN') {
    const next = screenPoint(event); model.pan(next.x - gesture.last.x, next.y - gesture.last.y);
    gesture.last = next; renderPanSoon();
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
  else if(gesture.mode==='LINE'||gesture.mode==='RECT')gesture.stroke.points=shapePoints(gesture.start,worldPoint(event),gesture.mode);
  else if (gesture.mode === 'ERASER') model.eraseAlong(gesture.lastWorld, worldPoint(event), 10 / model.getDraft().viewport.zoom);
  else if (gesture.mode === 'PAN') {
    const next = screenPoint(event); model.pan(next.x - gesture.last.x, next.y - gesture.last.y);
  }
  finish(true);
});
listen(document, 'pointercancel', event => { if (event.pointerId === gesture?.pointerId) finish(false); });
listen(viewport, 'lostpointercapture', event => { if (event.pointerId === gesture?.pointerId) finish(false); });
listen(window, 'blur', () => finish(false));
listen(texts,'dblclick',event=>{if(mode!=='INTERACT')return;const id=event.target.closest('.whiteboard-text')?.dataset.textId;const value=model.getDraft().texts?.find(t=>t.id===id);if(value){event.preventDefault();editText(value);}});
listen(document,'keydown',event=>{if(!canEdit()||mode!=='INTERACT'||!['Delete','Backspace'].includes(event.key))return;const id=event.target.closest('.whiteboard-text')?.dataset.textId;if(id){event.preventDefault();model.removeText(id);render();changed();}});
document.querySelectorAll('button[data-mode]').forEach(b => listen(b, 'click', () => setMode(b.dataset.mode)));
function editAction(action){finishText(true);finish(true);model[action]();render();changed();}
for (const action of ['undo', 'redo', 'clear']) listen($(`#${action}`), 'click', () => { if (canEdit()) editAction(action); });
listen(document, 'keydown', event => {
  if (!canEdit() || event.target.matches('input, textarea') || !(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'z') return;
  event.preventDefault();editAction(event.shiftKey?'redo':'undo');
});
listen($('#board-more'),'click',()=>{const menu=$('#board-menu');menu.hidden=!menu.hidden;$('#board-more').setAttribute('aria-expanded',String(!menu.hidden));});
listen(document,'pointerdown',event=>{if(!event.target.closest('.paper-tools')){$('#board-menu').hidden=true;$('#board-more').setAttribute('aria-expanded','false');}});
listen(document,'keydown',event=>{if(event.key==='Escape'){$('#board-menu').hidden=true;$('#board-more').setAttribute('aria-expanded','false');}});
root.querySelectorAll('[data-paper-color],[data-paper-pattern]').forEach(button=>listen(button,'click',()=>{
  if(canEdit())setPaper(button.dataset.paperColor?{color:button.dataset.paperColor}:{pattern:button.dataset.paperPattern});
}));
function setPaper(patch){
  if(!patch||typeof patch!=='object'||Array.isArray(patch)||Object.keys(patch).some(key=>!['color','pattern'].includes(key)))throw new TypeError('Invalid paper patch');
  const next={...(model.getPaper()||DEFAULT_PAPER),...patch};
  normalizePaper(next);finishText(true);finish(true);model.setPaper(next);render();changed();
}
function showJson(exporting) {
  if (!canEdit()) return;
  finish(true); $('#json-error').textContent = '';
  if (exporting || !$('#draft-json').value) $('#draft-json').value = JSON.stringify(model.getDraft(), null, 2);
  $('#draft-panel').hidden = false; $('#draft-json').focus();
}
listen($('#export'), 'click', () => showJson(true));
listen($('#import'), 'click', () => showJson(false));
listen($('#close-panel'), 'click', () => { $('#draft-panel').hidden = true; });
function loadDraft(json,{hasSavedDraft=true}={}) {
  alive(); finishText(false);finish(false); model.loadDraft(json); practiceScroll = 0;
  hasSavedGeometry=hasSavedDraft;allowInitialLayout=!hasSavedDraft&&!readOnly;
  applyInitialLayout();render();changed();
}
function setZoom(zoom, screenAnchor = { x: 0, y: 0 }) {
  alive(); if (!canNavigate()) return; finish(true); model.setZoom(zoom, screenAnchor); render(); changed();
}
function zoomBy(factor) {
  const zoom = Math.min(4, Math.max(0.1, model.getDraft().viewport.zoom * factor));
  setZoom(zoom, { x: viewport.clientWidth / 2, y: viewport.clientHeight / 2 });
}
function fitCard() {
  alive(); if (!canNavigate()) return; finish(true);
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
listen($('#zoom-value'),'click',()=>setZoom(1,{x:viewport.clientWidth/2,y:viewport.clientHeight/2}));
listen($('#load-json'), 'click', () => {
  if (!canEdit()) return;
  try { loadDraft($('#draft-json').value); $('#draft-panel').hidden = true; }
  catch (error) { $('#json-error').textContent = error.message; }
});
const api = Object.freeze({
  configureLayout(next,{ready=true}={}) {
    alive();const validated=validateLayout(layout,next);layout=validated;layoutReady=ready;
    const initialized=applyInitialLayout();
    render();if(initialized)changed();
  },
  layoutState(){alive();return {configuration:{...layout},cardWidth:model.getQuestionCard().width,hasSavedGeometry,initialLayoutApplied:!allowInitialLayout};},
  markSavedGeometry(){alive();hasSavedGeometry=true;allowInitialLayout=false;},
  uiState(){alive();return {learningMode,tool:mode,readOnly,canEdit:canEdit(),canNavigate:canNavigate(),canUndo:canEdit()&&model.canUndo,canRedo:canEdit()&&model.canRedo,zoom:displayViewport().zoom,paper:{...(model.getPaper()||DEFAULT_PAPER)}};},
  onUiChange(listener){alive();uiListeners.add(listener);return()=>uiListeners.delete(listener);},
  command(action,argument){
    alive();
    const denied=message=>{const error=new Error(message);error.code=readOnly?'READ_ONLY':'CAPABILITY_DENIED';throw error;};
    if(navigationLocked)denied('白板操作正在完成');
    if(action==='tool'){
      if(!Object.prototype.hasOwnProperty.call(helps,argument))throw new TypeError('Unknown tool mode');
      if(argument==='PAN'?!canNavigate():argument==='INTERACT'?readOnly&&learningMode!=='PRACTICE':!canEdit())denied('当前模式不能使用该工具');
      setMode(argument);
    }else if(['undo','redo','clear','paper'].includes(action)){
      if(!canEdit())denied('当前模式不能修改草稿');
      if(action==='paper')setPaper(argument);else editAction(action);
    }else if(['zoom','zoomBy'].includes(action)){
      if(!canNavigate())denied('练习视口已固定');
      if(typeof argument!=='number'||!Number.isFinite(argument)||argument<=0||(action==='zoom'&&(argument<.1||argument>4)))throw new TypeError('Invalid zoom');
      if(action==='zoomBy')zoomBy(argument);else setZoom(argument,{x:viewport.clientWidth/2,y:viewport.clientHeight/2});
    }else throw new TypeError('Unknown whiteboard operation');
    return api.uiState();
  },
  getDraft() { alive(); finishText(true);finish(true); return JSON.stringify(model.getDraft()); },
  loadDraft, setMode, setZoom, fitCard,
  focusElement(node) {
    alive(); if (!root.contains(node)) throw new Error('Focus target outside question card');
    finish(true); const target = node.getBoundingClientRect(), bounds = viewport.getBoundingClientRect();
    if (learningMode === 'PRACTICE') { scrollPractice(practiceScroll + target.top - bounds.top - 24); return; }
    model.pan(bounds.left + 24 - target.left, bounds.top + 24 - target.top); render(); changed();
  },
  onChange(listener) { alive(); changeListeners.add(listener); return () => changeListeners.delete(listener); },
  onModeChange(listener) { alive(); modeListeners.add(listener); return () => modeListeners.delete(listener); },
  setEditable(value) { alive(); finishText(true);finish(true); editable = Boolean(value);if(!editable){$('#board-menu').hidden=true;$('#board-more').setAttribute('aria-expanded','false');}render(); },
  setNavigationLocked(value) { alive(); finish(true); navigationLocked = Boolean(value); render(); },
  setLearningMode(next, { initializeViewport = false } = {}) {
    alive(); if (!['PRACTICE', 'DRAFT'].includes(next)) throw new Error('Invalid learning mode');
    modePolicy(next); finishText(true);finish(true);
    const oldBounds=learningMode==='PRACTICE'?viewport.getBoundingClientRect():root.getBoundingClientRect();
    const initial=next==='DRAFT' && initializeViewport
      ? practiceViewport(model.getDraft().questionCard,viewport.clientWidth,learningMode==='PRACTICE'?practiceScroll:0,oldBounds.height,object.offsetHeight,layout) : null;
    learningMode = next; if(next === 'PRACTICE') practiceScroll = 0; root.dataset.learningMode = next;
    setMode(readOnly && next === 'DRAFT' ? 'PAN' : 'INTERACT'); root.querySelector('.toolbar').hidden = next === 'PRACTICE';
    root.querySelector('footer').hidden = next === 'PRACTICE'; $('#draft-panel').hidden = true;$('#board-menu').hidden=true;
    if(initial){
      const bounds=viewport.getBoundingClientRect(),draft=model.getDraft();
      const camera={x:initial.x+(oldBounds.left-bounds.left)/initial.zoom,y:initial.y+(oldBounds.top-bounds.top)/initial.zoom,zoom:initial.zoom};
      // Keep long passages below the newly visible tools; short cards retain their screen position.
      camera.y=Math.max(camera.y,layout.padding/initial.zoom-draft.questionCard.y);
      model.loadDraft({...draft,viewport:camera});
    }
    render();if(initial)changed();
  },
  diagnostics() { return { supportedPointerEvents: typeof PointerEvent !== 'undefined', counts: { ...counts }, events: events.map(e => ({ ...e })), accessMode, mode, learningMode, displayViewport: { ...displayViewport() }, handlerCount: listeners.length, destroyed }; },
  destroy() {
    if (destroyed) return;
    finishText(false);finish(false); destroyed = true; changeListeners.clear(); modeListeners.clear();uiListeners.clear(); listeners.splice(0).forEach(remove => remove());
    if (root.contains(document.activeElement)) document.activeElement.blur();
    root.inert = true; root.style.pointerEvents = 'none';
    root.querySelectorAll('input, button, textarea').forEach(element => { element.disabled = true; });
    $('#status').textContent = '已销毁';
  }
});
function scrollPractice(value) {
  const zoom = displayViewport().zoom;
  const maximum = Math.max(0, object.offsetHeight * zoom + 2*layout.padding - viewport.clientHeight);
  practiceScroll = Math.max(0, Math.min(maximum, value)); renderTransform();
}
listen(viewport, 'wheel', event => {
  if (learningMode !== 'PRACTICE') {if((event.ctrlKey||event.metaKey)&&canNavigate()){event.preventDefault();const delta=event.deltaY*(event.deltaMode===1?16:1);setZoom(Math.min(4,Math.max(.1,model.getDraft().viewport.zoom*Math.exp(-delta*.002))),screenPoint(event));}return;}
  if (event.ctrlKey || event.metaKey) { event.preventDefault(); return; }
  if (event.target.closest('textarea, select, .essay-answer-scroll')) return;
  event.preventDefault(); scrollPractice(practiceScroll + event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? viewport.clientHeight : 1));
}, { passive: false });
function resized() {
  if(applyInitialLayout()){render();changed();}
  else if(learningMode==='PRACTICE')scrollPractice(practiceScroll);
}
listen(window, 'resize', resized);
if (typeof ResizeObserver !== 'undefined') {
  const observer = new ResizeObserver(resized);
  observer.observe(viewport); observer.observe(object); listeners.push(() => observer.disconnect());
}
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
