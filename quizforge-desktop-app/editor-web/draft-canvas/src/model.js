/** Draft Canvas v1. Durable geometry is always expressed in world coordinates. */
import { createDraftCanvasDocument, normalizePocDraft, normalizeStroke, normalizeText, normalizePaper } from './canvas/document.js';

const clone = value => JSON.parse(JSON.stringify(value));

function finite(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new TypeError(`${name} must be a finite number`);
  }
  return value;
}

function positive(value, name) {
  if (finite(value, name) <= 0) throw new RangeError(`${name} must be positive`);
  return value;
}

function object(value, name) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new TypeError(`${name} must be an object`);
  }
  return value;
}

function point(value, name) {
  object(value, name);
  return { x: finite(value.x, `${name}.x`), y: finite(value.y, `${name}.y`) };
}

function viewport(value) {
  return { ...point(value, 'viewport'), zoom: positive(value.zoom, 'viewport.zoom') };
}

export function createDraft() {
  return createDraftCanvasDocument();
}

/** Screen coordinates are relative to the viewport element, not client/page coordinates.
 * Persisted viewport offsets are world units: screen = (world + offset) * zoom.
 */
export function screenToWorld(screen, transform) {
  const p = point(screen, 'screen');
  const view = viewport(transform);
  return { x: finite(p.x / view.zoom - view.x, 'world.x'), y: finite(p.y / view.zoom - view.y, 'world.y') };
}

export function worldToScreen(world, transform) {
  const p = point(world, 'world');
  const view = viewport(transform);
  return { x: finite((p.x + view.x) * view.zoom, 'screen.x'), y: finite((p.y + view.y) * view.zoom, 'screen.y') };
}

function pointSegmentDistanceSquared(p, a, b) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const length = dx * dx + dy * dy;
  const t = length === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / length));
  return (p.x - a.x - t * dx) ** 2 + (p.y - a.y - t * dy) ** 2;
}

function cross(a, b, c) {
  return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
}

function intersects(a, b, c, d) {
  if (Math.max(a.x, b.x) < Math.min(c.x, d.x) || Math.max(c.x, d.x) < Math.min(a.x, b.x)
    || Math.max(a.y, b.y) < Math.min(c.y, d.y) || Math.max(c.y, d.y) < Math.min(a.y, b.y)) return false;
  const ac = cross(a, b, c);
  const ad = cross(a, b, d);
  const ca = cross(c, d, a);
  const cb = cross(c, d, b);
  return ((ac <= 0 && ad >= 0) || (ad <= 0 && ac >= 0))
    && ((ca <= 0 && cb >= 0) || (cb <= 0 && ca >= 0));
}

function segmentDistanceSquared(a, b, c, d) {
  if (intersects(a, b, c, d)) return 0;
  return Math.min(pointSegmentDistanceSquared(a, c, d), pointSegmentDistanceSquared(b, c, d),
    pointSegmentDistanceSquared(c, a, b), pointSegmentDistanceSquared(d, a, b));
}

function strokeHit(value, from, to, radius) {
  const threshold = (radius + value.width / 2) ** 2;
  if (value.points.length === 1) return pointSegmentDistanceSquared(value.points[0], from, to) <= threshold;
  for (let index = 1; index < value.points.length; index++) {
    if (segmentDistanceSquared(from, to, value.points[index - 1], value.points[index]) <= threshold) return true;
  }
  return false;
}

export class DraftModel {
  #draft;
  #undo = [];
  #redo = [];
  #editDepth = 0;
  #editBefore = null;

  constructor(draft = createDraft()) {
    this.#draft = normalizePocDraft(draft);
  }

