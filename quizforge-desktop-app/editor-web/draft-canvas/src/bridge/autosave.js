/** Coalesces completed canvas changes; acknowledgements, not timers, determine durability. */
export class DraftAutosave {
  constructor(save, { delay = 500, schedule = (fn, ms) => setTimeout(fn, ms), cancel = id => clearTimeout(id) } = {}) {
    this.save = save; this.delay = delay; this.schedule = schedule; this.cancel = cancel;
    this.pending = null; this.saved = null; this.timer = null; this.inFlight = null; this.destroyed = false;
  }
  changed(json) {
    if (this.destroyed) return;
    this.pending = json;
    if (this.timer !== null) this.cancel(this.timer);
    this.timer = this.schedule(() => { this.timer = null; this.flush().catch(() => {}); }, this.delay);
  }
  reset(json) {
    if (this.timer !== null) this.cancel(this.timer);
    this.timer = null; this.pending = json; this.saved = json;
  }
  flush() {
    if (this.destroyed) return Promise.reject(new Error('Draft autosave is closed'));
    if (this.timer !== null) this.cancel(this.timer);
    this.timer = null;
    if (this.inFlight) return this.inFlight;
    this.inFlight = (async () => {
      while (this.pending !== null && this.pending !== this.saved) {
        const value = this.pending;
        await this.save(JSON.parse(value));
        this.saved = value;
      }
    })().finally(() => { this.inFlight = null; });
    return this.inFlight;
  }
  destroy() { this.destroyed = true; if (this.timer !== null) this.cancel(this.timer); this.timer = null; }
  state() { return { dirty: this.pending !== this.saved, saving: this.inFlight !== null }; }
}
