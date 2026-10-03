import test from 'node:test';
import assert from 'node:assert/strict';
import { DraftAutosave } from '../src/bridge/autosave.js';

function setup(save) {
  const timers = new Map(); let id = 0;
  const autosave = new DraftAutosave(save, { schedule: fn => { timers.set(++id, fn); return id; }, cancel: key => timers.delete(key) });
  autosave.reset('{}');
  return { autosave, fire: () => { const jobs = [...timers.values()]; timers.clear(); jobs.forEach(fn => fn()); }, timers };
}
test('debounce coalesces changes and persists only the final completed document', async () => {
  const saved = []; const { autosave, fire, timers } = setup(async doc => saved.push(doc));
  autosave.changed('{"strokes":1}'); autosave.changed('{"strokes":2}');
  assert.equal(timers.size, 1); assert.deepEqual(saved, []);
  fire(); await autosave.flush();
  assert.deepEqual(saved, [{ strokes: 2 }]); assert.equal(autosave.state().dirty, false);
});
test('immediate submit flush includes the last stroke and waits for its acknowledgement', async () => {
  const events = []; let ack;
  const { autosave, timers } = setup(doc => { events.push(['SAVE', doc]); return new Promise(resolve => { ack = resolve; }); });
  autosave.changed('{"lastStroke":"final"}');
  const submit = autosave.flush().then(() => events.push(['SUBMIT']));
  assert.equal(timers.size, 0); assert.equal(events.length, 1);
  ack(); await submit;
  assert.deepEqual(events, [['SAVE', { lastStroke: 'final' }], ['SUBMIT']]);
});
test('a change during an acknowledgement is saved in order before a flush completes', async () => {
  const saved = []; let ack;
  const { autosave } = setup(doc => { saved.push(doc); return new Promise(resolve => { ack = resolve; }); });
  autosave.changed('{"n":1}'); const flush = autosave.flush();
  autosave.changed('{"n":2}'); const concurrent = autosave.flush();
  ack(); await Promise.resolve(); await Promise.resolve();
  assert.deepEqual(saved, [{ n: 1 }, { n: 2 }]); ack(); await Promise.all([flush, concurrent]);
  assert.equal(autosave.state().dirty, false);
});
test('failed draft save keeps dirty document and prevents submit; explicit retry can succeed', async () => {
  let fail = true, submitted = false;
  const { autosave } = setup(async () => { if (fail) throw new Error('DB failure'); });
  autosave.changed('{"stroke":"keep"}');
  await assert.rejects(autosave.flush().then(() => { submitted = true; }), /DB failure/);
  assert.equal(submitted, false); assert.equal(autosave.state().dirty, true);
  fail = false; await autosave.flush(); assert.equal(autosave.state().dirty, false);
});
test('restore is clean and retry reset cancels pending autosave without copying old ink', async () => {
  const saved = []; const { autosave, timers } = setup(async value => saved.push(value));
  autosave.changed('{"old":1}'); autosave.reset('{}');
  assert.equal(timers.size, 0); await autosave.flush(); assert.deepEqual(saved, []);
  autosave.destroy(); await assert.rejects(autosave.flush(), /closed/);
});
