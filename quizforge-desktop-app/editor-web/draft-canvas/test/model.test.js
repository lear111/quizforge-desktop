import test from 'node:test';
import assert from 'node:assert/strict';
import { createDraft, DraftModel, screenToWorld, worldToScreen } from '../src/model.js';

const pen = (id = 'stroke-1', points = [{ x: 100, y: 100, pressure: 0.5 }, { x: 200, y: 100, pressure: 0.8 }]) =>
  ({ id, tool: 'PEN', color: '#7660ab', width: 2, points });

test('Draft JSON round trip preserves stable ids and all durable geometry', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  model.pan(-81, 36);
  const draft = model.getDraft();
  const restored = new DraftModel(JSON.stringify(draft));
  assert.deepEqual(restored.getDraft(), draft);
  assert.equal(restored.getDraft().strokes[0].id, 'stroke-1');
});

test('pan changes viewport only and coordinate conversion uses translation and zoom', () => {
  const draft = createDraft();
  draft.viewport.zoom = 2;
  const model = new DraftModel(draft);
  model.addStroke(pen());
  model.pan(57, -21);
  const result = model.getDraft();
  assert.deepEqual(result.questionCard, draft.questionCard);
  assert.deepEqual(result.strokes[0], pen());
  assert.deepEqual(result.viewport, { x: 28.5, y: -10.5, zoom: 2 });
  const screen = worldToScreen({ x: 100, y: 100 }, result.viewport);
  assert.deepEqual(screen, { x: 257, y: 179 });
  assert.deepEqual(screenToWorld(screen, result.viewport), { x: 100, y: 100 });
});

test('loaded viewport offsets are world units independent of zoom', () => {
  const transform = { x: 25, y: -10, zoom: 3 };
  assert.deepEqual(worldToScreen({ x: 10, y: 20 }, transform), { x: 105, y: 30 });
  assert.deepEqual(screenToWorld({ x: 105, y: 30 }, transform), { x: 10, y: 20 });
});

test('stroke add copies input and getDraft never exposes mutable model state', () => {
  const model = new DraftModel();
  const value = pen();
  assert.equal(model.addStroke(value), 'stroke-1');
  value.points[0].x = 999;
  const result = model.getDraft();
  assert.equal(result.strokes[0].points[0].x, 100);
  result.viewport.x = 900;
  result.strokes[0].points[0].x = 900;
  assert.equal(model.getDraft().viewport.x, 0);
  assert.equal(model.getDraft().strokes[0].points[0].x, 100);
});

test('eraser hits between stroke points and includes the visible stroke width', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  assert.deepEqual(model.eraseAt({ x: 150, y: 103 }, 1), []);
  assert.deepEqual(model.eraseAt({ x: 150, y: 102 }, 1), ['stroke-1']);
  assert.deepEqual(model.getDraft().strokes, []);
});

test('swept eraser deletes crossing segments even when neither sample touches ink', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  assert.deepEqual(model.eraseAlong({ x: 150, y: 50 }, { x: 150, y: 150 }, 0), ['stroke-1']);
});

test('eraser handles single-point dots, endpoints, parallel segments and zero-length ink', () => {
  const model = new DraftModel();
  model.addStroke(pen('dot', [{ x: 20, y: 20 }]));
  model.addStroke(pen('zero', [{ x: 50, y: 50 }, { x: 50, y: 50 }]));
  model.addStroke(pen('line', [{ x: 0, y: 100 }, { x: 100, y: 100 }]));
  assert.deepEqual(model.eraseAlong({ x: 0, y: 20 }, { x: 40, y: 20 }, 0), ['dot']);
  assert.deepEqual(model.eraseAt({ x: 50, y: 50 }, 0), ['zero']);
  assert.deepEqual(model.eraseAlong({ x: 10, y: 102 }, { x: 90, y: 102 }, 1), ['line']);
  assert.equal(model.getDraft().strokes.length, 0);
});

