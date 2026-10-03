/** Transport ordering only. Practice transitions and grading remain in Java/Core. */
export function operationSeq(value) {
  if (!Number.isSafeInteger(value) || value < 1) throw new TypeError('operationSeq must be a positive safe integer');
  return value;
}

export class OperationOrdering {
  #issued = 0;
  #applied = 0;
  #authoritative = 0;
  #pending = null;

  begin() {
    if (this.#pending !== null) throw new Error('A Practice mutation is already in flight');
    const seq = operationSeq(this.#issued + 1);
    this.#issued = seq;
    this.#pending = seq;
    return seq;
  }

  canApply(value) {
    const seq = operationSeq(value);
    return seq > this.#applied && (this.#pending === null || seq === this.#pending);
  }

  complete(value, success) {
    const seq = operationSeq(value);
    if (!this.canApply(seq)) return false;
    // Failed operations consume a transport sequence, never a new authoritative state.
    this.#applied = seq;
    this.#issued = Math.max(this.#issued, seq);
    if (success) this.#authoritative = seq;
    this.#pending = null;
    return true;
  }

  getState() {
    return { lastIssuedSeq: this.#issued, lastAppliedSeq: this.#applied,
      lastAuthoritativeSeq: this.#authoritative, inFlightSeq: this.#pending };
  }
}
