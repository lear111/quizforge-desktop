(() => {
  var __typeError = (msg) => {
    throw TypeError(msg);
  };
  var __accessCheck = (obj, member, msg) => member.has(obj) || __typeError("Cannot " + msg);
  var __privateGet = (obj, member, getter) => (__accessCheck(obj, member, "read from private field"), getter ? getter.call(obj) : member.get(obj));
  var __privateAdd = (obj, member, value) => member.has(obj) ? __typeError("Cannot add the same private member more than once") : member instanceof WeakSet ? member.add(obj) : member.set(obj, value);
  var __privateSet = (obj, member, value, setter) => (__accessCheck(obj, member, "write to private field"), setter ? setter.call(obj, value) : member.set(obj, value), value);
  var __privateMethod = (obj, member, method) => (__accessCheck(obj, member, "access private method"), method);
  var __privateWrapper = (obj, member, setter, getter) => ({
    set _(value) {
      __privateSet(obj, member, value, setter);
    },
    get _() {
      return __privateGet(obj, member, getter);
    }
  });

  // src/canvas/document.js
  var DRAFT_SCHEMA_VERSION = "1.0";
  var DRAFT_LAYOUT_VERSION = "1";
  var DEFAULT_CARD_WIDTH = 720;
  var DEFAULT_PAPER = Object.freeze({ color: "#ffffff", pattern: "PLAIN" });
  var DEFAULT_COLOR = "#7660ab";
  function object(value, name) {
    if (!value || typeof value !== "object" || Array.isArray(value)) throw new TypeError(`${name} must be an object`);
    return value;
  }
  function finite(value, name) {
    if (typeof value !== "number" || !Number.isFinite(value)) throw new TypeError(`${name} must be a finite number`);
    return value;
  }
  function positive(value, name) {
    if (finite(value, name) <= 0) throw new RangeError(`${name} must be positive`);
    return value;
  }
  function inputObject(value) {
    return object(typeof value === "string" ? JSON.parse(value) : value, "draft");
  }
  function normalizeText(value) {
    object(value, "text");
    if (typeof value.id !== "string" || !value.id.trim()) throw new TypeError("text.id must be a nonempty stable string");
    if (typeof value.text !== "string" || value.text.length > 1e4) throw new TypeError("Invalid annotation text");
    if (typeof value.color !== "string" || !/^#[0-9a-f]{3}(?:[0-9a-f]{3}(?:[0-9a-f]{2})?)?$/i.test(value.color)) throw new TypeError("Invalid text color");
    return { id: value.id, x: finite(value.x, "text.x"), y: finite(value.y, "text.y"), width: positive(value.width, "text.width"), size: positive(value.size, "text.size"), color: value.color, text: value.text };
  }
  function normalizePaper(value) {
    object(value, "paper");
    if (typeof value.color !== "string" || !/^#[0-9a-f]{3}(?:[0-9a-f]{3}(?:[0-9a-f]{2})?)?$/i.test(value.color) || !["PLAIN", "DOTS", "LINES", "GRID"].includes(value.pattern)) throw new TypeError("Invalid paper");
    return { color: value.color, pattern: value.pattern };
  }
  function normalizeStroke(value, pocDefaults = false) {
    object(value, "stroke");
    if (typeof value.id !== "string" || !value.id.trim()) throw new TypeError("stroke.id must be a nonempty stable string");
    if (value.tool !== "PEN") throw new TypeError("stroke.tool must be PEN");
    const color = pocDefaults && value.color === void 0 ? DEFAULT_COLOR : value.color;
    if (typeof color !== "string" || !/^#[0-9a-f]{3}(?:[0-9a-f]{3}(?:[0-9a-f]{2})?)?$/i.test(color)) {
      throw new TypeError("stroke.color must be a hexadecimal CSS color");
    }
    if (!Array.isArray(value.points) || !value.points.length) throw new TypeError("stroke.points must contain at least one world point");
    return {
      id: value.id,
      tool: "PEN",
      color,
      width: positive(value.width, "stroke.width"),
      points: value.points.map((point2, index) => {
        object(point2, `stroke.points[${index}]`);
        const pressure = pocDefaults && point2.pressure === void 0 ? 0.5 : finite(point2.pressure, "pressure");
        if (pressure < 0 || pressure > 1) throw new RangeError("pressure must be between 0 and 1");
        return { x: finite(point2.x, "point.x"), y: finite(point2.y, "point.y"), pressure };
      })
    };
  }
  function parseDraftCanvasDocument(value) {
    const input = inputObject(value);
    if (input.schemaVersion !== DRAFT_SCHEMA_VERSION) throw new TypeError("Unsupported draft schemaVersion");
    if (input.layoutVersion !== DRAFT_LAYOUT_VERSION) throw new TypeError("Unsupported draft layoutVersion");
    const view = object(input.viewport, "viewport");
    const card = object(input.questionCard, "questionCard");
    if (!Array.isArray(input.strokes)) throw new TypeError("draft.strokes must be an array");
    const strokes = input.strokes.map((value2) => normalizeStroke(value2));
    if (new Set(strokes.map((value2) => value2.id)).size !== strokes.length) throw new TypeError("stroke ids must be unique");
    if (input.texts !== void 0 && !Array.isArray(input.texts)) throw new TypeError("draft.texts must be an array");
    const texts = (input.texts || []).map(normalizeText);
    if (new Set(texts.map((t) => t.id)).size !== texts.length) throw new TypeError("text ids must be unique");
    return {
      schemaVersion: DRAFT_SCHEMA_VERSION,
      layoutVersion: DRAFT_LAYOUT_VERSION,
      viewport: { x: finite(view.x, "viewport.x"), y: finite(view.y, "viewport.y"), zoom: positive(view.zoom, "viewport.zoom") },
      questionCard: { x: finite(card.x, "questionCard.x"), y: finite(card.y, "questionCard.y"), width: positive(card.width, "questionCard.width") },
      strokes,
      ...texts.length ? { texts } : {},
      ...input.paper == null ? {} : { paper: normalizePaper(input.paper) }
    };
  }
  function upgradePocDraft(value) {
    const input = inputObject(value);
    if (input.layoutVersion !== void 0) return parseDraftCanvasDocument(input);
    if (input.schemaVersion !== DRAFT_SCHEMA_VERSION) throw new TypeError("Unsupported draft schemaVersion");
    const card = object(input.questionCard, "questionCard");
    if (!Array.isArray(input.strokes)) throw new TypeError("draft.strokes must be an array");
    return parseDraftCanvasDocument({
      ...input,
      layoutVersion: DRAFT_LAYOUT_VERSION,
      questionCard: { ...card, width: card.width === void 0 ? DEFAULT_CARD_WIDTH : card.width },
      strokes: input.strokes.map((value2) => normalizeStroke(value2, true))
    });
  }
  function normalizePocDraft(value) {
    return upgradePocDraft(value);
  }
  function createDraftCanvasDocument() {
    return {
      schemaVersion: DRAFT_SCHEMA_VERSION,
      layoutVersion: DRAFT_LAYOUT_VERSION,
      viewport: { x: 0, y: 0, zoom: 1 },
      questionCard: { x: 120, y: 70, width: DEFAULT_CARD_WIDTH },
      strokes: []
    };
  }

  // src/model.js
  var clone = (value) => JSON.parse(JSON.stringify(value));
  function finite2(value, name) {
    if (typeof value !== "number" || !Number.isFinite(value)) {
      throw new TypeError(`${name} must be a finite number`);
    }
    return value;
  }
  function positive2(value, name) {
    if (finite2(value, name) <= 0) throw new RangeError(`${name} must be positive`);
    return value;
  }
  function object2(value, name) {
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      throw new TypeError(`${name} must be an object`);
    }
    return value;
  }
  function point(value, name) {
    object2(value, name);
    return { x: finite2(value.x, `${name}.x`), y: finite2(value.y, `${name}.y`) };
  }
  function viewport(value) {
    return { ...point(value, "viewport"), zoom: positive2(value.zoom, "viewport.zoom") };
  }
  function createDraft() {
    return createDraftCanvasDocument();
  }
  function screenToWorld(screen, transform) {
    const p = point(screen, "screen");
    const view = viewport(transform);
    return { x: finite2(p.x / view.zoom - view.x, "world.x"), y: finite2(p.y / view.zoom - view.y, "world.y") };
  }
  function worldToScreen(world, transform) {
    const p = point(world, "world");
    const view = viewport(transform);
    return { x: finite2((p.x + view.x) * view.zoom, "screen.x"), y: finite2((p.y + view.y) * view.zoom, "screen.y") };
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
    if (Math.max(a.x, b.x) < Math.min(c.x, d.x) || Math.max(c.x, d.x) < Math.min(a.x, b.x) || Math.max(a.y, b.y) < Math.min(c.y, d.y) || Math.max(c.y, d.y) < Math.min(a.y, b.y)) return false;
    const ac = cross(a, b, c);
    const ad = cross(a, b, d);
    const ca = cross(c, d, a);
    const cb = cross(c, d, b);
    return (ac <= 0 && ad >= 0 || ad <= 0 && ac >= 0) && (ca <= 0 && cb >= 0 || cb <= 0 && ca >= 0);
  }
  function segmentDistanceSquared(a, b, c, d) {
    if (intersects(a, b, c, d)) return 0;
    return Math.min(
      pointSegmentDistanceSquared(a, c, d),
      pointSegmentDistanceSquared(b, c, d),
      pointSegmentDistanceSquared(c, a, b),
      pointSegmentDistanceSquared(d, a, b)
    );
  }
  function strokeHit(value, from, to, radius) {
    const threshold = (radius + value.width / 2) ** 2;
    if (value.points.length === 1) return pointSegmentDistanceSquared(value.points[0], from, to) <= threshold;
    for (let index = 1; index < value.points.length; index++) {
      if (segmentDistanceSquared(from, to, value.points[index - 1], value.points[index]) <= threshold) return true;
    }
    return false;
  }
  var _draft, _undo, _redo, _editDepth, _editBefore, _DraftModel_instances, record_fn, replaceStrokes_fn, annotationState_fn, restoreAnnotations_fn;
  var DraftModel = class {
    constructor(draft = createDraft()) {
      __privateAdd(this, _DraftModel_instances);
      __privateAdd(this, _draft);
      __privateAdd(this, _undo, []);
      __privateAdd(this, _redo, []);
      __privateAdd(this, _editDepth, 0);
      __privateAdd(this, _editBefore, null);
      __privateSet(this, _draft, normalizePocDraft(draft));
    }
    getDraft() {
      return clone(__privateGet(this, _draft));
    }
    getViewport() {
      return { ...__privateGet(this, _draft).viewport };
    }
    getQuestionCard() {
      return { ...__privateGet(this, _draft).questionCard };
    }
    getPaper() {
      return __privateGet(this, _draft).paper ? { ...__privateGet(this, _draft).paper } : void 0;
    }
    get canUndo() {
      return __privateGet(this, _undo).length > 0;
    }
    get canRedo() {
      return __privateGet(this, _redo).length > 0;
    }
    /** Validate before replacing state so a malformed import leaves the existing draft intact. */
    loadDraft(value) {
      const next = normalizePocDraft(value);
      __privateSet(this, _draft, next);
      __privateSet(this, _undo, []);
      __privateSet(this, _redo, []);
      __privateSet(this, _editDepth, 0);
      __privateSet(this, _editBefore, null);
      return this.getDraft();
    }
    /** Pointer drag deltas arrive in screen pixels and become world translation offsets. */
    pan(dx, dy) {
      finite2(dx, "pan.dx");
      finite2(dy, "pan.dy");
      const x = finite2(__privateGet(this, _draft).viewport.x + dx / __privateGet(this, _draft).viewport.zoom, "viewport.x");
      const y = finite2(__privateGet(this, _draft).viewport.y + dy / __privateGet(this, _draft).viewport.zoom, "viewport.y");
      __privateGet(this, _draft).viewport.x = x;
      __privateGet(this, _draft).viewport.y = y;
    }
    /** Keep the world point under the screen anchor stationary; zoom never changes card/ink geometry. */
    setZoom(zoom, screenAnchor = { x: 0, y: 0 }) {
      positive2(zoom, "viewport.zoom");
      const anchor = point(screenAnchor, "screenAnchor");
      const world = screenToWorld(anchor, __privateGet(this, _draft).viewport);
      const next = {
        x: finite2(anchor.x / zoom - world.x, "viewport.x"),
        y: finite2(anchor.y / zoom - world.y, "viewport.y"),
        zoom
      };
      worldToScreen(world, next);
      worldToScreen({ x: 0, y: 0 }, next);
      worldToScreen(__privateGet(this, _draft).questionCard, next);
      worldToScreen({
        x: finite2(__privateGet(this, _draft).questionCard.x + __privateGet(this, _draft).questionCard.width, "questionCard.right"),
        y: __privateGet(this, _draft).questionCard.y
      }, next);
      for (const stroke of __privateGet(this, _draft).strokes) {
        for (const point2 of stroke.points) worldToScreen(point2, next);
      }
      for (const text of __privateGet(this, _draft).texts || []) worldToScreen(text, next);
      __privateGet(this, _draft).viewport = next;
    }
    beginEdit() {
      if (__privateGet(this, _editDepth) === 0) __privateSet(this, _editBefore, __privateMethod(this, _DraftModel_instances, annotationState_fn).call(this));
      __privateWrapper(this, _editDepth)._++;
    }
    endEdit() {
      if (__privateGet(this, _editDepth) === 0) return false;
      __privateWrapper(this, _editDepth)._--;
      if (__privateGet(this, _editDepth) > 0) return false;
      const before = __privateGet(this, _editBefore);
      __privateSet(this, _editBefore, null);
      return __privateMethod(this, _DraftModel_instances, record_fn).call(this, before);
    }
    setPaper(paper) {
      const next = normalizePaper(paper), before = __privateMethod(this, _DraftModel_instances, annotationState_fn).call(this);
      __privateGet(this, _draft).paper = next;
      __privateMethod(this, _DraftModel_instances, record_fn).call(this, before);
    }
    putText(text) {
      const next = normalizeText(text), before = __privateGet(this, _editDepth) === 0 ? __privateMethod(this, _DraftModel_instances, annotationState_fn).call(this) : null;
      __privateGet(this, _draft).texts = [...(__privateGet(this, _draft).texts || []).filter((t) => t.id !== next.id), next];
      if (before) __privateMethod(this, _DraftModel_instances, record_fn).call(this, before);
    }
    removeText(id) {
      const before = __privateGet(this, _editDepth) === 0 ? __privateMethod(this, _DraftModel_instances, annotationState_fn).call(this) : null;
      const texts = (__privateGet(this, _draft).texts || []).filter((t) => t.id !== id);
      if (texts.length) __privateGet(this, _draft).texts = texts;
      else delete __privateGet(this, _draft).texts;
      if (before) __privateMethod(this, _DraftModel_instances, record_fn).call(this, before);
    }
    addStroke(value) {
      const next = normalizeStroke(value, true);
      if (__privateGet(this, _draft).strokes.some((existing) => existing.id === next.id)) {
        throw new TypeError("stroke ids must be unique");
      }
      __privateMethod(this, _DraftModel_instances, replaceStrokes_fn).call(this, [...__privateGet(this, _draft).strokes, next]);
      return next.id;
    }
    eraseAt(location, radius = 10) {
      return this.eraseAlong(location, location, radius);
    }
    /** Swept segment hit testing catches ink between successive fast pointermove samples. */
    eraseAlong(start, end, radius = 10) {
      const from = point(start, "erase.from");
      const to = point(end, "erase.to");
      if (finite2(radius, "erase.radius") < 0) throw new RangeError("erase.radius must not be negative");
      const removed = __privateGet(this, _draft).strokes.filter((value) => strokeHit(value, from, to, radius));
      if (removed.length) {
        const ids = new Set(removed.map((value) => value.id));
        __privateMethod(this, _DraftModel_instances, replaceStrokes_fn).call(this, __privateGet(this, _draft).strokes.filter((value) => !ids.has(value.id)));
      }
      return removed.map((value) => value.id);
    }
    clear() {
      if (__privateGet(this, _draft).strokes.length === 0 && !(__privateGet(this, _draft).texts || []).length) return false;
      const before = __privateMethod(this, _DraftModel_instances, annotationState_fn).call(this);
      __privateGet(this, _draft).strokes = [];
      delete __privateGet(this, _draft).texts;
      __privateMethod(this, _DraftModel_instances, record_fn).call(this, before);
      return true;
    }
    undo() {
      if (__privateGet(this, _editDepth)) throw new Error("Finish the current edit before undo");
      const entry = __privateGet(this, _undo).pop();
      if (!entry) return false;
      __privateMethod(this, _DraftModel_instances, restoreAnnotations_fn).call(this, entry.before);
      __privateGet(this, _redo).push(entry);
      return true;
    }
    redo() {
      if (__privateGet(this, _editDepth)) throw new Error("Finish the current edit before redo");
      const entry = __privateGet(this, _redo).pop();
      if (!entry) return false;
      __privateMethod(this, _DraftModel_instances, restoreAnnotations_fn).call(this, entry.after);
      __privateGet(this, _undo).push(entry);
      return true;
    }
  };
  _draft = new WeakMap();
  _undo = new WeakMap();
  _redo = new WeakMap();
  _editDepth = new WeakMap();
  _editBefore = new WeakMap();
  _DraftModel_instances = new WeakSet();
  record_fn = function(before) {
    const after = __privateMethod(this, _DraftModel_instances, annotationState_fn).call(this);
    if (JSON.stringify(before) === JSON.stringify(after)) return false;
    __privateGet(this, _undo).push({ before, after });
    __privateSet(this, _redo, []);
    return true;
  };
  replaceStrokes_fn = function(next) {
    const before = __privateGet(this, _editDepth) === 0 ? __privateMethod(this, _DraftModel_instances, annotationState_fn).call(this) : null;
    __privateGet(this, _draft).strokes = next;
    if (before) __privateMethod(this, _DraftModel_instances, record_fn).call(this, before);
  };
  annotationState_fn = function() {
    return clone({ strokes: __privateGet(this, _draft).strokes, texts: __privateGet(this, _draft).texts || [], paper: __privateGet(this, _draft).paper || null });
  };
  restoreAnnotations_fn = function(state) {
    __privateGet(this, _draft).strokes = clone(state.strokes);
    if (state.texts.length) __privateGet(this, _draft).texts = clone(state.texts);
    else delete __privateGet(this, _draft).texts;
    if (state.paper) __privateGet(this, _draft).paper = clone(state.paper);
    else delete __privateGet(this, _draft).paper;
  };

  // src/shared/ui/layout.js
  var keys = ["cardWidth", "maxCardWidth", "horizontalAlign", "verticalAlign", "padding"];
  function defaultLayout(mode = "PRACTICE") {
    return Object.freeze({ cardWidth: 720, maxCardWidth: "100%", horizontalAlign: "center", verticalAlign: mode === "EDITOR" ? "top" : "center", padding: 20 });
  }
  function configureLayout(current, patch) {
    if (!patch || typeof patch !== "object" || Array.isArray(patch)) throw new TypeError("\u5E03\u5C40\u914D\u7F6E\u5FC5\u987B\u4E3A\u5BF9\u8C61");
    if (Object.keys(patch).some((key) => !keys.includes(key))) throw new TypeError("\u672A\u77E5\u5E03\u5C40\u5B57\u6BB5");
    const next = { ...current, ...patch };
    if (!Number.isFinite(next.cardWidth) || next.cardWidth < 64 || next.cardWidth > 8192) throw new TypeError("cardWidth \u5FC5\u987B\u4E3A 64\u20138192 \u7684\u6570\u503C");
    if (typeof next.maxCardWidth === "number") {
      if (!Number.isFinite(next.maxCardWidth) || next.maxCardWidth < 64 || next.maxCardWidth > 8192) throw new TypeError("maxCardWidth \u6570\u503C\u5FC5\u987B\u4E3A 64\u20138192");
    } else if (typeof next.maxCardWidth !== "string" || !/^(?:\d+(?:\.\d+)?)%$/.test(next.maxCardWidth) || parseFloat(next.maxCardWidth) <= 0 || parseFloat(next.maxCardWidth) > 100) {
      throw new TypeError("maxCardWidth \u5FC5\u987B\u4E3A\u6570\u503C\u6216\u5927\u4E8E 0\u3001\u4E0D\u8D85\u8FC7 100% \u7684\u767E\u5206\u6BD4");
    }
    if (!["left", "center", "right"].includes(next.horizontalAlign) || !["top", "center", "bottom"].includes(next.verticalAlign)) throw new TypeError("\u4E0D\u652F\u6301\u7684\u5BF9\u9F50\u65B9\u5F0F");
    if (!Number.isFinite(next.padding) || next.padding < 0 || next.padding > 256) throw new TypeError("padding \u5FC5\u987B\u4E3A 0\u2013256 \u7684\u6570\u503C");
    return Object.freeze(next);
  }
  function availableCardWidth(layout, width) {
    const available = Math.max(1, width - 2 * layout.padding);
    return Math.min(available, typeof layout.maxCardWidth === "number" ? layout.maxCardWidth : available * parseFloat(layout.maxCardWidth) / 100);
  }
  function alignedOffset(align, available, content, padding) {
    return Math.max(padding, align === "right" || align === "bottom" ? available - padding - content : align === "center" ? (available - content) / 2 : padding);
  }

  // src/learning/mode-policy.js
  var SharedLearningSurfaceMode = Object.freeze({ PRACTICE: "PRACTICE", DRAFT: "DRAFT" });
  var policies = Object.freeze({
    PRACTICE: Object.freeze({ answerInteractive: true, annotationsVisible: true, annotationEditing: false, userViewport: false }),
    DRAFT: Object.freeze({ answerInteractive: true, annotationsVisible: true, annotationEditing: true, userViewport: true }),
    HISTORY: Object.freeze({ answerInteractive: false, annotationsVisible: true, annotationEditing: false, userViewport: true })
  });
  function modePolicy(mode) {
    if (!Object.prototype.hasOwnProperty.call(policies, mode)) throw new TypeError("Unknown learning surface mode");
    return policies[mode];
  }
  function practiceViewport(card, width, scroll = 0, height = 0, cardHeight = 0, layout = defaultLayout()) {
    const zoom = Math.min(1, Math.max(0.1, availableCardWidth(layout, width) / card.width));
    const top = alignedOffset(layout.verticalAlign, height, cardHeight * zoom, layout.padding);
    const left = alignedOffset(layout.horizontalAlign, width, card.width * zoom, layout.padding);
    return { x: left / zoom - card.x, y: (top - scroll) / zoom - card.y, zoom };
  }

  // src/canvas/floating-tools.js
  var icon = (path) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
  var paths = {
    INTERACT: '<path d="m5 3 14 10-7 1-3 7z"/>',
    PAN: '<path d="M8 12V6a2 2 0 0 1 4 0v5-7a2 2 0 0 1 4 0v7-4a2 2 0 0 1 4 0v7c0 5-3 7-7 7-3 0-5-2-7-5l-3-4a2 2 0 0 1 3-2l2 2"/>',
    PEN: '<path d="m4 20 1-5L16 4a2 2 0 0 1 4 4L9 19zM14 6l4 4"/>',
    ERASER: '<path d="m4 13 9-9a2 2 0 0 1 3 0l5 5a2 2 0 0 1 0 3l-8 8H8l-4-4a2 2 0 0 1 0-3zM10 7l9 9M13 20h8"/>',
    LINE: '<path d="M4 20 20 4"/>',
    RECT: '<rect x="4" y="5" width="16" height="14" rx="1"/>',
    TEXT: '<path d="M4 5h16M12 5v15M8 20h8"/>',
    undo: '<path d="m8 5-5 5 5 5M3 10h10a7 7 0 0 1 7 7"/>',
    redo: '<path d="m16 5 5 5-5 5M21 10H11a7 7 0 0 0-7 7"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>'
  };
  function mountFloatingTools(root) {
    const labels = { INTERACT: "\u9009\u62E9", PAN: "\u62D6\u52A8", PEN: "\u753B\u7B14", ERASER: "\u6A61\u76AE", LINE: "\u76F4\u7EBF", RECT: "\u77E9\u5F62", TEXT: "\u6587\u672C" };
    const toolbar = root.querySelector(".toolbar");
    toolbar.classList.add("floating-toolbar");
    toolbar.innerHTML = `<div class="floating-group history-tools">${["undo", "redo"].map((id) => `<button type="button" id="${id}" aria-label="${id === "undo" ? "\u64A4\u9500" : "\u91CD\u505A"}" title="${id === "undo" ? "\u64A4\u9500 Ctrl+Z" : "\u91CD\u505A Ctrl+Shift+Z"}" disabled>${icon(paths[id])}</button>`).join("")}</div>
 <nav class="floating-group drawing-tools" aria-label="\u767D\u677F\u5DE5\u5177">${Object.entries(labels).map(([mode, label]) => `<button type="button" data-mode="${mode}" aria-pressed="${mode === "INTERACT"}" title="${mode === "PAN" ? "\u62D6\u52A8\u753B\u5E03\uFF1A\u6309\u4F4F\u9F20\u6807\u5DE6\u952E\u62D6\u52A8" : label}" aria-label="${label}">${icon(paths[mode])}<span>${label}</span></button>`).join("")}</nav>
 <div class="paper-tools"><button type="button" id="board-more" class="floating-group" aria-expanded="false" aria-controls="board-menu" title="\u66F4\u591A" aria-label="\u66F4\u591A">${icon(paths.more)}</button>
 <section id="board-menu" class="floating-group board-menu" hidden aria-label="\u767D\u677F\u8BBE\u7F6E"><strong>\u767D\u677F\u5E95\u8272</strong><div class="paper-colors">${[["#f5f4f7", "\u6D45\u7070"], ["#ffffff", "\u767D\u8272"], ["#fff7dc", "\u7EB8\u9EC4\u8272"], ["#f6eee3", "\u7C73\u8272"], ["#edf4ee", "\u6D45\u7EFF"]].map(([color, label]) => `<button type="button" data-paper-color="${color}" style="--paper-swatch:${color}" title="${label}" aria-label="${label}" aria-pressed="false"></button>`).join("")}</div><strong>\u767D\u677F\u6837\u5F0F</strong><div class="paper-patterns">${[["PLAIN", "\u7A7A\u767D"], ["LINES", "\u6A2A\u7EBF"], ["DOTS", "\u70B9\u5F0F"], ["GRID", "\u683C\u5B50"]].map(([pattern, label]) => `<button type="button" data-paper-pattern="${pattern}" aria-pressed="false"><span class="paper-preview" data-pattern="${pattern}"></span>${label}</button>`).join("")}</div></section></div>
 <div hidden><button id="clear" disabled></button><button id="export"></button><button id="import"></button></div>`;
    const footer = root.querySelector("footer");
    footer.classList.add("floating-footer");
    footer.innerHTML = '<span id="mode-help" class="visually-hidden"></span><div class="floating-group zoom-actions" aria-label="\u89C6\u53E3\u7F29\u653E"><button id="zoom-out" type="button" aria-label="\u7F29\u5C0F">\u2212</button><button id="zoom-value" type="button" title="\u6062\u590D 100%">100%</button><button id="zoom-in" type="button" aria-label="\u653E\u5927">+</button><button id="zoom-fit" type="button" hidden></button></div><span id="status" class="visually-hidden" role="status"></span>';
  }

  // src/canvas/core.js
  function mountDraftCanvas(object3, { accessMode = "EDITABLE" } = {}) {
    if (!(object3 instanceof HTMLElement)) throw new TypeError("A live World object is required");
    if (!["EDITABLE", "READ_ONLY"].includes(accessMode)) throw new TypeError("Unknown Canvas access mode");
    const readOnly = accessMode === "READ_ONLY";
    const $ = (selector) => document.querySelector(selector);
    const root = $("#draft-canvas-root"), viewport2 = $("#viewport"), world = $("#world");
    mountFloatingTools(root);
    const strokes = $("#strokes"), activeStroke = $("#active-stroke");
    const texts = document.createElement("div");
    texts.className = "whiteboard-texts";
    world.append(texts);
    const model = new DraftModel();
    const ns = "http://www.w3.org/2000/svg";
    const listeners = [], counts = {}, events = [];
    let mode = "INTERACT", gesture = null, destroyed = false, sequence = 0, editable = true;
    let learningMode = null, practiceScroll = 0, navigationLocked = false;
    let layout = defaultLayout(), layoutReady = false, allowInitialLayout = false, hasSavedGeometry = true;
    let textEditor = null, panFrame = null;
    const canEdit = () => editable && !readOnly && learningMode !== "PRACTICE";
    const canNavigate = () => learningMode !== "PRACTICE" && !navigationLocked;
    const displayViewport = () => learningMode === "PRACTICE" ? practiceViewport(model.getQuestionCard(), viewport2.clientWidth, practiceScroll, viewport2.clientHeight, object3.offsetHeight, layout) : model.getViewport();
    const changeListeners = /* @__PURE__ */ new Set();
    let lastNotified = JSON.stringify(model.getDraft());
    const modeListeners = /* @__PURE__ */ new Set(), uiListeners = /* @__PURE__ */ new Set();
    function notifyUi() {
      uiListeners.forEach((listener) => listener());
    }
    function changed() {
      const value = JSON.stringify(model.getDraft());
      if (value === lastNotified) return;
      lastNotified = value;
      if (!readOnly) changeListeners.forEach((listener) => listener(value));
    }
    const helps = {
      INTERACT: "\u4EA4\u4E92\uFF1A\u64CD\u4F5C\u9898\u5361\u5185\u5BB9\u3002",
      PEN: "\u753B\u7B14\uFF1A\u5728\u7A7A\u767D\u533A\u57DF\u6216\u9898\u5361\u4E0A\u4E66\u5199\u3002",
      ERASER: "\u6A61\u76AE\uFF1A\u5212\u8FC7\u7B14\u8FF9\uFF0C\u5220\u9664\u6574\u7B14\u3002",
      PAN: "\u62D6\u52A8\uFF1A\u6309\u4F4F\u9F20\u6807\u5DE6\u952E\uFF0C\u9898\u5361\u3001\u7B14\u8FF9\u548C\u6587\u672C\u4E00\u8D77\u79FB\u52A8\u3002",
      LINE: "\u76F4\u7EBF\uFF1A\u62D6\u52A8\u7ED8\u5236\u76F4\u7EBF\u3002",
      RECT: "\u77E9\u5F62\uFF1A\u62D6\u52A8\u7ED8\u5236\u77E9\u5F62\u3002",
      TEXT: "\u6587\u672C\uFF1A\u70B9\u51FB\u767D\u677F\u8F93\u5165\u6587\u5B57\uFF0CCtrl+Enter \u5B8C\u6210\u3002"
    };
    function listen(target, type, fn, options) {
      target.addEventListener(type, fn, options);
      listeners.push(() => target.removeEventListener(type, fn, options));
    }
    function alive() {
      if (destroyed) throw new Error("Draft Canvas has been destroyed");
    }
    function drawStroke(parent, stroke) {
      const line = document.createElementNS(ns, stroke.points.length === 1 ? "circle" : "polyline");
      line.setAttribute("data-stroke-id", stroke.id);
      if (stroke.points.length === 1) {
        line.setAttribute("cx", stroke.points[0].x);
        line.setAttribute("cy", stroke.points[0].y);
        line.setAttribute("r", stroke.width / 2);
        line.setAttribute("fill", stroke.color);
      } else {
        line.setAttribute("points", stroke.points.map((p) => `${p.x},${p.y}`).join(" "));
        line.setAttribute("fill", "none");
        line.setAttribute("stroke", stroke.color);
        line.setAttribute("stroke-width", stroke.width);
        line.setAttribute("stroke-linecap", "round");
        line.setAttribute("stroke-linejoin", "round");
      }
      parent.appendChild(line);
    }
    function renderTransform(draft = { questionCard: model.getQuestionCard(), paper: model.getPaper() }) {
      const v = displayViewport(), c = draft.questionCard;
      world.style.transform = `translate(${v.x * v.zoom}px, ${v.y * v.zoom}px) scale(${v.zoom})`;
      object3.style.left = `${c.x}px`;
      object3.style.top = `${c.y}px`;
      object3.style.width = `${c.width}px`;
      object3.style.minWidth = `${c.width}px`;
      object3.style.maxWidth = `${c.width}px`;
      viewport2.style.backgroundPosition = `${v.x * v.zoom}px ${v.y * v.zoom}px`;
      const paper = draft.paper || DEFAULT_PAPER;
      const size = (paper.pattern === "LINES" ? 32 : 24) * v.zoom;
      viewport2.dataset.paperPattern = paper.pattern;
      viewport2.style.backgroundSize = `${size}px ${size}px`;
      viewport2.style.backgroundColor = paper.color;
      root.style.backgroundColor = paper.color;
      $("#zoom-value").textContent = `${Math.round(v.zoom * 100)}%`;
    }
    function renderPanSoon() {
      if (panFrame !== null) return;
      panFrame = requestAnimationFrame(() => {
        panFrame = null;
        if (!destroyed) renderTransform();
      });
    }
    function applyInitialLayout() {
      if (!allowInitialLayout || !layoutReady || readOnly || viewport2.clientWidth <= 0) return false;
      const draft = model.getDraft();
      draft.questionCard.width = Math.min(layout.cardWidth, typeof layout.maxCardWidth === "number" ? layout.maxCardWidth : layout.cardWidth);
      model.loadDraft(draft);
      allowInitialLayout = false;
      renderTransform();
      draft.viewport = practiceViewport(draft.questionCard, viewport2.clientWidth, 0, viewport2.clientHeight, object3.offsetHeight, layout);
      model.loadDraft(draft);
      return true;
    }
    function renderTexts(draft) {
      const existing = new Map(Array.from(texts.querySelectorAll(".whiteboard-text"), (node) => [node.dataset.textId, node]));
      for (const text of draft.texts || []) {
        if ((textEditor == null ? void 0 : textEditor.value.id) === text.id) continue;
        const node = existing.get(text.id) || document.createElement("div");
        existing.delete(text.id);
        node.className = "whiteboard-text";
        node.dataset.textId = text.id;
        node.tabIndex = canEdit() ? 0 : -1;
        node.textContent = text.text;
        node.style.cssText = `left:${text.x}px;top:${text.y}px;width:${text.width}px;font-size:${text.size}px;color:${text.color}`;
        node.title = canEdit() ? "\u53CC\u51FB\u7F16\u8F91\uFF0C\u62D6\u52A8\u79FB\u52A8\uFF0CDelete \u5220\u9664" : "";
        if (!node.parentElement) texts.append(node);
      }
      existing.forEach((node) => node.remove());
    }
    function finishText(commit) {
      if (!textEditor) return;
      const editing = textEditor;
      textEditor = null;
      editing.node.onblur = null;
      if (commit) {
        const value = { ...editing.value, text: editing.node.value };
        if (value.text.trim()) model.putText(value);
        else model.removeText(value.id);
      }
      editing.node.remove();
      render();
      if (commit) changed();
    }
    function editText(value) {
      if (!canEdit()) return;
      finishText(true);
      const node = document.createElement("textarea");
      node.className = "whiteboard-text-editor";
      node.setAttribute("aria-label", "\u767D\u677F\u6587\u672C");
      node.maxLength = 1e4;
      node.placeholder = "\u8F93\u5165\u6587\u672C";
      node.value = value.text;
      node.style.cssText = `left:${value.x}px;top:${value.y}px;width:${value.width}px;font-size:${value.size}px;color:${value.color}`;
      textEditor = { node, value };
      texts.append(node);
      renderTexts(model.getDraft());
      node.onpointerdown = (event) => event.stopPropagation();
      node.onblur = () => finishText(true);
      node.onkeydown = (event) => {
        if (event.key === "Escape" || (event.ctrlKey || event.metaKey) && event.key === "Enter") {
          event.preventDefault();
          event.stopPropagation();
          finishText(event.key !== "Escape");
        }
      };
      node.focus();
    }
    function shapePoints(start, end, shape) {
      return shape === "LINE" ? [start, end] : [start, { ...end, y: start.y }, end, { ...start, y: end.y }, start];
    }
    function uniqueId(prefix) {
      return `${prefix}-${Date.now()}-${++sequence}-${Math.random().toString(36).slice(2)}`;
    }
    function capturePointer(id) {
      var _a;
      try {
        (_a = viewport2.setPointerCapture) == null ? void 0 : _a.call(viewport2, id);
      } catch (e) {
      }
    }
    function render() {
      const draft = model.getDraft();
      renderTransform(draft);
      strokes.replaceChildren();
      draft.strokes.forEach((s) => drawStroke(strokes, s));
      renderTexts(draft);
      $("#undo").disabled = !canEdit() || !model.canUndo;
      $("#redo").disabled = !canEdit() || !model.canRedo;
      $("#clear").disabled = !canEdit() || !draft.strokes.length && !(draft.texts || []).length;
      root.querySelectorAll("button[data-mode]").forEach((button) => {
        button.disabled = button.dataset.mode === "PAN" ? !canNavigate() : button.dataset.mode === "INTERACT" ? readOnly : !canEdit();
      });
      for (const id of ["zoom-out", "zoom-in", "zoom-value"]) $(`#${id}`).disabled = !canNavigate();
      $("#board-more").disabled = !canEdit();
      root.querySelectorAll("[data-paper-color]").forEach((b) => {
        var _a;
        return b.setAttribute("aria-pressed", String(b.dataset.paperColor === (((_a = draft.paper) == null ? void 0 : _a.color) || DEFAULT_PAPER.color)));
      });
      root.querySelectorAll("button[data-paper-pattern]").forEach((b) => {
        var _a;
        return b.setAttribute("aria-pressed", String(b.dataset.paperPattern === (((_a = draft.paper) == null ? void 0 : _a.pattern) || DEFAULT_PAPER.pattern)));
      });
      $("#status").textContent = `${draft.strokes.length} \u7B14 \xB7 \u89C6\u53E3 ${Math.round(draft.viewport.x)}, ${Math.round(draft.viewport.y)} \xB7 ${Math.round(draft.viewport.zoom * 100)}%`;
      notifyUi();
    }
    function screenPoint(event) {
      const bounds = viewport2.getBoundingClientRect();
      return { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
    }
    function worldPoint(event) {
      return {
        ...screenToWorld(screenPoint(event), model.getViewport()),
        pressure: Number.isFinite(event.pressure) && event.pressure >= 0 && event.pressure <= 1 ? event.pressure : 0.5
      };
    }
    function observe(event) {
      counts[event.type] = (counts[event.type] || 0) + 1;
      events.push({ type: event.type, pointerType: event.pointerType, pressure: event.pressure, isTrusted: event.isTrusted });
      if (events.length > 128) events.shift();
    }
    function finish(commit) {
      var _a;
      if (!gesture) return;
      if (panFrame !== null) {
        cancelAnimationFrame(panFrame);
        panFrame = null;
      }
      const current = gesture;
      gesture = null;
      if (["PEN", "LINE", "RECT"].includes(current.mode) && commit) model.addStroke(current.stroke);
      if (current.mode === "ERASER" || current.mode === "MOVE_TEXT") model.endEdit();
      activeStroke.replaceChildren();
      viewport2.classList.remove("dragging");
      if ((_a = viewport2.hasPointerCapture) == null ? void 0 : _a.call(viewport2, current.pointerId)) viewport2.releasePointerCapture(current.pointerId);
      render();
      changed();
    }
    function setMode(next) {
      alive();
      if (!Object.prototype.hasOwnProperty.call(helps, next)) throw new Error("Unknown tool mode");
      if (learningMode === "PRACTICE" && next !== "INTERACT") throw new Error("Practice annotations and viewport are locked");
      if (readOnly && next !== "PAN" && !(learningMode === "PRACTICE" && next === "INTERACT")) throw new Error("History Canvas permits viewing only");
      finishText(true);
      finish(true);
      mode = next;
      root.dataset.mode = next;
      if (next !== "INTERACT" && world.contains(document.activeElement)) document.activeElement.blur();
      document.querySelectorAll("button[data-mode]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.mode === next)));
      $("#mode-help").textContent = helps[next];
      modeListeners.forEach((listener) => listener(next));
      notifyUi();
    }
    listen(viewport2, "pointerdown", (event) => {
      observe(event);
      if (event.target.closest(".whiteboard-text-editor,[data-qf-host-controls]")) return;
      const textNode = event.target.closest(".whiteboard-text");
      if (mode === "INTERACT" && textNode && canEdit() && !gesture && event.isPrimary && event.button === 0) {
        finishText(true);
        event.preventDefault();
        textNode.focus();
        model.beginEdit();
        gesture = { mode: "MOVE_TEXT", pointerId: event.pointerId, start: worldPoint(event), text: model.getDraft().texts.find((t) => t.id === textNode.dataset.textId) };
        capturePointer(event.pointerId);
        return;
      }
      if (learningMode === "PRACTICE" || (mode === "PAN" ? !canNavigate() : !canEdit()) || mode === "INTERACT" || gesture || !event.isPrimary || event.button !== 0) return;
      allowInitialLayout = false;
      if (mode === "TEXT") {
        event.preventDefault();
        const at = worldPoint(event);
        editText({ id: uniqueId("text"), x: at.x, y: at.y, width: 260, size: 20, color: "#34313b", text: "" });
        return;
      }
      event.preventDefault();
      gesture = { mode, pointerId: event.pointerId, last: screenPoint(event) };
      capturePointer(event.pointerId);
      viewport2.classList.add("dragging");
      if (["PEN", "LINE", "RECT"].includes(mode)) {
        gesture.start = worldPoint(event);
        gesture.stroke = { id: uniqueId("stroke"), tool: "PEN", color: "#7054a5", width: 2.4, points: [gesture.start] };
        drawStroke(activeStroke, gesture.stroke);
      } else if (mode === "ERASER") {
        model.beginEdit();
        gesture.lastWorld = worldPoint(event);
        model.eraseAt(gesture.lastWorld, 10 / model.getDraft().viewport.zoom);
        render();
      }
    });
    listen(document, "pointermove", (event) => {
      if (viewport2.contains(event.target) || (gesture == null ? void 0 : gesture.pointerId) === event.pointerId) observe(event);
      if (!gesture || event.pointerId !== gesture.pointerId) return;
      event.preventDefault();
      if (["PEN", "LINE", "RECT"].includes(gesture.mode)) {
        if (gesture.mode === "PEN") gesture.stroke.points.push(worldPoint(event));
        else gesture.stroke.points = shapePoints(gesture.start, worldPoint(event), gesture.mode);
        activeStroke.replaceChildren();
        drawStroke(activeStroke, gesture.stroke);
      } else if (gesture.mode === "MOVE_TEXT") {
        const at = worldPoint(event);
        model.putText({ ...gesture.text, x: gesture.text.x + at.x - gesture.start.x, y: gesture.text.y + at.y - gesture.start.y });
        renderTexts(model.getDraft());
      } else if (gesture.mode === "PAN") {
        const next = screenPoint(event);
        model.pan(next.x - gesture.last.x, next.y - gesture.last.y);
        gesture.last = next;
        renderPanSoon();
      } else if (gesture.mode === "ERASER") {
        const next = worldPoint(event);
        model.eraseAlong(gesture.lastWorld, next, 10 / model.getDraft().viewport.zoom);
        gesture.lastWorld = next;
        render();
      }
    });
    listen(document, "pointerup", (event) => {
      if (viewport2.contains(event.target) || (gesture == null ? void 0 : gesture.pointerId) === event.pointerId) observe(event);
      if (event.pointerId !== (gesture == null ? void 0 : gesture.pointerId)) return;
      if (gesture.mode === "PEN") gesture.stroke.points.push(worldPoint(event));
      else if (gesture.mode === "LINE" || gesture.mode === "RECT") gesture.stroke.points = shapePoints(gesture.start, worldPoint(event), gesture.mode);
      else if (gesture.mode === "ERASER") model.eraseAlong(gesture.lastWorld, worldPoint(event), 10 / model.getDraft().viewport.zoom);
      else if (gesture.mode === "PAN") {
        const next = screenPoint(event);
        model.pan(next.x - gesture.last.x, next.y - gesture.last.y);
      }
      finish(true);
    });
    listen(document, "pointercancel", (event) => {
      if (event.pointerId === (gesture == null ? void 0 : gesture.pointerId)) finish(false);
    });
    listen(viewport2, "lostpointercapture", (event) => {
      if (event.pointerId === (gesture == null ? void 0 : gesture.pointerId)) finish(false);
    });
    listen(window, "blur", () => finish(false));
    listen(texts, "dblclick", (event) => {
      var _a, _b;
      if (mode !== "INTERACT") return;
      const id = (_a = event.target.closest(".whiteboard-text")) == null ? void 0 : _a.dataset.textId;
      const value = (_b = model.getDraft().texts) == null ? void 0 : _b.find((t) => t.id === id);
      if (value) {
        event.preventDefault();
        editText(value);
      }
    });
    listen(document, "keydown", (event) => {
      var _a;
      if (!canEdit() || mode !== "INTERACT" || !["Delete", "Backspace"].includes(event.key)) return;
      const id = (_a = event.target.closest(".whiteboard-text")) == null ? void 0 : _a.dataset.textId;
      if (id) {
        event.preventDefault();
        model.removeText(id);
        render();
        changed();
      }
    });
    document.querySelectorAll("button[data-mode]").forEach((b) => listen(b, "click", () => setMode(b.dataset.mode)));
    function editAction(action) {
      finishText(true);
      finish(true);
      model[action]();
      render();
      changed();
    }
    for (const action of ["undo", "redo", "clear"]) listen($(`#${action}`), "click", () => {
      if (canEdit()) editAction(action);
    });
    listen(document, "keydown", (event) => {
      if (!canEdit() || event.target.matches("input, textarea") || !(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== "z") return;
      event.preventDefault();
      editAction(event.shiftKey ? "redo" : "undo");
    });
    listen($("#board-more"), "click", () => {
      const menu = $("#board-menu");
      menu.hidden = !menu.hidden;
      $("#board-more").setAttribute("aria-expanded", String(!menu.hidden));
    });
    listen(document, "pointerdown", (event) => {
      if (!event.target.closest(".paper-tools")) {
        $("#board-menu").hidden = true;
        $("#board-more").setAttribute("aria-expanded", "false");
      }
    });
    listen(document, "keydown", (event) => {
      if (event.key === "Escape") {
        $("#board-menu").hidden = true;
        $("#board-more").setAttribute("aria-expanded", "false");
      }
    });
    root.querySelectorAll("[data-paper-color],[data-paper-pattern]").forEach((button) => listen(button, "click", () => {
      if (canEdit()) setPaper(button.dataset.paperColor ? { color: button.dataset.paperColor } : { pattern: button.dataset.paperPattern });
    }));
    function setPaper(patch) {
      if (!patch || typeof patch !== "object" || Array.isArray(patch) || Object.keys(patch).some((key) => !["color", "pattern"].includes(key))) throw new TypeError("Invalid paper patch");
      const next = { ...model.getPaper() || DEFAULT_PAPER, ...patch };
      normalizePaper(next);
      finishText(true);
      finish(true);
      model.setPaper(next);
      render();
      changed();
    }
    function showJson(exporting) {
      if (!canEdit()) return;
      finish(true);
      $("#json-error").textContent = "";
      if (exporting || !$("#draft-json").value) $("#draft-json").value = JSON.stringify(model.getDraft(), null, 2);
      $("#draft-panel").hidden = false;
      $("#draft-json").focus();
    }
    listen($("#export"), "click", () => showJson(true));
    listen($("#import"), "click", () => showJson(false));
    listen($("#close-panel"), "click", () => {
      $("#draft-panel").hidden = true;
    });
    function loadDraft(json, { hasSavedDraft = true } = {}) {
      alive();
      finishText(false);
      finish(false);
      model.loadDraft(json);
      practiceScroll = 0;
      hasSavedGeometry = hasSavedDraft;
      allowInitialLayout = !hasSavedDraft && !readOnly;
      applyInitialLayout();
      render();
      changed();
    }
    function setZoom(zoom, screenAnchor = { x: 0, y: 0 }) {
      alive();
      if (!canNavigate()) return;
      finish(true);
      model.setZoom(zoom, screenAnchor);
      render();
      changed();
    }
    function zoomBy(factor) {
      const zoom = Math.min(4, Math.max(0.1, model.getDraft().viewport.zoom * factor));
      setZoom(zoom, { x: viewport2.clientWidth / 2, y: viewport2.clientHeight / 2 });
    }
    function fitCard() {
      alive();
      if (!canNavigate()) return;
      finish(true);
      const card = model.getDraft().questionCard;
      const zoom = Math.min(1, Math.max(0.1, (viewport2.clientWidth - 40) / card.width));
      model.setZoom(zoom);
      const view = model.getDraft().viewport;
      model.pan(
        (viewport2.clientWidth - card.width * zoom) / 2 - (card.x + view.x) * zoom,
        20 - (card.y + view.y) * zoom
      );
      render();
      changed();
    }
    listen($("#zoom-out"), "click", () => zoomBy(1 / 1.2));
    listen($("#zoom-in"), "click", () => zoomBy(1.2));
    listen($("#zoom-fit"), "click", fitCard);
    listen($("#zoom-value"), "click", () => setZoom(1, { x: viewport2.clientWidth / 2, y: viewport2.clientHeight / 2 }));
    listen($("#load-json"), "click", () => {
      if (!canEdit()) return;
      try {
        loadDraft($("#draft-json").value);
        $("#draft-panel").hidden = true;
      } catch (error) {
        $("#json-error").textContent = error.message;
      }
    });
    const api = Object.freeze({
      configureLayout(next, { ready = true } = {}) {
        alive();
        const validated = configureLayout(layout, next);
        layout = validated;
        layoutReady = ready;
        const initialized = applyInitialLayout();
        render();
        if (initialized) changed();
      },
      layoutState() {
        alive();
        return { configuration: { ...layout }, cardWidth: model.getQuestionCard().width, hasSavedGeometry, initialLayoutApplied: !allowInitialLayout };
      },
      markSavedGeometry() {
        alive();
        hasSavedGeometry = true;
        allowInitialLayout = false;
      },
      uiState() {
        alive();
        return { learningMode, tool: mode, readOnly, canEdit: canEdit(), canNavigate: canNavigate(), canUndo: canEdit() && model.canUndo, canRedo: canEdit() && model.canRedo, zoom: displayViewport().zoom, paper: { ...model.getPaper() || DEFAULT_PAPER } };
      },
      onUiChange(listener) {
        alive();
        uiListeners.add(listener);
        return () => uiListeners.delete(listener);
      },
      command(action, argument) {
        alive();
        const denied = (message) => {
          const error = new Error(message);
          error.code = readOnly ? "READ_ONLY" : "CAPABILITY_DENIED";
          throw error;
        };
        if (navigationLocked) denied("\u767D\u677F\u64CD\u4F5C\u6B63\u5728\u5B8C\u6210");
        if (action === "tool") {
          if (!Object.prototype.hasOwnProperty.call(helps, argument)) throw new TypeError("Unknown tool mode");
          if (argument === "PAN" ? !canNavigate() : argument === "INTERACT" ? readOnly && learningMode !== "PRACTICE" : !canEdit()) denied("\u5F53\u524D\u6A21\u5F0F\u4E0D\u80FD\u4F7F\u7528\u8BE5\u5DE5\u5177");
          setMode(argument);
        } else if (["undo", "redo", "clear", "paper"].includes(action)) {
          if (!canEdit()) denied("\u5F53\u524D\u6A21\u5F0F\u4E0D\u80FD\u4FEE\u6539\u8349\u7A3F");
          if (action === "paper") setPaper(argument);
          else editAction(action);
        } else if (["zoom", "zoomBy"].includes(action)) {
          if (!canNavigate()) denied("\u7EC3\u4E60\u89C6\u53E3\u5DF2\u56FA\u5B9A");
          if (typeof argument !== "number" || !Number.isFinite(argument) || argument <= 0 || action === "zoom" && (argument < 0.1 || argument > 4)) throw new TypeError("Invalid zoom");
          if (action === "zoomBy") zoomBy(argument);
          else setZoom(argument, { x: viewport2.clientWidth / 2, y: viewport2.clientHeight / 2 });
        } else throw new TypeError("Unknown whiteboard operation");
        return api.uiState();
      },
      getDraft() {
        alive();
        finishText(true);
        finish(true);
        return JSON.stringify(model.getDraft());
      },
      loadDraft,
      setMode,
      setZoom,
      fitCard,
      focusElement(node) {
        alive();
        if (!root.contains(node)) throw new Error("Focus target outside question card");
        finish(true);
        const target = node.getBoundingClientRect(), bounds = viewport2.getBoundingClientRect();
        if (learningMode === "PRACTICE") {
          scrollPractice(practiceScroll + target.top - bounds.top - 24);
          return;
        }
        model.pan(bounds.left + 24 - target.left, bounds.top + 24 - target.top);
        render();
        changed();
      },
      onChange(listener) {
        alive();
        changeListeners.add(listener);
        return () => changeListeners.delete(listener);
      },
      onModeChange(listener) {
        alive();
        modeListeners.add(listener);
        return () => modeListeners.delete(listener);
      },
      setEditable(value) {
        alive();
        finishText(true);
        finish(true);
        editable = Boolean(value);
        if (!editable) {
          $("#board-menu").hidden = true;
          $("#board-more").setAttribute("aria-expanded", "false");
        }
        render();
      },
      setNavigationLocked(value) {
        alive();
        finish(true);
        navigationLocked = Boolean(value);
        render();
      },
      setLearningMode(next, { initializeViewport = false } = {}) {
        alive();
        if (!["PRACTICE", "DRAFT"].includes(next)) throw new Error("Invalid learning mode");
        modePolicy(next);
        finishText(true);
        finish(true);
        const oldBounds = learningMode === "PRACTICE" ? viewport2.getBoundingClientRect() : root.getBoundingClientRect();
        const initial = next === "DRAFT" && initializeViewport ? practiceViewport(model.getDraft().questionCard, viewport2.clientWidth, learningMode === "PRACTICE" ? practiceScroll : 0, oldBounds.height, object3.offsetHeight, layout) : null;
        learningMode = next;
        if (next === "PRACTICE") practiceScroll = 0;
        root.dataset.learningMode = next;
        setMode(readOnly && next === "DRAFT" ? "PAN" : "INTERACT");
        root.querySelector(".toolbar").hidden = next === "PRACTICE";
        root.querySelector("footer").hidden = next === "PRACTICE";
        $("#draft-panel").hidden = true;
        $("#board-menu").hidden = true;
        if (initial) {
          const bounds = viewport2.getBoundingClientRect(), draft = model.getDraft();
          const camera = { x: initial.x + (oldBounds.left - bounds.left) / initial.zoom, y: initial.y + (oldBounds.top - bounds.top) / initial.zoom, zoom: initial.zoom };
          camera.y = Math.max(camera.y, layout.padding / initial.zoom - draft.questionCard.y);
          model.loadDraft({ ...draft, viewport: camera });
        }
        render();
        if (initial) changed();
      },
      diagnostics() {
        return { supportedPointerEvents: typeof PointerEvent !== "undefined", counts: { ...counts }, events: events.map((e) => ({ ...e })), accessMode, mode, learningMode, displayViewport: { ...displayViewport() }, handlerCount: listeners.length, destroyed };
      },
      destroy() {
        if (destroyed) return;
        finishText(false);
        finish(false);
        destroyed = true;
        changeListeners.clear();
        modeListeners.clear();
        uiListeners.clear();
        listeners.splice(0).forEach((remove) => remove());
        if (root.contains(document.activeElement)) document.activeElement.blur();
        root.inert = true;
        root.style.pointerEvents = "none";
        root.querySelectorAll("input, button, textarea").forEach((element) => {
          element.disabled = true;
        });
        $("#status").textContent = "\u5DF2\u9500\u6BC1";
      }
    });
    function scrollPractice(value) {
      const zoom = displayViewport().zoom;
      const maximum = Math.max(0, object3.offsetHeight * zoom + 2 * layout.padding - viewport2.clientHeight);
      practiceScroll = Math.max(0, Math.min(maximum, value));
      renderTransform();
    }
    listen(viewport2, "wheel", (event) => {
      if (learningMode !== "PRACTICE") {
        if ((event.ctrlKey || event.metaKey) && canNavigate()) {
          event.preventDefault();
          const delta = event.deltaY * (event.deltaMode === 1 ? 16 : 1);
          setZoom(Math.min(4, Math.max(0.1, model.getDraft().viewport.zoom * Math.exp(-delta * 2e-3))), screenPoint(event));
        }
        return;
      }
      if (event.ctrlKey || event.metaKey) {
        event.preventDefault();
        return;
      }
      if (event.target.closest("textarea, select, .essay-answer-scroll")) return;
      event.preventDefault();
      scrollPractice(practiceScroll + event.deltaY * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? viewport2.clientHeight : 1));
    }, { passive: false });
    function resized() {
      if (applyInitialLayout()) {
        render();
        changed();
      } else if (learningMode === "PRACTICE") scrollPractice(practiceScroll);
    }
    listen(window, "resize", resized);
    if (typeof ResizeObserver !== "undefined") {
      const observer = new ResizeObserver(resized);
      observer.observe(viewport2);
      observer.observe(object3);
      listeners.push(() => observer.disconnect());
    }
    render();
    root.dataset.accessMode = accessMode;
    if (readOnly) {
      document.querySelectorAll("button[data-mode]").forEach((button) => {
        if (button.dataset.mode !== "PAN") {
          button.hidden = true;
          button.disabled = true;
        }
      });
      for (const id of ["undo", "redo", "clear", "export", "import", "load-json"]) {
        $(`#${id}`).hidden = true;
        $(`#${id}`).disabled = true;
      }
      setMode("PAN");
    }
    if (typeof PointerEvent === "undefined") $("#mode-help").textContent = "\u5F53\u524D\u8FD0\u884C\u73AF\u5883\u7F3A\u5C11 Pointer Events\u3002";
    return Object.freeze({ ...api, addDisposer(dispose) {
      alive();
      listeners.push(dispose);
    } });
  }

  // src/app.js
  var canvas = mountDraftCanvas(document.querySelector("#question-card"));
  window.draftCanvas = canvas;
  var form = document.querySelector("#question-form");
  var submitted = (event) => {
    var _a;
    event.preventDefault();
    const chosen = (_a = document.querySelector("input[name=answer]:checked")) == null ? void 0 : _a.value;
    document.querySelector("#card-feedback").textContent = chosen ? `\u5DF2\u63D0\u4EA4\u6D4B\u8BD5\u7B54\u6848 ${chosen}\u3002${document.querySelector("#answer-note").value}` : "\u8BF7\u5148\u9009\u62E9\u4E00\u4E2A\u7B54\u6848\u3002";
  };
  form.addEventListener("submit", submitted);
  canvas.addDisposer(() => form.removeEventListener("submit", submitted));
})();
