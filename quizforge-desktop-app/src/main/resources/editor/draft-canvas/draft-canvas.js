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
    return {
      schemaVersion: DRAFT_SCHEMA_VERSION,
      layoutVersion: DRAFT_LAYOUT_VERSION,
      viewport: { x: finite(view.x, "viewport.x"), y: finite(view.y, "viewport.y"), zoom: positive(view.zoom, "viewport.zoom") },
      questionCard: { x: finite(card.x, "questionCard.x"), y: finite(card.y, "questionCard.y"), width: positive(card.width, "questionCard.width") },
      strokes
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
  var _draft, _undo, _redo, _editDepth, _editBefore, _DraftModel_instances, record_fn, replaceStrokes_fn;
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
      __privateGet(this, _draft).viewport = next;
    }
    beginEdit() {
      if (__privateGet(this, _editDepth) === 0) __privateSet(this, _editBefore, clone(__privateGet(this, _draft).strokes));
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
      if (__privateGet(this, _draft).strokes.length === 0) return false;
      __privateMethod(this, _DraftModel_instances, replaceStrokes_fn).call(this, []);
      return true;
    }
    undo() {
      if (__privateGet(this, _editDepth)) throw new Error("Finish the current edit before undo");
      const entry = __privateGet(this, _undo).pop();
      if (!entry) return false;
      __privateGet(this, _draft).strokes = clone(entry.before);
      __privateGet(this, _redo).push(entry);
      return true;
    }
    redo() {
      if (__privateGet(this, _editDepth)) throw new Error("Finish the current edit before redo");
      const entry = __privateGet(this, _redo).pop();
      if (!entry) return false;
      __privateGet(this, _draft).strokes = clone(entry.after);
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
    const after = clone(__privateGet(this, _draft).strokes);
    if (JSON.stringify(before) === JSON.stringify(after)) return false;
    __privateGet(this, _undo).push({ before, after });
    __privateSet(this, _redo, []);
    return true;
  };
  replaceStrokes_fn = function(next) {
    const before = __privateGet(this, _editDepth) === 0 ? clone(__privateGet(this, _draft).strokes) : null;
    __privateGet(this, _draft).strokes = next;
    if (before) __privateMethod(this, _DraftModel_instances, record_fn).call(this, before);
  };

  // src/canvas/core.js
  function mountDraftCanvas(object3, { accessMode = "EDITABLE" } = {}) {
    if (!(object3 instanceof HTMLElement)) throw new TypeError("A live World object is required");
    if (!["EDITABLE", "READ_ONLY"].includes(accessMode)) throw new TypeError("Unknown Canvas access mode");
    const readOnly = accessMode === "READ_ONLY";
    const $ = (selector) => document.querySelector(selector);
    const root = $("#draft-canvas-root"), viewport2 = $("#viewport"), world = $("#world");
    const strokes = $("#strokes"), activeStroke = $("#active-stroke");
    const model = new DraftModel();
    const ns = "http://www.w3.org/2000/svg";
    const listeners = [], counts = {}, events = [];
    let mode = "INTERACT", gesture = null, destroyed = false, sequence = 0, editable = true;
    const changeListeners = /* @__PURE__ */ new Set();
    let lastNotified = JSON.stringify(model.getDraft());
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
      PAN: "\u5E73\u79FB\uFF1A\u62D6\u52A8\u767D\u677F\uFF0C\u9898\u5361\u4E0E\u7B14\u8FF9\u4E00\u8D77\u79FB\u52A8\u3002"
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
    function renderTransform(draft = model.getDraft()) {
      const v = draft.viewport, c = draft.questionCard;
      world.style.transform = `translate(${v.x * v.zoom}px, ${v.y * v.zoom}px) scale(${v.zoom})`;
      object3.style.left = `${c.x}px`;
      object3.style.top = `${c.y}px`;
      object3.style.width = `${c.width}px`;
      object3.style.minWidth = `${c.width}px`;
      object3.style.maxWidth = `${c.width}px`;
      viewport2.style.backgroundPosition = `${v.x * v.zoom}px ${v.y * v.zoom}px`;
    }
    function render() {
      const draft = model.getDraft();
      renderTransform(draft);
      strokes.replaceChildren();
      draft.strokes.forEach((s) => drawStroke(strokes, s));
      $("#undo").disabled = readOnly || !model.canUndo;
      $("#redo").disabled = readOnly || !model.canRedo;
      $("#clear").disabled = readOnly || !draft.strokes.length;
      $("#status").textContent = `${draft.strokes.length} \u7B14 \xB7 \u89C6\u53E3 ${Math.round(draft.viewport.x)}, ${Math.round(draft.viewport.y)} \xB7 ${Math.round(draft.viewport.zoom * 100)}%`;
    }
    function screenPoint(event) {
      const bounds = viewport2.getBoundingClientRect();
      return { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
    }
    function worldPoint(event) {
      return {
        ...screenToWorld(screenPoint(event), model.getDraft().viewport),
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
      const current = gesture;
      gesture = null;
      if (current.mode === "PEN" && commit) model.addStroke(current.stroke);
      if (current.mode === "ERASER") model.endEdit();
      activeStroke.replaceChildren();
      viewport2.classList.remove("dragging");
      if ((_a = viewport2.hasPointerCapture) == null ? void 0 : _a.call(viewport2, current.pointerId)) viewport2.releasePointerCapture(current.pointerId);
      render();
      changed();
    }
    function setMode(next) {
      alive();
      if (!Object.prototype.hasOwnProperty.call(helps, next)) throw new Error("Unknown tool mode");
      if (readOnly && next !== "PAN") throw new Error("History Canvas permits Pan only");
      finish(true);
      mode = next;
      root.dataset.mode = next;
      if (next !== "INTERACT" && object3.contains(document.activeElement)) document.activeElement.blur();
      document.querySelectorAll("button[data-mode]").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.mode === next)));
      $("#mode-help").textContent = helps[next];
    }
    listen(viewport2, "pointerdown", (event) => {
      var _a;
      observe(event);
      if (!editable || mode === "INTERACT" || gesture || !event.isPrimary || event.button !== 0) return;
      event.preventDefault();
      gesture = { mode, pointerId: event.pointerId, last: screenPoint(event) };
      (_a = viewport2.setPointerCapture) == null ? void 0 : _a.call(viewport2, event.pointerId);
      viewport2.classList.add("dragging");
      if (mode === "PEN") {
        gesture.stroke = { id: `stroke-${Date.now()}-${++sequence}-${Math.random().toString(36).slice(2)}`, tool: "PEN", color: "#7054a5", width: 2.4, points: [worldPoint(event)] };
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
      if (gesture.mode === "PEN") {
        gesture.stroke.points.push(worldPoint(event));
        activeStroke.replaceChildren();
        drawStroke(activeStroke, gesture.stroke);
      } else if (gesture.mode === "PAN") {
        const next = screenPoint(event);
        model.pan(next.x - gesture.last.x, next.y - gesture.last.y);
        gesture.last = next;
        renderTransform();
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
    document.querySelectorAll("button[data-mode]").forEach((b) => listen(b, "click", () => setMode(b.dataset.mode)));
    for (const action of ["undo", "redo", "clear"]) listen($(`#${action}`), "click", () => {
      if (!editable || readOnly) return;
      finish(true);
      model[action]();
      render();
      changed();
    });
    listen(document, "keydown", (event) => {
      if (!editable || readOnly || event.target.matches("input, textarea") || !(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== "z") return;
      event.preventDefault();
      finish(true);
      event.shiftKey ? model.redo() : model.undo();
      render();
      changed();
    });
    function showJson(exporting) {
      if (readOnly) return;
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
    function loadDraft(json) {
      alive();
      finish(false);
      model.loadDraft(json);
      render();
      changed();
    }
    function setZoom(zoom, screenAnchor = { x: 0, y: 0 }) {
      alive();
      if (!editable) return;
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
      if (!editable) return;
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
    listen($("#load-json"), "click", () => {
      if (!editable || readOnly) return;
      try {
        loadDraft($("#draft-json").value);
        $("#draft-panel").hidden = true;
      } catch (error) {
        $("#json-error").textContent = error.message;
      }
    });
    const api = Object.freeze({
      getDraft() {
        alive();
        finish(true);
        return JSON.stringify(model.getDraft());
      },
      loadDraft,
      setMode,
      setZoom,
      fitCard,
      onChange(listener) {
        alive();
        changeListeners.add(listener);
        return () => changeListeners.delete(listener);
      },
      setEditable(value) {
        alive();
        finish(true);
        editable = Boolean(value);
      },
      diagnostics() {
        return { supportedPointerEvents: typeof PointerEvent !== "undefined", counts: { ...counts }, events: events.map((e) => ({ ...e })), accessMode, mode, destroyed };
      },
      destroy() {
        if (destroyed) return;
        finish(false);
        destroyed = true;
        changeListeners.clear();
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
