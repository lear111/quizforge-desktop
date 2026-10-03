import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createDraftCanvasDocument, parseDraftCanvasDocument, upgradePocDraft, normalizePocDraft } from '../src/canvas/document.js';

const fixture = () => JSON.parse(readFileSync(new URL('./fixtures/document-v1.json', import.meta.url), 'utf8'));

test('shared Java/JS v1 fixture round trips with schema and layout versions preserved separately', () => {
  const input = fixture();
  const parsed = parseDraftCanvasDocument(JSON.stringify(input));
  assert.deepEqual(parsed, input);
  assert.deepEqual(parseDraftCanvasDocument(JSON.stringify(parsed)), input);
  assert.equal(parsed.schemaVersion, '1.0');
  assert.equal(parsed.layoutVersion, '1');
});

test('canonical geometry preserves positive snapshot width rather than forcing the default', () => {
  const input = fixture();
  input.questionCard.width = 600;
  assert.equal(parseDraftCanvasDocument(input).questionCard.width, 600);
  assert.equal(createDraftCanvasDocument().questionCard.width, 720);
});

test('canonical parser rejects missing or unsupported schema/layout independently', () => {
  for (const field of ['schemaVersion', 'layoutVersion']) {
    for (const invalid of [undefined, null, 1, '', 'unknown']) {
      const input = fixture();
      if (invalid === undefined) delete input[field];
      else input[field] = invalid;
      assert.throws(() => parseDraftCanvasDocument(input));
    }
  }
});

test('canonical required width, color and pressure are not silently defaulted', () => {
  for (const mutate of [
    input => { delete input.questionCard.width; },
    input => { delete input.strokes[0].color; },
    input => { delete input.strokes[0].points[0].pressure; },
  ]) {
    const input = fixture();
    mutate(input);
    assert.throws(() => parseDraftCanvasDocument(input));
    assert.throws(() => normalizePocDraft(input));
  }
});

test('explicit POC upgrade only upgrades absent layoutVersion and preserves existing snapshot width', () => {
  const input = fixture();
  delete input.layoutVersion;
  input.questionCard.width = 600;
  delete input.strokes[0].color;
  delete input.strokes[0].points[0].pressure;
  const upgraded = upgradePocDraft(input);
  assert.equal(upgraded.layoutVersion, '1');
  assert.equal(upgraded.questionCard.width, 600);
  assert.equal(upgraded.strokes[0].color, '#7660ab');
  assert.equal(upgraded.strokes[0].points[0].pressure, 0.5);
  assert.equal(input.layoutVersion, undefined);
  assert.equal(input.strokes[0].color, undefined);
  delete input.questionCard.width;
  assert.equal(normalizePocDraft(JSON.stringify(input)).questionCard.width, 720);
  for (const version of [null, '2', 1]) {
    input.layoutVersion = version;
    assert.throws(() => upgradePocDraft(input));
  }
});

test('canonical output copies only the explicit contract, never DOM or SVG internals', () => {
  const input = fixture();
  input.dom = { nodeType: 1 };
  input.viewport.clientWidth = 400;
  input.questionCard.innerHTML = '<p>Not draft data</p>';
  input.strokes[0].svgPath = 'M0 0';
  input.strokes[0].points[0].clientX = 1000;
  const parsed = parseDraftCanvasDocument(input);
  assert.deepEqual(parsed, fixture());
  input.strokes[0].points[0].x = 9000;
  assert.equal(parsed.strokes[0].points[0].x, 150.25);
});

test('strict document validation rejects nonfinite geometry, zero zoom/width, invalid pressure and duplicate IDs', () => {
  const mutations = [
    input => { input.viewport.x = Infinity; },
    input => { input.viewport.zoom = 0; },
    input => { input.viewport.zoom = '1'; },
    input => { input.questionCard.x = NaN; },
    input => { input.questionCard.width = -720; },
    input => { input.questionCard.width = null; },
    input => { input.strokes[0].width = 0; },
    input => { input.strokes[0].points[0].x = Infinity; },
    input => { input.strokes[0].points[0].pressure = -0.01; },
    input => { input.strokes[0].points[0].pressure = 1.01; },
    input => { input.strokes[0].id = ' '; },
    input => { input.strokes[0].tool = 'SVG'; },
    input => { input.strokes[0].color = 'url(https://invalid.example/)'; },
    input => { input.strokes[0].points = []; },
    input => { input.strokes.push(input.strokes[0]); },
  ];
  for (const mutate of mutations) {
    const input = fixture(); mutate(input);
    assert.throws(() => parseDraftCanvasDocument(input));
  }
});