  getDraft() { return clone(this.#draft); }
  getViewport() { return {...this.#draft.viewport}; }
  getQuestionCard() { return {...this.#draft.questionCard}; }
  getPaper() { return this.#draft.paper?{...this.#draft.paper}:undefined; }
  get canUndo() { return this.#undo.length > 0; }
  get canRedo() { return this.#redo.length > 0; }

  /** Validate before replacing state so a malformed import leaves the existing draft intact. */
  loadDraft(value) {
    const next = normalizePocDraft(value);
    this.#draft = next;
    this.#undo = [];
    this.#redo = [];
    this.#editDepth = 0;
    this.#editBefore = null;
    return this.getDraft();
  }

  /** Pointer drag deltas arrive in screen pixels and become world translation offsets. */
  pan(dx, dy) {
    finite(dx, 'pan.dx');
    finite(dy, 'pan.dy');
    const x = finite(this.#draft.viewport.x + dx / this.#draft.viewport.zoom, 'viewport.x');
    const y = finite(this.#draft.viewport.y + dy / this.#draft.viewport.zoom, 'viewport.y');
    this.#draft.viewport.x = x;
    this.#draft.viewport.y = y;
  }

  /** Keep the world point under the screen anchor stationary; zoom never changes card/ink geometry. */
  setZoom(zoom, screenAnchor = { x: 0, y: 0 }) {
    positive(zoom, 'viewport.zoom');
    const anchor = point(screenAnchor, 'screenAnchor');
    const world = screenToWorld(anchor, this.#draft.viewport);
    const next = { x: finite(anchor.x / zoom - world.x, 'viewport.x'),
      y: finite(anchor.y / zoom - world.y, 'viewport.y'), zoom };
    // Reject projection overflow before changing any state.
    worldToScreen(world, next);
    worldToScreen({ x: 0, y: 0 }, next);
    worldToScreen(this.#draft.questionCard, next);
    worldToScreen({ x: finite(this.#draft.questionCard.x + this.#draft.questionCard.width, 'questionCard.right'),
      y: this.#draft.questionCard.y }, next);
    for (const stroke of this.#draft.strokes) {
      for (const point of stroke.points) worldToScreen(point, next);
    }
    for(const text of this.#draft.texts||[])worldToScreen(text,next);
    this.#draft.viewport = next;
  }

  beginEdit() {
    if (this.#editDepth === 0) this.#editBefore = this.#annotationState();
    this.#editDepth++;
  }

  endEdit() {
    if (this.#editDepth === 0) return false;
    this.#editDepth--;
    if (this.#editDepth > 0) return false;
    const before = this.#editBefore;
    this.#editBefore = null;
    return this.#record(before);
  }

  #record(before) {
    const after = this.#annotationState();
    if (JSON.stringify(before) === JSON.stringify(after)) return false;
    this.#undo.push({ before, after });
    this.#redo = [];
    return true;
  }

  #replaceStrokes(next) {
    const before = this.#editDepth === 0 ? this.#annotationState() : null;
    this.#draft.strokes = next;
    if (before) this.#record(before);
  }

  #annotationState(){return clone({strokes:this.#draft.strokes,texts:this.#draft.texts||[],paper:this.#draft.paper||null});}
  #restoreAnnotations(state){this.#draft.strokes=clone(state.strokes);if(state.texts.length)this.#draft.texts=clone(state.texts);else delete this.#draft.texts;if(state.paper)this.#draft.paper=clone(state.paper);else delete this.#draft.paper;}
  setPaper(paper){const next=normalizePaper(paper),before=this.#annotationState();this.#draft.paper=next;this.#record(before);}
  putText(text){const next=normalizeText(text),before=this.#editDepth===0?this.#annotationState():null;this.#draft.texts=[...(this.#draft.texts||[]).filter(t=>t.id!==next.id),next];if(before)this.#record(before);}
  removeText(id){const before=this.#editDepth===0?this.#annotationState():null;const texts=(this.#draft.texts||[]).filter(t=>t.id!==id);if(texts.length)this.#draft.texts=texts;else delete this.#draft.texts;if(before)this.#record(before);}

  addStroke(value) {
    const next = normalizeStroke(value, true);
    if (this.#draft.strokes.some(existing => existing.id === next.id)) {
      throw new TypeError('stroke ids must be unique');
    }
    this.#replaceStrokes([...this.#draft.strokes, next]);
    return next.id;
  }

  eraseAt(location, radius = 10) { return this.eraseAlong(location, location, radius); }

  /** Swept segment hit testing catches ink between successive fast pointermove samples. */
  eraseAlong(start, end, radius = 10) {
    const from = point(start, 'erase.from');
    const to = point(end, 'erase.to');
    if (finite(radius, 'erase.radius') < 0) throw new RangeError('erase.radius must not be negative');
    const removed = this.#draft.strokes.filter(value => strokeHit(value, from, to, radius));
    if (removed.length) {
      const ids = new Set(removed.map(value => value.id));
      this.#replaceStrokes(this.#draft.strokes.filter(value => !ids.has(value.id)));
    }
    return removed.map(value => value.id);
  }

  clear() {
    if (this.#draft.strokes.length === 0 && !(this.#draft.texts||[]).length) return false;
    const before=this.#annotationState();this.#draft.strokes=[];delete this.#draft.texts;this.#record(before);
    return true;
  }

  undo() {
    if (this.#editDepth) throw new Error('Finish the current edit before undo');
    const entry = this.#undo.pop();
    if (!entry) return false;
    this.#restoreAnnotations(entry.before);
    this.#redo.push(entry);
    return true;
  }

  redo() {
    if (this.#editDepth) throw new Error('Finish the current edit before redo');
    const entry = this.#redo.pop();
    if (!entry) return false;
    this.#restoreAnnotations(entry.after);
    this.#undo.push(entry);
    return true;
  }
}
