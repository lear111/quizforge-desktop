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
    const card2 = object(input.questionCard, "questionCard");
    if (!Array.isArray(input.strokes)) throw new TypeError("draft.strokes must be an array");
    const strokes = input.strokes.map((value2) => normalizeStroke(value2));
    if (new Set(strokes.map((value2) => value2.id)).size !== strokes.length) throw new TypeError("stroke ids must be unique");
    return {
      schemaVersion: DRAFT_SCHEMA_VERSION,
      layoutVersion: DRAFT_LAYOUT_VERSION,
      viewport: { x: finite(view.x, "viewport.x"), y: finite(view.y, "viewport.y"), zoom: positive(view.zoom, "viewport.zoom") },
      questionCard: { x: finite(card2.x, "questionCard.x"), y: finite(card2.y, "questionCard.y"), width: positive(card2.width, "questionCard.width") },
      strokes
    };
  }
  function upgradePocDraft(value) {
    const input = inputObject(value);
    if (input.layoutVersion !== void 0) return parseDraftCanvasDocument(input);
    if (input.schemaVersion !== DRAFT_SCHEMA_VERSION) throw new TypeError("Unsupported draft schemaVersion");
    const card2 = object(input.questionCard, "questionCard");
    if (!Array.isArray(input.strokes)) throw new TypeError("draft.strokes must be an array");
    return parseDraftCanvasDocument({
      ...input,
      layoutVersion: DRAFT_LAYOUT_VERSION,
      questionCard: { ...card2, width: card2.width === void 0 ? DEFAULT_CARD_WIDTH : card2.width },
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
  function mountDraftCanvas(object4, { accessMode = "EDITABLE" } = {}) {
    if (!(object4 instanceof HTMLElement)) throw new TypeError("A live World object is required");
    if (!["EDITABLE", "READ_ONLY"].includes(accessMode)) throw new TypeError("Unknown Canvas access mode");
    const readOnly = accessMode === "READ_ONLY";
    const $ = (selector) => document.querySelector(selector);
    const root = $("#draft-canvas-root"), viewport2 = $("#viewport"), world = $("#world");
    const strokes = $("#strokes"), activeStroke = $("#active-stroke");
    const model = new DraftModel();
    const ns = "http://www.w3.org/2000/svg";
    const listeners = [], counts = {}, events = [];
    let mode = "INTERACT", gesture = null, destroyed2 = false, sequence = 0, editable = true;
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
      if (destroyed2) throw new Error("Draft Canvas has been destroyed");
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
      object4.style.left = `${c.x}px`;
      object4.style.top = `${c.y}px`;
      object4.style.width = `${c.width}px`;
      object4.style.minWidth = `${c.width}px`;
      object4.style.maxWidth = `${c.width}px`;
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
      if (next !== "INTERACT" && object4.contains(document.activeElement)) document.activeElement.blur();
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
      const card2 = model.getDraft().questionCard;
      const zoom = Math.min(1, Math.max(0.1, (viewport2.clientWidth - 40) / card2.width));
      model.setZoom(zoom);
      const view = model.getDraft().viewport;
      model.pan(
        (viewport2.clientWidth - card2.width * zoom) / 2 - (card2.x + view.x) * zoom,
        20 - (card2.y + view.y) * zoom
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
      focusElement(node) {
        alive();
        if (!root.contains(node)) throw new Error("Focus target outside question card");
        finish(true);
        const target = node.getBoundingClientRect(), bounds = viewport2.getBoundingClientRect();
        model.pan(bounds.left + 24 - target.left, bounds.top + 24 - target.top);
        render();
        changed();
      },
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
        return { supportedPointerEvents: typeof PointerEvent !== "undefined", counts: { ...counts }, events: events.map((e) => ({ ...e })), accessMode, mode, destroyed: destroyed2 };
      },
      destroy() {
        if (destroyed2) return;
        finish(false);
        destroyed2 = true;
        changeListeners.clear();
        listeners.splice(0).forEach((remove) => remove());
        if (root.contains(document.activeElement)) document.activeElement.blur();
        root.inert = true;
        root.style.pointerEvents = "none";
        root.querySelectorAll("input, button, textarea").forEach((element2) => {
          element2.disabled = true;
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
      for (const id2 of ["undo", "redo", "clear", "export", "import", "load-json"]) {
        $(`#${id2}`).hidden = true;
        $(`#${id2}`).disabled = true;
      }
      setMode("PAN");
    }
    if (typeof PointerEvent === "undefined") $("#mode-help").textContent = "\u5F53\u524D\u8FD0\u884C\u73AF\u5883\u7F3A\u5C11 Pointer Events\u3002";
    return Object.freeze({ ...api, addDisposer(dispose) {
      alive();
      listeners.push(dispose);
    } });
  }

  // src/renderer/choice/contract.js
  var feedback = /* @__PURE__ */ new Set(["NONE", "CORRECT", "INCORRECT"]);
  function textContent(value, name) {
    if ((value == null ? void 0 : value.kind) !== "TEXT") throw new TypeError(`Unsupported content: ${name} supports TEXT only in v1`);
    if (typeof value.text !== "string") throw new TypeError(`${name}.text must be text`);
    return { kind: "TEXT", text: value.text };
  }
  function optionIds(value, available, name) {
    if (!Array.isArray(value) || new Set(value).size !== value.length || value.some((v) => !available.has(v)))
      throw new TypeError(`${name} must contain unique known option ids`);
    return [...value];
  }
  function readChoiceQuestion(q, definition) {
    if (q.selectionMode !== void 0 && q.selectionMode !== definition.selectionMode)
      throw new TypeError("Choice selectionMode does not match question type");
    if (!Array.isArray(q.options) || q.options.length < 2) throw new TypeError("Question options are missing");
    const options = q.options.map((o) => {
      if (typeof o.id !== "string" || !o.id.trim()) throw new TypeError("option.id is required");
      if (!feedback.has(o.feedback)) throw new TypeError("Unknown authoritative option feedback");
      return { id: o.id, content: textContent(o.content, "option.content"), feedback: o.feedback };
    });
    const available = new Set(options.map((o) => o.id));
    if (available.size !== options.length) throw new TypeError("Option ids must be unique");
    const selected = optionIds(q.selectedOptionIds, available, "selectedOptionIds");
    if (definition.selectionMode === "SINGLE" && selected.length > 1)
      throw new TypeError("SINGLE_CHOICE cannot display multiple selected options");
    return {
      prompt: textContent(q.prompt, "prompt"),
      options,
      selectedOptionIds: selected,
      selectionMode: definition.selectionMode,
      available
    };
  }

  // src/shared/renderer/contract.js
  var RendererMode = Object.freeze({ ACTIVE: "ACTIVE", READ_ONLY_HISTORY: "READ_ONLY_HISTORY" });
  function requireRendererMode(mode) {
    if (!Object.values(RendererMode).includes(mode)) throw new TypeError("Unsupported renderer capability mode");
    return mode;
  }
  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== void 0) node.textContent = text;
    return node;
  }

  // src/renderer/choice/renderer.js
  function mountChoiceRenderer(form, initial, { mode, definition, contentRoot, canInteract, answerChanged }) {
    requireRendererMode(mode);
    const history = mode === RendererMode.READ_ONLY_HISTORY;
    let question = initial, readOnly = history, interaction = "INTERACT", destroyed2 = false;
    const prompt = element("p", "practice-prompt", question.prompt.text);
    prompt.id = "practice-prompt";
    const options = element("fieldset", "practice-options");
    options.appendChild(element("legend", "", "\u9009\u62E9\u7B54\u6848"));
    question.options.forEach((option, index) => {
      const label = element("label", `practice-option feedback-${option.feedback.toLowerCase()}`);
      label.dataset.optionId = option.id;
      const input = element("input");
      input.type = definition.selectionMode === "MULTIPLE" ? "checkbox" : "radio";
      input.name = "practice-answer";
      input.value = option.id;
      input.id = `practice-option-${index}`;
      label.append(input, element("span", "practice-option-text", `${String.fromCharCode(65 + index)}. ${option.content.text}`));
      options.appendChild(label);
    });
    contentRoot.appendChild(prompt);
    form.appendChild(options);
    function alive() {
      if (destroyed2) throw new Error("Question renderer destroyed");
    }
    function synchronize() {
      options.querySelectorAll("input").forEach((input) => {
        input.checked = question.selectedOptionIds.includes(input.value);
        input.disabled = readOnly || interaction !== "INTERACT" || question.state === "SUBMITTED";
        input.closest(".practice-option").classList.toggle("selected", input.checked);
      });
    }
    function getAnswerIntent() {
      alive();
      return { selectedOptionIds: Array.from(options.querySelectorAll("input:checked"), (input) => input.value) };
    }
    const change = (event) => {
      if (!event.target.matches("input[name=practice-answer]")) return;
      if (destroyed2 || readOnly || interaction !== "INTERACT" || question.state === "SUBMITTED" || !canInteract()) {
        synchronize();
        return;
      }
      Promise.resolve(answerChanged(getAnswerIntent())).catch(() => {
      });
    };
    if (!history) form.addEventListener("change", change);
    synchronize();
    return Object.freeze({
      update(next) {
        alive();
        question = next;
        synchronize();
      },
      getAnswerIntent,
      hasAnswer() {
        alive();
        return question.selectedOptionIds.length > 0;
      },
      focusTarget() {
        alive();
        return null;
      },
      setInteractionMode(next) {
        alive();
        if (!["INTERACT", "DISABLED"].includes(next)) throw new TypeError("Unknown interaction mode");
        interaction = next;
        synchronize();
      },
      setReadOnly(value) {
        alive();
        if (history && !value) throw new Error("History capability cannot be upgraded");
        readOnly = Boolean(value);
        synchronize();
      },
      renderResult() {
        alive();
        const result = element("section", "practice-result");
        result.id = "practice-result";
        result.hidden = !question.result;
        const r = question.result;
        if (r) {
          result.append(
            element("p", `practice-result-${r.status.toLowerCase()}`, r.status === "CORRECT" ? "\u56DE\u7B54\u6B63\u786E" : "\u56DE\u7B54\u9519\u8BEF"),
            element("p", "practice-earned-score", `\u5F97\u5206\uFF1A${r.score} / ${r.maxScore}`)
          );
          const labels = r.correctOptionIds.map((id2) => String.fromCharCode(65 + question.options.findIndex((o) => o.id === id2))).join("\u3001");
          result.appendChild(element("p", "practice-correct-answer", `\u6B63\u786E\u7B54\u6848\uFF1A${labels}`));
          if (r.analysis.text) result.append(element("strong", "", "\u7B54\u6848\u4E0E\u89E3\u6790"), element("p", "practice-analysis", r.analysis.text));
        }
        return result;
      },
      destroy() {
        if (destroyed2) return;
        destroyed2 = true;
        form.removeEventListener("change", change);
        options.querySelectorAll("input").forEach((i) => i.disabled = true);
      }
    });
  }

  // src/renderer/single-choice/index.js
  var singleChoiceRenderer = Object.freeze({
    id: "builtin.single-choice.v1",
    questionType: "SINGLE_CHOICE",
    selectionMode: "SINGLE",
    label: "\u5355\u9009\u9898",
    parse(question) {
      return readChoiceQuestion(question, this);
    },
    mount(form, question, capabilities) {
      return mountChoiceRenderer(form, question, { ...capabilities, definition: this });
    }
  });

  // src/renderer/multiple-choice/index.js
  var multipleChoiceRenderer = Object.freeze({
    id: "builtin.multiple-choice.v1",
    questionType: "MULTIPLE_CHOICE",
    selectionMode: "MULTIPLE",
    label: "\u591A\u9009\u9898",
    parse(question) {
      return readChoiceQuestion(question, this);
    },
    mount(form, question, capabilities) {
      return mountChoiceRenderer(form, question, { ...capabilities, definition: this });
    }
  });

  // src/shared/renderer/result.js
  function resultSection(r) {
    const section = element("section", "practice-result");
    section.id = "practice-result";
    section.hidden = !r;
    if (r) {
      section.append(element("p", `practice-result-${r.status.toLowerCase()}`, r.status === "UNSCORED" ? "\u5DF2\u63D0\u4EA4 \xB7 \u672A\u8BC4\u5206" : r.status === "CORRECT" ? "\u56DE\u7B54\u6B63\u786E" : "\u56DE\u7B54\u9519\u8BEF"));
      if (r.score != null) section.append(element("p", "practice-earned-score", `\u5F97\u5206\uFF1A${r.score} / ${r.maxScore}`));
      if (r.analysis.text) section.append(element("strong", "", "\u7B54\u6848\u4E0E\u89E3\u6790"), element("p", "practice-analysis", r.analysis.text));
    }
    return section;
  }

  // src/renderer/composite-selection/renderer.js
  function mountCompositeSelection(form, initial, { mode, contentRoot, canInteract, answerChanged, renderPrompt: renderPrompt2 }) {
    requireRendererMode(mode);
    const history = mode === RendererMode.READ_ONLY_HISTORY;
    let question = initial, readOnly = history, interaction = "INTERACT", destroyed2 = false;
    const prompt = element("p", "practice-prompt");
    if (renderPrompt2) renderPrompt2(prompt, question);
    else prompt.textContent = question.prompt.text;
    prompt.id = "practice-prompt";
    contentRoot.append(prompt);
    const groups = element("div", "composite-items");
    question.presentation.items.forEach((item, n) => {
      const section = element("section", "composite-item");
      section.dataset.targetId = item.id;
      section.tabIndex = -1;
      section.append(element("h3", "", `${item.number}. ${item.prompt.text}`));
      const field = element("fieldset", "practice-options");
      field.append(element("legend", "", `\u7B2C ${item.number} \u9898`));
      item.options.forEach((option, i) => {
        const label = element("label", `practice-option feedback-${option.feedback.toLowerCase()}`);
        label.dataset.optionId = option.id;
        const input = element("input");
        input.type = "radio";
        input.name = `practice-child-${n}`;
        input.value = option.id;
        input.id = `practice-child-${n}-${i}`;
        label.append(input, element("span", "practice-option-text", `${String.fromCharCode(65 + i)}. ${option.content.text}`));
        field.append(label);
      });
      section.append(field);
      groups.append(section);
    });
    form.append(groups);
    function alive() {
      if (destroyed2) throw new Error("Question renderer destroyed");
    }
    function sync() {
      groups.querySelectorAll("input").forEach((input) => {
        input.checked = question.selectedOptionIds.includes(input.value);
        input.disabled = readOnly || interaction !== "INTERACT" || question.state === "SUBMITTED";
        input.closest("label").classList.toggle("selected", input.checked);
      });
      prompt.querySelectorAll("select[data-child-id]").forEach((input) => {
        var _a;
        const item = question.presentation.items.find((i) => i.id === input.dataset.childId);
        input.value = ((_a = item.options.find((o) => question.selectedOptionIds.includes(o.id))) == null ? void 0 : _a.id) || "";
        input.disabled = readOnly || interaction !== "INTERACT" || question.state === "SUBMITTED";
      });
    }
    function getAnswerIntent() {
      alive();
      return { selectedOptionIds: Array.from(groups.querySelectorAll("input:checked"), (i) => i.value) };
    }
    const change = (event) => {
      if (!event.target.matches("input[type=radio],select[data-child-id]")) return;
      if (destroyed2 || readOnly || interaction !== "INTERACT" || question.state === "SUBMITTED" || !canInteract()) {
        sync();
        return;
      }
      if (event.target.matches("select[data-child-id]")) {
        const item = question.presentation.items.find((i) => i.id === event.target.dataset.childId);
        groups.querySelectorAll("input").forEach((input) => {
          if (item.options.some((o) => o.id === input.value)) input.checked = input.value === event.target.value;
        });
      }
      Promise.resolve(answerChanged(getAnswerIntent())).catch(() => {
      });
    };
    if (!history) {
      form.addEventListener("change", change);
      prompt.addEventListener("change", change);
    }
    sync();
    return Object.freeze({
      update(next) {
        alive();
        question = next;
        sync();
      },
      getAnswerIntent,
      hasAnswer() {
        alive();
        return question.selectedOptionIds.length > 0;
      },
      focusTarget(id2) {
        alive();
        const node = Array.from(groups.children).find((n) => n.dataset.targetId === id2);
        if (!node) return null;
        node.focus({ preventScroll: true });
        return node;
      },
      setInteractionMode(next) {
        alive();
        if (!["INTERACT", "DISABLED"].includes(next)) throw new TypeError("Unknown interaction mode");
        interaction = next;
        sync();
      },
      setReadOnly(value) {
        alive();
        if (history && !value) throw new Error("History capability cannot be upgraded");
        readOnly = Boolean(value);
        sync();
      },
      renderResult() {
        alive();
        return resultSection(question.result);
      },
      destroy() {
        if (destroyed2) return;
        destroyed2 = true;
        form.removeEventListener("change", change);
        prompt.removeEventListener("change", change);
        prompt.querySelectorAll("select").forEach((i) => i.disabled = true);
        groups.querySelectorAll("input").forEach((i) => i.disabled = true);
      }
    });
  }

  // src/renderer/reading/index.js
  var readingRenderer = Object.freeze({
    id: "builtin.reading.v1",
    questionType: "READING",
    selectionMode: "COMPOSITE_SINGLE",
    label: "\u9605\u8BFB\u7406\u89E3",
    parse(q) {
      var _a;
      if (!Array.isArray((_a = q.presentation) == null ? void 0 : _a.items) || !q.presentation.items.length) throw new TypeError("Reading items are missing");
      const items = q.presentation.items.map((item) => {
        if (typeof item.id !== "string" || !item.id.trim() || !Number.isInteger(item.number) || item.number < 1) throw new TypeError("Invalid reading item identity");
        const child = readChoiceQuestion({
          ...q,
          selectionMode: "SINGLE",
          prompt: item.prompt,
          options: item.options,
          selectedOptionIds: q.selectedOptionIds.filter((id2) => item.options.some((o) => o.id === id2))
        }, { selectionMode: "SINGLE" });
        return { id: item.id, number: item.number, prompt: child.prompt, options: child.options };
      });
      if (new Set(items.map((i) => i.id)).size !== items.length) throw new TypeError("Reading child IDs must be unique");
      const all = readChoiceQuestion({ ...q, selectionMode: "MULTIPLE", options: items.flatMap((i) => i.options) }, { selectionMode: "MULTIPLE" });
      return { ...all, selectionMode: "COMPOSITE_SINGLE", presentation: { items } };
    },
    mount: mountCompositeSelection
  });

  // src/renderer/cloze/index.js
  function renderPrompt(prompt, q, select) {
    const marker = /(\\)?\{\{([1-9][0-9]*)}}/g;
    let end = 0;
    for (const match of q.prompt.text.matchAll(marker)) {
      prompt.append(document.createTextNode(q.prompt.text.slice(end, match.index)));
      end = match.index + match[0].length;
      if (match[1]) {
        prompt.append(document.createTextNode(match[0].slice(1)));
        continue;
      }
      const item = q.presentation.items.find((i) => i.number === Number(match[2]));
      if (!item) throw new TypeError("Unknown inline blank");
      const input = element("select", "inline-blank");
      input.dataset.childId = item.id;
      input.setAttribute("aria-label", `\u7B2C ${item.number} \u7A7A`);
      input.append(new Option(`${item.number}._______`, ""));
      item.options.forEach((o) => input.append(new Option(`${item.number}.${o.content.text}`, o.id)));
      prompt.append(input);
    }
    prompt.append(document.createTextNode(q.prompt.text.slice(end)));
  }
  var clozeRenderer = Object.freeze({
    id: "builtin.cloze.v1",
    questionType: "CLOZE",
    selectionMode: "COMPOSITE_SINGLE",
    label: "\u5B8C\u5F62\u586B\u7A7A",
    parse(q) {
      const parsed = readingRenderer.parse(q);
      return { ...parsed, selectionMode: "COMPOSITE_SINGLE" };
    },
    mount(form, q, context) {
      return mountCompositeSelection(form, q, { ...context, renderPrompt });
    }
  });

  // src/renderer/matching/index.js
  var matchingRenderer = Object.freeze({
    id: "builtin.matching.v1",
    questionType: "MATCHING",
    selectionMode: "ASSIGNMENT",
    label: "\u6BB5\u843D\u6392\u5E8F",
    parse(q) {
      const all = readChoiceQuestion({ ...q, selectionMode: "MULTIPLE", selectedOptionIds: [] }, { selectionMode: "MULTIPLE" });
      const p = q.presentation;
      if (!Array.isArray(p == null ? void 0 : p.slots) || !p.slots.length || typeof p.assignments !== "object" || !p.assignments) throw new TypeError("Missing matching presentation");
      const ids = /* @__PURE__ */ new Set();
      const slots = p.slots.map((s) => {
        if (typeof s.id !== "string" || !s.id.trim() || ids.has(s.id) || !Number.isInteger(s.number) || typeof s.locked !== "boolean") throw new TypeError("Invalid assignment slot");
        ids.add(s.id);
        for (const id2 of [s.givenOptionId, s.selectedOptionId, s.correctOptionId]) if (id2 != null && !all.available.has(id2)) throw new TypeError("Unknown assignment option");
        if (!["NONE", "CORRECT", "INCORRECT"].includes(s.feedback) || q.state !== "SUBMITTED" && s.feedback !== "NONE") throw new TypeError("Invalid slot feedback");
        if (q.state !== "SUBMITTED" && s.correctOptionId != null) throw new TypeError("Unsubmitted correct assignment");
        return { ...s };
      });
      const assignments = { ...p.assignments };
      for (const [id2, option] of Object.entries(assignments)) if (!ids.has(id2) || !all.available.has(option)) throw new TypeError("Unknown assignment");
      return { ...all, selectionMode: "ASSIGNMENT", presentation: { slots, assignments } };
    },
    mount(form, initial, { mode, contentRoot, canInteract, answerChanged }) {
      requireRendererMode(mode);
      const history = mode === RendererMode.READ_ONLY_HISTORY;
      let q = initial, readOnly = history, interaction = "INTERACT", destroyed2 = false;
      const prompt = element("p", "practice-prompt", q.prompt.text);
      prompt.id = "practice-prompt";
      contentRoot.append(prompt);
      const area = element("div", "assignment-slots");
      const reserved = new Set(q.presentation.slots.filter((s) => s.locked).map((s) => s.givenOptionId));
      q.presentation.slots.forEach((slot, n) => {
        const label = element("label", `assignment-slot feedback-${slot.feedback.toLowerCase()}`);
        label.dataset.targetId = slot.id;
        label.tabIndex = -1;
        label.append(element("span", "", `${slot.number}.`));
        const select = element("select");
        select.dataset.slotId = slot.id;
        select.id = `assignment-slot-${n}`;
        select.setAttribute("aria-label", `\u7B2C ${slot.number} \u4E2A\u4F4D\u7F6E`);
        select.append(new Option("\u2014", ""));
        q.options.forEach((o) => {
          const option = new Option(o.content.text, o.id);
          option.disabled = reserved.has(o.id);
          select.append(option);
        });
        label.append(select);
        area.append(label);
      });
      form.append(area);
      function alive() {
        if (destroyed2) throw new Error("Question renderer destroyed");
      }
      function sync() {
        area.querySelectorAll("select").forEach((input) => {
          const slot = q.presentation.slots.find((s) => s.id === input.dataset.slotId);
          input.value = slot.locked ? slot.givenOptionId : q.presentation.assignments[slot.id] || "";
          input.disabled = slot.locked || readOnly || interaction !== "INTERACT" || q.state === "SUBMITTED";
        });
      }
      function getAnswerIntent() {
        alive();
        const assignments = {};
        area.querySelectorAll("select").forEach((i) => {
          if (!q.presentation.slots.find((s) => s.id === i.dataset.slotId).locked && i.value) assignments[i.dataset.slotId] = i.value;
        });
        return { assignments };
      }
      const change = (event) => {
        if (!event.target.matches("select")) return;
        if (destroyed2 || readOnly || interaction !== "INTERACT" || q.state === "SUBMITTED" || !canInteract()) {
          sync();
          return;
        }
        Promise.resolve(answerChanged(getAnswerIntent())).catch(() => {
        });
      };
      if (!history) form.addEventListener("change", change);
      sync();
      return Object.freeze({
        update(next) {
          alive();
          q = next;
          sync();
        },
        getAnswerIntent,
        hasAnswer() {
          alive();
          return Object.keys(q.presentation.assignments).length > 0;
        },
        focusTarget(id2) {
          alive();
          const node = Array.from(area.children).find((n) => n.dataset.targetId === id2);
          if (node) node.focus({ preventScroll: true });
          return node || null;
        },
        setInteractionMode(next) {
          alive();
          if (!["INTERACT", "DISABLED"].includes(next)) throw new TypeError("Unknown interaction mode");
          interaction = next;
          sync();
        },
        setReadOnly(value) {
          alive();
          if (history && !value) throw new Error("History capability cannot be upgraded");
          readOnly = Boolean(value);
          sync();
        },
        renderResult() {
          alive();
          return resultSection(q.result);
        },
        destroy() {
          if (destroyed2) return;
          destroyed2 = true;
          form.removeEventListener("change", change);
          area.querySelectorAll("select").forEach((i) => i.disabled = true);
        }
      });
    }
  });

  // src/renderer/text-answer/renderer.js
  function mountTextAnswers(form, initial, { mode, contentRoot, canInteract, answerChanged, answerEdited, fields: providedFields, plainPrompt = false, answerPlaceholder = "\u5728\u8FD9\u91CC\u8F93\u5165\u8BD1\u6587", intent }) {
    requireRendererMode(mode);
    const history = mode === RendererMode.READ_ONLY_HISTORY;
    let q = initial, readOnly = history, interaction = "INTERACT", destroyed2 = false, timer = null, dirty = false, saving = false, inFlight = null;
    const fields = providedFields || q.presentation.items;
    const prompt = element("p", "practice-prompt");
    prompt.id = "practice-prompt";
    const marker = /(\\)?\{\{([\s\S]*?)}}/g;
    let end = 0, ordinal = 0;
    for (const m of plainPrompt ? [] : q.prompt.text.matchAll(marker)) {
      prompt.append(document.createTextNode(q.prompt.text.slice(end, m.index)));
      end = m.index + m[0].length;
      if (m[1]) {
        prompt.append(document.createTextNode(m[0].slice(1)));
        continue;
      }
      const item = fields[ordinal++];
      if (!item) throw new TypeError("Translation target missing");
      const sentence = element("u", "translation-sentence", `${item.number}. ${m[2]}`);
      sentence.dataset.targetId = item.id;
      prompt.append(sentence);
    }
    prompt.append(document.createTextNode(q.prompt.text.slice(end)));
    contentRoot.append(prompt);
    const area = element("div", "text-answer-items");
    fields.forEach((item, n) => {
      var _a;
      const section = element("section", "text-answer-item");
      section.dataset.targetId = item.id;
      section.tabIndex = -1;
      const label = element("label", "", `${item.number}. ${item.text}`);
      const input2 = element("textarea");
      input2.id = `text-answer-${n}`;
      input2.dataset.itemId = item.id;
      input2.rows = 5;
      input2.value = item.answer.text;
      input2.placeholder = answerPlaceholder;
      label.htmlFor = input2.id;
      section.append(label, input2);
      if (((_a = item.reference) == null ? void 0 : _a.text) && q.state === "SUBMITTED") section.append(element("p", "practice-reference", `\u53C2\u8003\u7B54\u6848\uFF1A${item.reference.text}`));
      area.append(section);
    });
    form.append(area);
    function alive() {
      if (destroyed2) throw new Error("Question renderer destroyed");
    }
    function sync() {
      area.querySelectorAll("textarea").forEach((input2) => {
        var _a, _b;
        if (!dirty && !saving) input2.value = (((_b = (_a = q.presentation.items) == null ? void 0 : _a.find((i) => i.id === input2.dataset.itemId)) == null ? void 0 : _b.answer) || q.presentation.answer).text;
        input2.disabled = readOnly || interaction !== "INTERACT" || q.state === "SUBMITTED";
      });
    }
    function getAnswerIntent() {
      alive();
      if (intent) return intent(Array.from(area.querySelectorAll("textarea")));
      const textAnswers = {};
      area.querySelectorAll("textarea").forEach((i) => {
        if (i.value.trim()) textAnswers[i.dataset.itemId] = i.value;
      });
      return { textAnswers };
    }
    function flushAnswer() {
      alive();
      clearTimeout(timer);
      timer = null;
      if (saving) return inFlight;
      if (!dirty) return Promise.resolve();
      const intent2 = getAnswerIntent();
      dirty = false;
      saving = true;
      inFlight = Promise.resolve(answerChanged(intent2)).finally(() => {
        saving = false;
        inFlight = null;
      });
      return inFlight;
    }
    const input = (event) => {
      if (!event.target.matches("textarea")) return;
      if (destroyed2 || readOnly || interaction !== "INTERACT" || q.state === "SUBMITTED" || !canInteract()) {
        sync();
        return;
      }
      dirty = true;
      answerEdited == null ? void 0 : answerEdited();
      clearTimeout(timer);
      timer = setTimeout(() => {
        flushAnswer().catch(() => {
        });
      }, 300);
    };
    if (!history) form.addEventListener("input", input);
    sync();
    return Object.freeze({
      update(next) {
        alive();
        q = next;
        sync();
      },
      getAnswerIntent,
      flushAnswer,
      pendingAnswerIntent() {
        alive();
        return dirty ? getAnswerIntent() : null;
      },
      rejectAnswer() {
        alive();
        dirty = false;
        sync();
      },
      hasAnswer() {
        alive();
        return Array.from(area.querySelectorAll("textarea")).some((i) => i.value.trim());
      },
      focusTarget(id2) {
        alive();
        const node = Array.from(area.children).find((n) => n.dataset.targetId === id2);
        if (node) node.focus({ preventScroll: true });
        return node || null;
      },
      setInteractionMode(next) {
        alive();
        if (!["INTERACT", "DISABLED"].includes(next)) throw new TypeError("Unknown interaction mode");
        interaction = next;
        sync();
      },
      setReadOnly(value) {
        alive();
        if (history && !value) throw new Error("History capability cannot be upgraded");
        readOnly = Boolean(value);
        sync();
      },
      renderResult() {
        alive();
        return resultSection(q.result);
      },
      destroy() {
        if (destroyed2) return;
        destroyed2 = true;
        clearTimeout(timer);
        form.removeEventListener("input", input);
        area.querySelectorAll("textarea").forEach((i) => i.disabled = true);
      }
    });
  }

  // src/renderer/translation/index.js
  var translationRenderer = Object.freeze({
    id: "builtin.translation.v1",
    questionType: "TRANSLATION",
    selectionMode: "TEXT_FIELDS",
    label: "\u7FFB\u8BD1",
    parse(q) {
      var _a;
      if (!Array.isArray((_a = q.presentation) == null ? void 0 : _a.items) || !q.presentation.items.length) throw new TypeError("Missing translation items");
      const ids = /* @__PURE__ */ new Set();
      const items = q.presentation.items.map((i) => {
        if (typeof i.id !== "string" || !i.id.trim() || ids.has(i.id) || !Number.isInteger(i.number) || typeof i.text !== "string") throw new TypeError("Invalid translation identity");
        ids.add(i.id);
        if (q.state !== "SUBMITTED" && i.reference != null) throw new TypeError("Unsubmitted reference answer");
        return { ...i, answer: textContent(i.answer, "text answer"), reference: i.reference == null ? null : textContent(i.reference, "reference") };
      });
      return { prompt: textContent(q.prompt, "prompt"), options: [], available: /* @__PURE__ */ new Set(), selectedOptionIds: [], presentation: { items }, selectionMode: "TEXT_FIELDS" };
    },
    mount: mountTextAnswers
  });

  // src/renderer/essay/index.js
  var essayRenderer = Object.freeze({
    id: "builtin.essay.v1",
    questionType: "ESSAY",
    selectionMode: "LONG_TEXT",
    label: "\u4F5C\u6587\u9898",
    parse(q) {
      var _a, _b, _c;
      if (q.state !== "SUBMITTED" && ((_a = q.presentation) == null ? void 0 : _a.reference) != null) throw new TypeError("Unsubmitted reference answer");
      return {
        prompt: textContent(q.prompt, "prompt"),
        options: [],
        available: /* @__PURE__ */ new Set(),
        selectedOptionIds: [],
        selectionMode: "LONG_TEXT",
        presentation: { answer: textContent((_b = q.presentation) == null ? void 0 : _b.answer, "essay answer"), reference: ((_c = q.presentation) == null ? void 0 : _c.reference) == null ? null : textContent(q.presentation.reference, "reference") }
      };
    },
    mount(form, q, context) {
      return mountTextAnswers(form, q, {
        ...context,
        fields: [{ id: q.questionId, number: 1, text: "\u6B63\u5F0F\u7B54\u6848", answer: q.presentation.answer, reference: q.presentation.reference }],
        plainPrompt: true,
        answerPlaceholder: "\u5728\u8FD9\u91CC\u8F93\u5165\u4F5C\u6587",
        intent: (inputs) => ({ essayText: inputs[0].value })
      });
    }
  });

  // src/shared/renderer/registry.js
  var builtins = new Map([singleChoiceRenderer, multipleChoiceRenderer, readingRenderer, clozeRenderer, matchingRenderer, translationRenderer, essayRenderer].map((renderer) => [renderer.questionType, renderer]));
  var QuestionRendererRegistry = Object.freeze({
    types: Object.freeze([...builtins.keys()]),
    require(type) {
      const renderer = builtins.get(type);
      if (!renderer) throw new TypeError(`Unsupported question type: ${type}`);
      return renderer;
    }
  });

  // src/practice/contract.js
  var states = /* @__PURE__ */ new Set(["UNANSWERED", "DRAFT", "SUBMITTED", "RETRYING", "REVISING"]);
  function string(value, name) {
    if (typeof value !== "string") throw new TypeError(`${name} must be text`);
    return value;
  }
  function id(value, name) {
    if (!string(value, name).trim()) throw new TypeError(`${name} is required`);
    return value;
  }
  function number(value, name) {
    if (typeof value !== "number" || !Number.isFinite(value)) throw new TypeError(`${name} must be finite`);
    return value;
  }
  function readPractice(value) {
    const vm = typeof value === "string" ? JSON.parse(value) : value;
    if ((vm == null ? void 0 : vm.schemaVersion) !== "1.0") throw new TypeError("Unsupported Shared Practice schemaVersion");
    const s = vm.session, q = vm.question;
    const definition = QuestionRendererRegistry.require(q == null ? void 0 : q.type);
    if (!states.has(q.state)) throw new TypeError("Unknown authoritative Practice state");
    if (!Number.isInteger(q.index) || !Number.isInteger(q.total) || q.index < 0 || q.index >= q.total)
      throw new TypeError("Question position is invalid");
    const choice = definition.parse(q);
    const { options, available, selectedOptionIds: selected } = choice;
    const maximum = q.maxScore == null && ["TEXT_FIELDS", "LONG_TEXT"].includes(definition.selectionMode) ? null : number(q.maxScore, "question.maxScore");
    let result = null;
    if (q.state === "SUBMITTED") {
      const r = q.result;
      if (!r || !["CORRECT", "INCORRECT", "UNSCORED"].includes(r.status) || !["INITIAL", "RETRY", "REVISION"].includes(r.attemptMode))
        throw new TypeError("Submitted question requires an authoritative result");
      if (!Number.isInteger(r.attemptNo) || r.attemptNo < 1) throw new TypeError("Invalid attemptNo");
      result = {
        status: r.status,
        score: r.status === "UNSCORED" && r.score == null ? null : number(r.score, "result.score"),
        maxScore: r.maxScore == null && r.status === "UNSCORED" ? null : number(r.maxScore, "result.maxScore"),
        attemptId: id(r.attemptId, "result.attemptId"),
        attemptNo: r.attemptNo,
        attemptMode: r.attemptMode,
        correctOptionIds: optionIds(r.correctOptionIds, available, "correctOptionIds"),
        analysis: textContent(r.analysis, "result.analysis")
      };
    } else if (q.result != null || options.some((o) => o.feedback !== "NONE")) {
      throw new TypeError("Unsubmitted question must not expose result feedback");
    }
    return {
      schemaVersion: "1.0",
      session: { sessionId: id(s == null ? void 0 : s.sessionId, "sessionId"), bankAssetId: id(s == null ? void 0 : s.bankAssetId, "bankAssetId"), bankContentId: id(s == null ? void 0 : s.bankContentId, "bankContentId") },
      question: {
        sessionQuestionId: id(q.sessionQuestionId, "sessionQuestionId"),
        questionId: id(q.questionId, "questionId"),
        type: definition.questionType,
        selectionMode: definition.selectionMode,
        ...choice.presentation ? { presentation: choice.presentation } : {},
        index: q.index,
        total: q.total,
        prompt: textContent(q.prompt, "prompt"),
        options,
        selectedOptionIds: selected,
        state: q.state,
        maxScore: maximum,
        result
      }
    };
  }

  // src/bridge/ordering.js
  function operationSeq(value) {
    if (!Number.isSafeInteger(value) || value < 1) throw new TypeError("operationSeq must be a positive safe integer");
    return value;
  }
  var _issued, _applied, _authoritative, _pending;
  var OperationOrdering = class {
    constructor() {
      __privateAdd(this, _issued, 0);
      __privateAdd(this, _applied, 0);
      __privateAdd(this, _authoritative, 0);
      __privateAdd(this, _pending, null);
    }
    begin() {
      if (__privateGet(this, _pending) !== null) throw new Error("A Practice mutation is already in flight");
      const seq = operationSeq(__privateGet(this, _issued) + 1);
      __privateSet(this, _issued, seq);
      __privateSet(this, _pending, seq);
      return seq;
    }
    canApply(value) {
      const seq = operationSeq(value);
      return seq > __privateGet(this, _applied) && (__privateGet(this, _pending) === null || seq === __privateGet(this, _pending));
    }
    complete(value, success) {
      const seq = operationSeq(value);
      if (!this.canApply(seq)) return false;
      __privateSet(this, _applied, seq);
      __privateSet(this, _issued, Math.max(__privateGet(this, _issued), seq));
      if (success) __privateSet(this, _authoritative, seq);
      __privateSet(this, _pending, null);
      return true;
    }
    getState() {
      return {
        lastIssuedSeq: __privateGet(this, _issued),
        lastAppliedSeq: __privateGet(this, _applied),
        lastAuthoritativeSeq: __privateGet(this, _authoritative),
        inFlightSeq: __privateGet(this, _pending)
      };
    }
  };
  _issued = new WeakMap();
  _applied = new WeakMap();
  _authoritative = new WeakMap();
  _pending = new WeakMap();

  // src/shared/runtime/question-runtime.js
  function mountSharedQuestionRuntime(root, sendIntent, interactionAllowed = () => true, hooks = {}) {
    const readOnly = hooks.readOnly === true;
    const mode = readOnly ? RendererMode.READ_ONLY_HISTORY : RendererMode.ACTIVE;
    let renderer = null;
    let authoritative = null, busy = false, destroyed2 = false, confirmation = false, error = "";
    const ordering = new OperationOrdering();
    let pendingReply = null;
    const idleWaiters = [];
    function settled(failure) {
      const reply = pendingReply;
      pendingReply = null;
      if (reply) failure ? reply.reject(failure) : reply.resolve();
      idleWaiters.splice(0).forEach((resolve) => resolve());
    }
    function idle() {
      return busy ? new Promise((resolve) => idleWaiters.push(resolve)) : Promise.resolve();
    }
    function alive() {
      if (destroyed2) throw new Error("Shared Practice UI has been destroyed");
    }
    function controls() {
      var _a, _b;
      if (!authoritative) return;
      renderer == null ? void 0 : renderer.update(authoritative.question);
      renderer == null ? void 0 : renderer.setReadOnly(readOnly);
      renderer == null ? void 0 : renderer.setInteractionMode(busy || authoritative.question.state === "SUBMITTED" || confirmation ? "DISABLED" : "INTERACT");
      const submit2 = root.querySelector("#practice-submit"), retry = root.querySelector("#practice-retry");
      if (submit2) submit2.disabled = busy || !((_b = (_a = renderer == null ? void 0 : renderer.hasAnswer) == null ? void 0 : _a.call(renderer)) != null ? _b : authoritative.question.selectedOptionIds.length);
      if (retry) retry.disabled = busy;
      root.querySelectorAll("#practice-confirmation button").forEach((button) => {
        button.disabled = busy;
      });
      const panel = root.querySelector("#practice-confirmation");
      if (panel) panel.hidden = !confirmation;
      const message = root.querySelector("#practice-error");
      if (message) {
        message.textContent = error;
        message.hidden = !error;
      }
      root.setAttribute("aria-busy", String(busy));
    }
    function render(vm) {
      var _a;
      renderer == null ? void 0 : renderer.destroy();
      const fragment = document.createDocumentFragment(), q = vm.question;
      const definition = QuestionRendererRegistry.require(q.type);
      const heading = element("div", "card-heading");
      heading.append(element("span", "tag", definition.label), element("span", "", `\u7B2C ${q.index + 1} / ${q.total} \u9898`));
      const state = element("span", "practice-state", { UNANSWERED: "\u672A\u4F5C\u7B54", DRAFT: "\u5DF2\u9009\u62E9", SUBMITTED: "\u5DF2\u63D0\u4EA4", RETRYING: "\u91CD\u8BD5\u4E2D", REVISING: "\u4FEE\u8BA2\u4E2D" }[q.state]);
      state.id = "practice-state";
      state.dataset.state = q.state;
      const meta = element("div", "practice-meta");
      meta.append(element("span", "practice-score", `\u5206\u503C\uFF1A${(_a = q.maxScore) != null ? _a : "\u672A\u8BBE\u7F6E"}`), state);
      fragment.append(heading, meta);
      const form = element("form");
      form.id = "practice-form";
      renderer = definition.mount(form, q, {
        mode,
        contentRoot: fragment,
        canInteract: () => !busy && !confirmation && interactionAllowed(),
        answerChanged: (intent) => emit("ANSWER_CHANGED", intent),
        answerEdited: () => {
          const button = root.querySelector("#practice-submit");
          if (button) button.disabled = busy || !renderer.hasAnswer();
        }
      });
      if (!readOnly) {
        const actions = element("div", "practice-actions");
        const button = element("button", q.state === "SUBMITTED" ? "practice-retry" : "practice-submit", q.state === "SUBMITTED" ? "\u21BB  \u91CD\u8BD5" : "\u2713  \u63D0\u4EA4\u7B54\u6848");
        button.id = q.state === "SUBMITTED" ? "practice-retry" : "practice-submit";
        button.type = q.state === "SUBMITTED" ? "button" : "submit";
        actions.appendChild(button);
        form.appendChild(actions);
        const confirm = element("section", "practice-confirmation");
        confirm.id = "practice-confirmation";
        confirm.hidden = true;
        confirm.appendChild(element("p", "", "\u786E\u8BA4\u63D0\u4EA4\u8FD9\u9053\u9898\u7684\u7B54\u6848\u5417\uFF1F\u63D0\u4EA4\u540E\u9700\u91CD\u8BD5\u624D\u80FD\u91CD\u65B0\u4F5C\u7B54\u3002"));
        const confirmActions = element("div", "practice-actions");
        for (const [id2, text, cls] of [["practice-cancel-submit", "\u7EE7\u7EED\u4F5C\u7B54", ""], ["practice-confirm-submit", "\u786E\u8BA4\u63D0\u4EA4", "practice-submit"]]) {
          const b = element("button", cls, text);
          b.id = id2;
          b.type = "button";
          confirmActions.appendChild(b);
        }
        confirm.appendChild(confirmActions);
        form.appendChild(confirm);
      }
      fragment.appendChild(form);
      fragment.appendChild(renderer.renderResult());
      const message = element("p", "practice-error");
      message.id = "practice-error";
      message.setAttribute("role", "alert");
      message.hidden = true;
      fragment.appendChild(message);
      fragment.appendChild(element("p", "practice-stage-note", readOnly ? "\u5386\u53F2\u8349\u7A3F \xB7 \u53EA\u8BFB" : "\u8349\u7A3F\u81EA\u52A8\u4FDD\u5B58\uFF1B\u63D0\u4EA4\u540E\u51BB\u7ED3\uFF0C\u91CD\u8BD5\u4ECE\u7A7A\u767D\u8349\u7A3F\u5F00\u59CB\u3002"));
      root.replaceChildren(fragment);
      root.dataset.practiceState = q.state;
    }
    function renderAuthoritative(next, seq) {
      const focused = root.contains(document.activeElement) ? document.activeElement : null;
      const focus = focused == null ? void 0 : focused.id, selection = (focused == null ? void 0 : focused.selectionStart) == null ? null : [focused.selectionStart, focused.selectionEnd];
      render(next);
      authoritative = next;
      if (seq !== void 0) ordering.complete(seq, true);
      busy = false;
      confirmation = false;
      error = "";
      controls();
      if (focus && interactionAllowed()) {
        const node = document.getElementById(focus);
        node == null ? void 0 : node.focus();
        if (selection && (node == null ? void 0 : node.setSelectionRange)) node.setSelectionRange(...selection);
      }
    }
    function parse(value) {
      try {
        return readPractice(value);
      } catch (failure) {
        const message = element("p", "practice-error", failure.message);
        message.id = "practice-unsupported";
        message.setAttribute("role", "alert");
        renderer == null ? void 0 : renderer.destroy();
        renderer = null;
        root.replaceChildren(message);
        throw failure;
      }
    }
    function loadPractice(value) {
      alive();
      if (authoritative) throw new Error("Practice is already loaded for this page");
      renderAuthoritative(parse(value));
    }
    function refreshPractice(value) {
      alive();
      const next = parse(value);
      if (busy || !authoritative || next.session.sessionId !== authoritative.session.sessionId || next.question.sessionQuestionId !== authoritative.question.sessionQuestionId)
        throw new Error("Refresh requires the same idle practice question");
      renderAuthoritative(next);
    }
    function applyResponse(value) {
      var _a, _b, _c;
      alive();
      if (readOnly) throw new Error("History card has no mutation responses");
      const response = typeof value === "string" ? JSON.parse(value) : value;
      const seq = operationSeq(response == null ? void 0 : response.operationSeq);
      if (!ordering.canApply(seq)) return false;
      if (!["SUCCESS", "ERROR"].includes(response.status)) throw new TypeError("Unknown operation response status");
      const next = readPractice(response.viewModel);
      if (!authoritative || next.session.sessionId !== authoritative.session.sessionId || next.question.sessionQuestionId !== authoritative.question.sessionQuestionId)
        throw new Error("Operation response identity does not match this page");
      if (response.status === "SUCCESS") {
        if (response.error != null) throw new TypeError("Successful response must not contain an error");
        if (response.operationType === "DRAFT_CHANGED") {
          ordering.complete(seq, true);
          busy = false;
          error = "";
          controls();
        } else renderAuthoritative(next, seq);
        (_a = hooks.onResponse) == null ? void 0 : _a.call(hooks, response, next);
        settled();
      } else {
        if (typeof ((_b = response.error) == null ? void 0 : _b.message) !== "string" || !response.error.message.trim())
          throw new TypeError("Failed response requires an error message");
        ordering.complete(seq, false);
        busy = false;
        confirmation = false;
        error = response.error.message;
        (_c = renderer == null ? void 0 : renderer.rejectAnswer) == null ? void 0 : _c.call(renderer);
        controls();
        settled(new Error(response.error.message));
      }
      return true;
    }
    function emit(type, answerIntent, document2) {
      if (readOnly || destroyed2 || busy || !authoritative || type === "RETRY" && !interactionAllowed()) {
        controls();
        return Promise.reject(new Error("Practice mutation unavailable"));
      }
      const seq = ordering.begin();
      const reply = new Promise((resolve, reject) => {
        pendingReply = { resolve, reject };
      });
      busy = true;
      error = "";
      controls();
      const event = {
        type,
        sessionId: authoritative.session.sessionId,
        sessionQuestionId: authoritative.question.sessionQuestionId,
        operationSeq: seq
      };
      if (type === "ANSWER_CHANGED") Object.assign(event, answerIntent);
      if (type === "DRAFT_CHANGED") event.document = document2;
      try {
        sendIntent(event);
      } catch (failure) {
        if (ordering.complete(seq, false)) {
          busy = false;
          confirmation = false;
          error = failure.message || "\u64CD\u4F5C\u672A\u5B8C\u6210\uFF0C\u8BF7\u91CD\u8BD5\u3002";
          controls();
          settled(failure);
        }
      }
      return reply;
    }
    const submit = (event) => {
      var _a, _b, _c;
      if (event.target.id !== "practice-form") return;
      event.preventDefault();
      if (readOnly) return;
      if (busy || !interactionAllowed() || !((_b = (_a = renderer == null ? void 0 : renderer.hasAnswer) == null ? void 0 : _a.call(renderer)) != null ? _b : authoritative == null ? void 0 : authoritative.question.selectedOptionIds.length)) return;
      confirmation = true;
      controls();
      (_c = root.querySelector("#practice-confirm-submit")) == null ? void 0 : _c.focus();
    };
    const click = (event) => {
      var _a;
      const button = event.target.closest("button");
      if (!button || !root.contains(button) || busy || !interactionAllowed()) return;
      if (button.id === "practice-retry") emit("RETRY").catch(() => {
      });
      else if (button.id === "practice-confirm-submit") {
        confirmation = false;
        (_a = hooks.lockSubmit) == null ? void 0 : _a.call(hooks);
        Promise.resolve().then(() => {
          var _a2;
          return (_a2 = renderer == null ? void 0 : renderer.flushAnswer) == null ? void 0 : _a2.call(renderer);
        }).then(() => idle()).then(() => {
          var _a2;
          return (_a2 = hooks.beforeSubmit) == null ? void 0 : _a2.call(hooks);
        }).then(() => {
          busy = false;
          return emit("SUBMIT");
        }).catch((failure) => {
          busy = false;
          error = failure.message;
          controls();
        }).finally(() => {
          var _a2;
          return (_a2 = hooks.afterSubmit) == null ? void 0 : _a2.call(hooks);
        });
      } else if (button.id === "practice-cancel-submit") {
        confirmation = false;
        controls();
      }
    };
    root.addEventListener("submit", submit);
    if (!readOnly) root.addEventListener("click", click);
    return Object.freeze({
      loadPractice,
      refreshPractice,
      applyResponse,
      loadHistory(value) {
        alive();
        if (!readOnly) throw new Error("History loading requires a read-only card");
        const next = parse(value);
        if (next.question.state !== "SUBMITTED") throw new Error("History requires a submitted Attempt");
        renderAuthoritative(next);
      },
      focusTarget(targetId) {
        var _a, _b;
        alive();
        if (typeof targetId !== "string" || !targetId.trim()) throw new TypeError("Target ID required");
        const node = (_a = renderer == null ? void 0 : renderer.focusTarget) == null ? void 0 : _a.call(renderer, targetId);
        if (node) (_b = hooks.focusTarget) == null ? void 0 : _b.call(hooks, node);
        return Boolean(node);
      },
      async whenIdle() {
        var _a;
        await ((_a = renderer == null ? void 0 : renderer.flushAnswer) == null ? void 0 : _a.call(renderer));
        await idle();
      },
      pendingAnswerIntent() {
        var _a, _b;
        alive();
        return (_b = (_a = renderer == null ? void 0 : renderer.pendingAnswerIntent) == null ? void 0 : _a.call(renderer)) != null ? _b : null;
      },
      async saveDraft(document2) {
        await idle();
        return emit("DRAFT_CHANGED", void 0, document2);
      },
      getOperationState() {
        alive();
        return ordering.getState();
      },
      getViewState() {
        alive();
        return authoritative ? JSON.parse(JSON.stringify(authoritative)) : null;
      },
      destroy() {
        if (destroyed2) return;
        destroyed2 = true;
        settled(new Error("Practice closed"));
        renderer == null ? void 0 : renderer.destroy();
        renderer = null;
        root.removeEventListener("submit", submit);
        root.removeEventListener("click", click);
      }
    });
  }

  // src/history-replay.js
  function readHistoryReplay(viewModel, document2) {
    const practice = readPractice(viewModel);
    if (practice.question.state !== "SUBMITTED") throw new TypeError("History requires a submitted Attempt");
    const source = typeof document2 === "string" ? JSON.parse(document2) : document2;
    const parsed = parseDraftCanvasDocument(source);
    const known = (object4, fields) => {
      if (Object.keys(object4).some((key) => !fields.includes(key))) throw new TypeError("Unknown frozen Draft field");
    };
    known(source, ["schemaVersion", "layoutVersion", "viewport", "questionCard", "strokes"]);
    known(source.viewport, ["x", "y", "zoom"]);
    known(source.questionCard, ["x", "y", "width"]);
    source.strokes.forEach((stroke) => {
      known(stroke, ["id", "tool", "color", "width", "points"]);
      stroke.points.forEach((point2) => known(point2, ["x", "y", "pressure"]));
    });
    return { viewModel: practice, document: parsed };
  }

  // src/history-replay-app.js
  var object3 = document.querySelector("#question-card");
  var canvas = mountDraftCanvas(object3, { accessMode: "READ_ONLY" });
  var card = mountSharedQuestionRuntime(object3, () => {
    throw new Error("History cannot mutate Practice");
  }, () => false, { readOnly: true, focusTarget: (node) => canvas.focusElement(node) });
  var destroyed = false;
  window.draftCanvas = canvas;
  window.historyDraftReplay = Object.freeze({
    loadHistoryDraft(viewModel, document2) {
      if (destroyed) throw new Error("History Draft page closed");
      const replay = readHistoryReplay(viewModel, document2);
      card.loadHistory(replay.viewModel);
      canvas.loadDraft(JSON.stringify(replay.document));
    },
    bindHost() {
      if (!destroyed) window.historyHost.ready();
    },
    focusTarget: card.focusTarget,
    getViewState: card.getViewState,
    diagnostics() {
      return { accessMode: "READ_ONLY", destroyed, canvas: canvas.diagnostics() };
    },
    destroy() {
      if (destroyed) return;
      destroyed = true;
      card.destroy();
      canvas.destroy();
    }
  });
  document.querySelector(".toolbar strong").textContent = "\u5386\u53F2\u8349\u7A3F \xB7 \u53EA\u8BFB";
  document.querySelector("#mode-help").textContent = "\u62D6\u52A8\u5E73\u79FB\uFF0C\u4F7F\u7528\u7F29\u653E\u67E5\u770B\u9898\u5361\u548C\u7B14\u8FF9\u3002";
})();
