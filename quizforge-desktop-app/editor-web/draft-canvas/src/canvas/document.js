/** Internal geometry contract. These values are independent of DOM/SVG and device pixels. */
export const DRAFT_SCHEMA_VERSION = '1.0';
export const DRAFT_LAYOUT_VERSION = '1';
export const DEFAULT_CARD_WIDTH = 720;
const DEFAULT_COLOR = '#7660ab';

function object(value, name) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new TypeError(`${name} must be an object`);
  return value;
}
function finite(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value)) throw new TypeError(`${name} must be a finite number`);
  return value;
}
function positive(value, name) {
  if (finite(value, name) <= 0) throw new RangeError(`${name} must be positive`);
  return value;
}
function inputObject(value) {
  return object(typeof value === 'string' ? JSON.parse(value) : value, 'draft');
}

/** Strict canonical stroke parsing; runtime stroke creation may explicitly request POC defaults. */
export function normalizeStroke(value, pocDefaults = false) {
  object(value, 'stroke');
  if (typeof value.id !== 'string' || !value.id.trim()) throw new TypeError('stroke.id must be a nonempty stable string');
  if (value.tool !== 'PEN') throw new TypeError('stroke.tool must be PEN');
  const color = pocDefaults && value.color === undefined ? DEFAULT_COLOR : value.color;
  if (typeof color !== 'string' || !/^#[0-9a-f]{3}(?:[0-9a-f]{3}(?:[0-9a-f]{2})?)?$/i.test(color)) {
    throw new TypeError('stroke.color must be a hexadecimal CSS color');
  }
  if (!Array.isArray(value.points) || !value.points.length) throw new TypeError('stroke.points must contain at least one world point');
  return {
    id: value.id, tool: 'PEN', color, width: positive(value.width, 'stroke.width'),
    points: value.points.map((point, index) => {
      object(point, `stroke.points[${index}]`);
      const pressure = pocDefaults && point.pressure === undefined ? 0.5 : finite(point.pressure, 'pressure');
      if (pressure < 0 || pressure > 1) throw new RangeError('pressure must be between 0 and 1');
      return { x: finite(point.x, 'point.x'), y: finite(point.y, 'point.y'), pressure };
    }),
  };
}

/** Parse v1 explicitly. Missing/unknown versions and missing required geometry are errors. */
export function parseDraftCanvasDocument(value) {
  const input = inputObject(value);
  if (input.schemaVersion !== DRAFT_SCHEMA_VERSION) throw new TypeError('Unsupported draft schemaVersion');
  if (input.layoutVersion !== DRAFT_LAYOUT_VERSION) throw new TypeError('Unsupported draft layoutVersion');
  const view = object(input.viewport, 'viewport');
  const card = object(input.questionCard, 'questionCard');
  if (!Array.isArray(input.strokes)) throw new TypeError('draft.strokes must be an array');
  const strokes = input.strokes.map(value => normalizeStroke(value));
  if (new Set(strokes.map(value => value.id)).size !== strokes.length) throw new TypeError('stroke ids must be unique');
  return {
    schemaVersion: DRAFT_SCHEMA_VERSION,
    layoutVersion: DRAFT_LAYOUT_VERSION,
    viewport: { x: finite(view.x, 'viewport.x'), y: finite(view.y, 'viewport.y'), zoom: positive(view.zoom, 'viewport.zoom') },
    questionCard: { x: finite(card.x, 'questionCard.x'), y: finite(card.y, 'questionCard.y'), width: positive(card.width, 'questionCard.width') },
    strokes,
  };
}

/** Explicit compatibility boundary for pre-contract POC JSON; only absent layoutVersion is upgraded. */
export function upgradePocDraft(value) {
  const input = inputObject(value);
  if (input.layoutVersion !== undefined) return parseDraftCanvasDocument(input);
  if (input.schemaVersion !== DRAFT_SCHEMA_VERSION) throw new TypeError('Unsupported draft schemaVersion');
  const card = object(input.questionCard, 'questionCard');
  if (!Array.isArray(input.strokes)) throw new TypeError('draft.strokes must be an array');
  return parseDraftCanvasDocument({ ...input, layoutVersion: DRAFT_LAYOUT_VERSION,
    questionCard: { ...card, width: card.width === undefined ? DEFAULT_CARD_WIDTH : card.width },
    strokes: input.strokes.map(value => normalizeStroke(value, true)),
  });
}

export function normalizePocDraft(value) { return upgradePocDraft(value); }

export function createDraftCanvasDocument() {
  return { schemaVersion: DRAFT_SCHEMA_VERSION, layoutVersion: DRAFT_LAYOUT_VERSION,
    viewport: { x: 0, y: 0, zoom: 1 }, questionCard: { x: 120, y: 70, width: DEFAULT_CARD_WIDTH }, strokes: [] };
}