test('undo and redo cover add, erase and clear while preserving later pan', () => {
  const model = new DraftModel();
  assert.equal(model.undo(), false);
  model.addStroke(pen('a'));
  model.addStroke(pen('b', [{ x: 300, y: 200 }]));
  model.eraseAt({ x: 150, y: 100 }, 2);
  model.clear();
  model.pan(33, -12);
  assert.equal(model.canUndo, true);
  model.undo();
  assert.deepEqual(model.getDraft().strokes.map(value => value.id), ['b']);
  model.undo();
  assert.deepEqual(model.getDraft().strokes.map(value => value.id), ['a', 'b']);
  model.undo();
  assert.deepEqual(model.getDraft().strokes.map(value => value.id), ['a']);
  model.undo();
  assert.deepEqual(model.getDraft().strokes, []);
  assert.equal(model.canUndo, false);
  assert.equal(model.canRedo, true);
  for (let index = 0; index < 4; index++) assert.equal(model.redo(), true);
  assert.deepEqual(model.getDraft().strokes, []);
  assert.equal(model.redo(), false);
  assert.deepEqual(model.getDraft().viewport, { x: 33, y: -12, zoom: 1 });
});

test('clear retains the card and viewport; clearing empty state adds no history', () => {
  const model = new DraftModel();
  assert.equal(model.clear(), false);
  assert.equal(model.canUndo, false);
  model.addStroke(pen());
  model.pan(12, 99);
  const before = model.getDraft();
  model.clear();
  const after = model.getDraft();
  assert.deepEqual(after.questionCard, before.questionCard);
  assert.deepEqual(after.viewport, before.viewport);
  assert.deepEqual(after.strokes, []);
  model.undo();
  assert.deepEqual(model.getDraft(), before);
});

test('loadDraft restores viewport, card and strokes, resets history and copies input', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  const input = { schemaVersion: '1.0', layoutVersion: '1', viewport: { x: -210, y: 71, zoom: 1.25 },
    questionCard: { x: 431, y: 87, width: 600 }, strokes: [pen('loaded')] };
  assert.deepEqual(model.loadDraft(input), input);
  assert.equal(model.canUndo, false);
  assert.equal(model.canRedo, false);
  input.questionCard.x = 1000;
  assert.equal(model.getDraft().questionCard.x, 431);
});

test('eraser drag grouping is one undo and preserves the original stroke order', () => {
  const model = new DraftModel();
  model.addStroke(pen('a'));
  model.addStroke(pen('b', [{ x: 300, y: 200 }]));
  model.beginEdit();
  model.eraseAt({ x: 150, y: 100 }, 2);
  model.eraseAt({ x: 300, y: 200 }, 2);
  assert.equal(model.endEdit(), true);
  model.undo();
  assert.deepEqual(model.getDraft().strokes.map(value => value.id), ['a', 'b']);
  model.redo();
  assert.deepEqual(model.getDraft().strokes, []);
});

test('a no-op erase preserves redo while a new edit discards redo', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  model.undo();
  model.beginEdit();
  model.eraseAt({ x: 0, y: 0 });
  assert.equal(model.endEdit(), false);
  assert.equal(model.canRedo, true);
  model.addStroke(pen('new'));
  assert.equal(model.canRedo, false);
});

test('missing optional fields use POC defaults and unrelated fields are excluded from schema', () => {
  const input = createDraft();
  delete input.layoutVersion;
  delete input.questionCard.width;
  input.testAnswer = 'not serialized';
  input.strokes = [{ id: 'dot', tool: 'PEN', width: 2, points: [{ x: 1, y: 2 }] }];
  const result = new DraftModel(input).getDraft();
  assert.equal(result.questionCard.width, 720);
  assert.equal(result.strokes[0].color, '#7660ab');
  assert.equal(result.strokes[0].points[0].pressure, 0.5);
  assert.equal(result.testAnswer, undefined);
});

