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
        const ids2 = new Set(removed.map((value) => value.id));
        __privateMethod(this, _DraftModel_instances, replaceStrokes_fn).call(this, __privateGet(this, _draft).strokes.filter((value) => !ids2.has(value.id)));
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
  function mountDraftCanvas(object4) {
    if (!(object4 instanceof HTMLElement)) throw new TypeError("A live World object is required");
    const $ = (selector) => document.querySelector(selector);
    const root = $("#draft-canvas-root"), viewport2 = $("#viewport"), world = $("#world");
    const strokes = $("#strokes"), activeStroke = $("#active-stroke");
    const model = new DraftModel();
    const ns = "http://www.w3.org/2000/svg";
    const listeners = [], counts = {}, events = [];
    let mode = "INTERACT", gesture = null, destroyed = false, sequence = 0;
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
      $("#undo").disabled = !model.canUndo;
      $("#redo").disabled = !model.canRedo;
      $("#clear").disabled = !draft.strokes.length;
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
    }
    function setMode(next) {
      alive();
      if (!Object.prototype.hasOwnProperty.call(helps, next)) throw new Error("Unknown tool mode");
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
      if (mode === "INTERACT" || gesture || !event.isPrimary || event.button !== 0) return;
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
      finish(true);
      model[action]();
      render();
    });
    listen(document, "keydown", (event) => {
      if (event.target.matches("input, textarea") || !(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== "z") return;
      event.preventDefault();
      finish(true);
      event.shiftKey ? model.redo() : model.undo();
      render();
    });
    function showJson(exporting) {
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
    }
    function setZoom(zoom, screenAnchor = { x: 0, y: 0 }) {
      alive();
      finish(true);
      model.setZoom(zoom, screenAnchor);
      render();
    }
    function zoomBy(factor) {
      const zoom = Math.min(4, Math.max(0.1, model.getDraft().viewport.zoom * factor));
      setZoom(zoom, { x: viewport2.clientWidth / 2, y: viewport2.clientHeight / 2 });
    }
    function fitCard() {
      alive();
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
    }
    listen($("#zoom-out"), "click", () => zoomBy(1 / 1.2));
    listen($("#zoom-in"), "click", () => zoomBy(1.2));
    listen($("#zoom-fit"), "click", fitCard);
    listen($("#load-json"), "click", () => {
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
      diagnostics() {
        return { supportedPointerEvents: typeof PointerEvent !== "undefined", counts: { ...counts }, events: events.map((e) => ({ ...e })), mode, destroyed };
      },
      destroy() {
        if (destroyed) return;
        finish(false);
        destroyed = true;
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
    if (typeof PointerEvent === "undefined") $("#mode-help").textContent = "\u5F53\u524D\u8FD0\u884C\u73AF\u5883\u7F3A\u5C11 Pointer Events\u3002";
    return Object.freeze({ ...api, addDisposer(dispose) {
      alive();
      listeners.push(dispose);
    } });
  }

  // src/practice/contract.js
  var states = /* @__PURE__ */ new Set(["UNANSWERED", "DRAFT", "SUBMITTED", "RETRYING"]);
  var feedback = /* @__PURE__ */ new Set(["NONE", "CORRECT", "INCORRECT"]);
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
  function content(value, name) {
    if ((value == null ? void 0 : value.kind) !== "TEXT") throw new TypeError(`${name} supports TEXT only in v1`);
    return { kind: "TEXT", text: string(value.text, `${name}.text`) };
  }
  function ids(value, available, name) {
    if (!Array.isArray(value) || new Set(value).size !== value.length || value.some((v) => !available.has(v)))
      throw new TypeError(`${name} must contain unique known option ids`);
    return [...value];
  }
  function readPractice(value) {
    const vm = typeof value === "string" ? JSON.parse(value) : value;
    if ((vm == null ? void 0 : vm.schemaVersion) !== "1.0") throw new TypeError("Unsupported Shared Practice schemaVersion");
    const s = vm.session, q = vm.question;
    if ((q == null ? void 0 : q.type) !== "SINGLE_CHOICE") throw new TypeError("Shared Practice v1 supports SINGLE_CHOICE only");
    if (!states.has(q.state)) throw new TypeError("Unknown authoritative Practice state");
    if (!Number.isInteger(q.index) || !Number.isInteger(q.total) || q.index < 0 || q.index >= q.total)
      throw new TypeError("Question position is invalid");
    if (!Array.isArray(q.options) || q.options.length < 2) throw new TypeError("Question options are missing");
    const options = q.options.map((o) => {
      if (!feedback.has(o.feedback)) throw new TypeError("Unknown authoritative option feedback");
      return { id: id(o.id, "option.id"), content: content(o.content, "option.content"), feedback: o.feedback };
    });
    const available = new Set(options.map((o) => o.id));
    if (available.size !== options.length) throw new TypeError("Option ids must be unique");
    const selected = ids(q.selectedOptionIds, available, "selectedOptionIds");
    if (selected.length > 1) throw new TypeError("SINGLE_CHOICE cannot display multiple selected options");
    const maximum = number(q.maxScore, "question.maxScore");
    let result = null;
    if (q.state === "SUBMITTED") {
      const r = q.result;
      if (!r || !["CORRECT", "INCORRECT"].includes(r.status) || !["INITIAL", "RETRY"].includes(r.attemptMode))
        throw new TypeError("Submitted question requires an authoritative result");
      if (!Number.isInteger(r.attemptNo) || r.attemptNo < 1) throw new TypeError("Invalid attemptNo");
      result = {
        status: r.status,
        score: number(r.score, "result.score"),
        maxScore: number(r.maxScore, "result.maxScore"),
        attemptId: id(r.attemptId, "result.attemptId"),
        attemptNo: r.attemptNo,
        attemptMode: r.attemptMode,
        correctOptionIds: ids(r.correctOptionIds, available, "correctOptionIds"),
        analysis: content(r.analysis, "result.analysis")
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
        type: "SINGLE_CHOICE",
        index: q.index,
        total: q.total,
        prompt: content(q.prompt, "prompt"),
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

  // src/practice/renderer.js
  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== void 0) node.textContent = text;
    return node;
  }
  function mountPracticeCard(root, sendIntent, interactionAllowed = () => true) {
    let authoritative = null, busy = false, destroyed = false, confirmation = false, error = "";
    const ordering = new OperationOrdering();
    function alive() {
      if (destroyed) throw new Error("Shared Practice UI has been destroyed");
    }
    function controls() {
      if (!authoritative) return;
      root.querySelectorAll("input[name=practice-answer]").forEach((input) => {
        input.checked = authoritative.question.selectedOptionIds.includes(input.value);
        input.closest(".practice-option").classList.toggle("selected", input.checked);
        input.disabled = busy || authoritative.question.state === "SUBMITTED" || confirmation;
      });
      const submit2 = root.querySelector("#practice-submit"), retry = root.querySelector("#practice-retry");
      if (submit2) submit2.disabled = busy || !authoritative.question.selectedOptionIds.length;
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
      const fragment = document.createDocumentFragment(), q = vm.question;
      const heading = element("div", "card-heading");
      heading.append(element("span", "tag", "\u5355\u9009\u9898"), element("span", "", `\u7B2C ${q.index + 1} / ${q.total} \u9898`));
      const state = element("span", "practice-state", { UNANSWERED: "\u672A\u4F5C\u7B54", DRAFT: "\u5DF2\u9009\u62E9", SUBMITTED: "\u5DF2\u63D0\u4EA4", RETRYING: "\u91CD\u8BD5\u4E2D" }[q.state]);
      state.id = "practice-state";
      state.dataset.state = q.state;
      const meta = element("div", "practice-meta");
      meta.append(element("span", "practice-score", `\u5206\u503C\uFF1A${q.maxScore}`), state);
      const prompt = element("p", "practice-prompt", q.prompt.text);
      prompt.id = "practice-prompt";
      fragment.append(heading, meta, prompt);
      const form = element("form");
      form.id = "practice-form";
      const options = element("fieldset", "practice-options");
      const legend = element("legend", "", "\u9009\u62E9\u7B54\u6848");
      options.appendChild(legend);
      q.options.forEach((option, index) => {
        const label = element("label", `practice-option feedback-${option.feedback.toLowerCase()}`);
        label.dataset.optionId = option.id;
        const radio = element("input");
        radio.type = "radio";
        radio.name = "practice-answer";
        radio.value = option.id;
        radio.id = `practice-option-${index}`;
        label.append(radio, element("span", "practice-option-text", `${String.fromCharCode(65 + index)}. ${option.content.text}`));
        options.appendChild(label);
      });
      form.appendChild(options);
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
      fragment.appendChild(form);
      const result = element("section", "practice-result");
      result.id = "practice-result";
      result.hidden = !q.result;
      if (q.result) {
        result.append(
          element("p", `practice-result-${q.result.status.toLowerCase()}`, q.result.status === "CORRECT" ? "\u56DE\u7B54\u6B63\u786E" : "\u56DE\u7B54\u9519\u8BEF"),
          element("p", "practice-earned-score", `\u5F97\u5206\uFF1A${q.result.score} / ${q.result.maxScore}`)
        );
        const labels = q.result.correctOptionIds.map((id2) => String.fromCharCode(65 + q.options.findIndex((option) => option.id === id2))).join("\u3001");
        result.appendChild(element("p", "practice-correct-answer", `\u6B63\u786E\u7B54\u6848\uFF1A${labels}`));
        if (q.result.analysis.text) result.append(element("strong", "", "\u7B54\u6848\u4E0E\u89E3\u6790"), element("p", "practice-analysis", q.result.analysis.text));
      }
      fragment.appendChild(result);
      const message = element("p", "practice-error");
      message.id = "practice-error";
      message.setAttribute("role", "alert");
      message.hidden = true;
      fragment.appendChild(message);
      fragment.appendChild(element("p", "practice-stage-note", "\u672C\u9636\u6BB5\u91CD\u8BD5\u4F1A\u4FDD\u7559\u767D\u677F\u7B14\u8FF9\u3002"));
      root.replaceChildren(fragment);
      root.dataset.practiceState = q.state;
    }
    function renderAuthoritative(next, seq) {
      var _a;
      const focus = root.contains(document.activeElement) ? document.activeElement.id : null;
      render(next);
      authoritative = next;
      if (seq !== void 0) ordering.complete(seq, true);
      busy = false;
      confirmation = false;
      error = "";
      controls();
      if (focus && interactionAllowed()) (_a = root.querySelector(`#${focus}`)) == null ? void 0 : _a.focus();
    }
    function loadPractice(value) {
      alive();
      if (authoritative) throw new Error("Practice is already loaded for this page");
      renderAuthoritative(readPractice(value));
    }
    function applyResponse(value) {
      var _a;
      alive();
      const response = typeof value === "string" ? JSON.parse(value) : value;
      const seq = operationSeq(response == null ? void 0 : response.operationSeq);
      if (!ordering.canApply(seq)) return false;
      if (!["SUCCESS", "ERROR"].includes(response.status)) throw new TypeError("Unknown operation response status");
      const next = readPractice(response.viewModel);
      if (!authoritative || next.session.sessionId !== authoritative.session.sessionId || next.question.sessionQuestionId !== authoritative.question.sessionQuestionId)
        throw new Error("Operation response identity does not match this page");
      if (response.status === "SUCCESS") {
        if (response.error != null) throw new TypeError("Successful response must not contain an error");
        renderAuthoritative(next, seq);
      } else {
        if (typeof ((_a = response.error) == null ? void 0 : _a.message) !== "string" || !response.error.message.trim())
          throw new TypeError("Failed response requires an error message");
        ordering.complete(seq, false);
        busy = false;
        confirmation = false;
        error = response.error.message;
        controls();
      }
      return true;
    }
    function emit(type, selectedOptionIds) {
      if (destroyed || busy || !authoritative || !interactionAllowed()) {
        controls();
        return;
      }
      const seq = ordering.begin();
      busy = true;
      error = "";
      controls();
      const event = {
        type,
        sessionId: authoritative.session.sessionId,
        sessionQuestionId: authoritative.question.sessionQuestionId,
        operationSeq: seq
      };
      if (type === "ANSWER_CHANGED") event.selectedOptionIds = selectedOptionIds;
      try {
        sendIntent(event);
      } catch (failure) {
        if (ordering.complete(seq, false)) {
          busy = false;
          confirmation = false;
          error = failure.message || "\u64CD\u4F5C\u672A\u5B8C\u6210\uFF0C\u8BF7\u91CD\u8BD5\u3002";
          controls();
        }
      }
    }
    const change = (event) => {
      if (event.target.matches("input[name=practice-answer]")) emit("ANSWER_CHANGED", [event.target.value]);
    };
    const submit = (event) => {
      var _a;
      if (event.target.id !== "practice-form") return;
      event.preventDefault();
      if (busy || !interactionAllowed() || !(authoritative == null ? void 0 : authoritative.question.selectedOptionIds.length)) return;
      confirmation = true;
      controls();
      (_a = root.querySelector("#practice-confirm-submit")) == null ? void 0 : _a.focus();
    };
    const click = (event) => {
      const button = event.target.closest("button");
      if (!button || !root.contains(button) || busy || !interactionAllowed()) return;
      if (button.id === "practice-retry") emit("RETRY");
      else if (button.id === "practice-confirm-submit") {
        confirmation = false;
        emit("SUBMIT");
      } else if (button.id === "practice-cancel-submit") {
        confirmation = false;
        controls();
      }
    };
    root.addEventListener("change", change);
    root.addEventListener("submit", submit);
    root.addEventListener("click", click);
    return Object.freeze({
      loadPractice,
      applyResponse,
      getOperationState() {
        alive();
        return ordering.getState();
      },
      getViewState() {
        alive();
        return authoritative ? JSON.parse(JSON.stringify(authoritative)) : null;
      },
      destroy() {
        if (destroyed) return;
        destroyed = true;
        root.removeEventListener("change", change);
        root.removeEventListener("submit", submit);
        root.removeEventListener("click", click);
      }
    });
  }

  // src/bridge/practice.js
  function practiceChannel(getHost) {
    let sent = 0;
    return Object.freeze({
      send(event) {
        operationSeq(event.operationSeq);
        const host = getHost();
        if (!host || typeof host.onEvent !== "function") throw new Error("\u7EC3\u4E60\u8FDE\u63A5\u5C1A\u672A\u5C31\u7EEA\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5\u3002");
        sent++;
        host.onEvent(JSON.stringify(event));
      },
      ready() {
        const host = getHost();
        if (!host || typeof host.ready !== "function") throw new Error("Practice host is missing");
        host.ready();
      },
      diagnostics() {
        return { sent };
      }
    });
  }

  // src/shared-practice-app.js
  var object3 = document.querySelector("#question-card");
  var canvas = mountDraftCanvas(object3);
  var channel = practiceChannel(() => window.practiceHost);
  var practice = mountPracticeCard(object3, channel.send, () => canvas.diagnostics().mode === "INTERACT");
  window.draftCanvas = canvas;
  window.sharedPractice = Object.freeze({ ...practice, bindHost: channel.ready, diagnostics: channel.diagnostics });
  canvas.addDisposer(practice.destroy);
  document.querySelector("#mode-help").textContent = "\u4EA4\u4E92\uFF1A\u9009\u62E9\u7B54\u6848\u3001\u63D0\u4EA4\u6216\u91CD\u8BD5\u3002";
})();
