import test from 'node:test';
import assert from 'node:assert/strict';
import { OperationOrdering, operationSeq } from '../src/bridge/ordering.js';

test('single flight orders answer, submit and retry barriers without guessing Practice state', () => {
  const order = new OperationOrdering();
  const answer = order.begin();
  assert.throws(() => order.begin(), /already in flight/);
  assert.equal(order.complete(answer, true), true);
  const submit = order.begin(); assert.equal(submit, answer + 1);
  order.complete(submit, true);
  const retry = order.begin(); assert.equal(retry, submit + 1);
  order.complete(retry, true);
  assert.deepEqual(order.getState(), { lastIssuedSeq: 3, lastAppliedSeq: 3, lastAuthoritativeSeq: 3, inFlightSeq: null });
});

test('old answer responses cannot overwrite submit, and old submit cannot overwrite retry', () => {
  const order = new OperationOrdering();
  order.complete(order.begin(), true);
  const submit = order.begin(); order.complete(submit, true);
  assert.equal(order.complete(submit - 1, true), false);
  const retry = order.begin(); order.complete(retry, true);
  assert.equal(order.complete(submit, true), false);
  assert.equal(order.getState().lastAuthoritativeSeq, retry);
});

test('a stale response cannot release a newer in-flight mutation or replace its error', () => {
  const order = new OperationOrdering();
  order.complete(order.begin(), true);
  const current = order.begin();
  assert.equal(order.canApply(current - 1), false);
  assert.equal(order.canApply(current + 1), false);
  assert.equal(order.complete(current - 1, false), false);
  assert.equal(order.getState().inFlightSeq, current);
  order.complete(current, false);
  assert.equal(order.complete(current - 1, true), false);
});

test('failure consumes a transport sequence but never advances authoritative state', () => {
  const order = new OperationOrdering();
  order.complete(order.begin(), true);
  const failed = order.begin(); order.complete(failed, false);
  assert.equal(order.getState().lastAuthoritativeSeq, 1);
  assert.equal(order.getState().lastAppliedSeq, failed);
  assert.equal(order.complete(failed, true), false, 'A late duplicate cannot undo an already handled failure');
  assert.equal(order.begin(), failed + 1);
});

test('sequenced host pushes keep subsequent locally issued operations monotonic', () => {
  const order = new OperationOrdering();
  assert.equal(order.complete(41, true), true);
  assert.equal(order.begin(), 42);
  order.complete(42, true);
  assert.equal(order.complete(41, false), false);
});

test('operation sequences reject invalid and overflowing integers', () => {
  for (const value of [0, -1, 1.5, '1', null, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1])
    assert.throws(() => operationSeq(value));
  const order = new OperationOrdering(); order.complete(Number.MAX_SAFE_INTEGER, true);
  assert.throws(() => order.begin());
});