test('malformed imports are rejected atomically without losing history or current draft', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  const before = model.getDraft();
  const mutations = [
    value => { value.schemaVersion = '2.0'; },
    value => { value.layoutVersion = '2'; },
    value => { value.viewport.zoom = 0; },
    value => { value.viewport.x = NaN; },
    value => { value.questionCard.width = -1; },
    value => { value.strokes.push(pen()); },
    value => { value.strokes[0].points[0].pressure = 1.1; },
    value => { value.strokes[0].points[0].x = Infinity; },
    value => { value.strokes[0].points = []; },
    value => { value.strokes[0].id = ''; },
    value => { value.strokes[0].tool = 'ERASER'; },
    value => { value.strokes[0].color = 'url(https://example.invalid/color)'; },
  ];
  for (const mutate of mutations) {
    const invalid = model.getDraft();
    mutate(invalid);
    assert.throws(() => model.loadDraft(invalid));
    assert.deepEqual(model.getDraft(), before);
    assert.equal(model.canUndo, true);
  }
  assert.throws(() => model.loadDraft('{broken json'));
  assert.deepEqual(model.getDraft(), before);
});

test('invalid coordinates, widths, duplicated ids and overflowing pan cannot corrupt model', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  const before = model.getDraft();
  assert.throws(() => model.addStroke(pen()));
  assert.throws(() => model.addStroke({ ...pen('invalid'), width: 0 }));
  assert.throws(() => model.pan(Infinity, 0));
  assert.throws(() => model.pan(0, '1'));
  assert.throws(() => model.eraseAt({ x: 0, y: 0 }, -1));
  assert.throws(() => model.eraseAlong({ x: 0, y: NaN }, { x: 1, y: 1 }));
  assert.throws(() => screenToWorld({ x: 1, y: 1 }, { x: 0, y: 0, zoom: 0 }));
  assert.deepEqual(model.getDraft(), before);
  const huge = createDraft();
  huge.viewport.x = Number.MAX_VALUE;
  model.loadDraft(huge);
  assert.throws(() => model.pan(Number.MAX_VALUE, 1));
  assert.deepEqual(model.getDraft().viewport, huge.viewport);
});

test('zoom preserves the anchor world point and the logical card/ink snapshot', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  model.pan(40, -60);
  const before = model.getDraft();
  const anchor = { x: 430, y: 215 };
  const anchoredWorld = screenToWorld(anchor, before.viewport);
  model.setZoom(2.5, anchor);
  const after = model.getDraft();
  assert.deepEqual(screenToWorld(anchor, after.viewport), anchoredWorld);
  assert.deepEqual(worldToScreen(anchoredWorld, after.viewport), anchor);
  assert.deepEqual(after.questionCard, before.questionCard);
  assert.deepEqual(after.strokes, before.strokes);
  assert.equal(after.questionCard.width, 720);
  model.setZoom(1, anchor);
  assert.deepEqual(model.getDraft(), before);
});

test('pan/zoom round trip leaves persistent geometry and snapshot width unchanged', () => {
  const input = createDraft();
  input.questionCard.width = 600;
  const model = new DraftModel(input);
  model.addStroke(pen());
  const before = model.getDraft();
  model.setZoom(2, { x: 300, y: 200 });
  model.pan(82, -24);
  model.pan(-82, 24);
  model.setZoom(1, { x: 300, y: 200 });
  assert.deepEqual(model.getDraft(), before);
  assert.equal(model.getDraft().questionCard.width, 600);
});

test('invalid zoom and nonfinite projected geometry reject atomically', () => {
  const model = new DraftModel();
  model.addStroke(pen());
  const before = model.getDraft();
  for (const value of [0, -1, NaN, Infinity, Number.MAX_VALUE, Number.MIN_VALUE]) {
    assert.throws(() => model.setZoom(value, { x: 10, y: 20 }));
    assert.deepEqual(model.getDraft(), before);
  }
  assert.throws(() => model.setZoom(2, { x: Infinity, y: 0 }));
  assert.deepEqual(model.getDraft(), before);
  assert.throws(() => worldToScreen({ x: Number.MAX_VALUE, y: 0 }, { x: 0, y: 0, zoom: 2 }));
  assert.throws(() => screenToWorld({ x: Number.MAX_VALUE, y: 0 }, { x: 0, y: 0, zoom: 0.5 }));
});
