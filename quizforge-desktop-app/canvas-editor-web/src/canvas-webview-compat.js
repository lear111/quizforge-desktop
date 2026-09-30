// Some JavaFX WebKit versions omit the weight/style from the Canvas font getter.
// Canvas Editor keys its measurement cache by that getter, so bold and regular
// glyphs otherwise share widths. Keep the assigned font and save/restore state.
export function installCanvasFontCompatibility() {
  if (window.__quizforgeCanvasFontCompatibility) return;
  const prototype = CanvasRenderingContext2D.prototype;
  const font = Object.getOwnPropertyDescriptor(prototype, 'font');
  const probe = document.createElement('canvas').getContext('2d');
  probe.font = 'bold 16px Arial';
  if (/\b(bold|[6-9]00)\b/.test(probe.font)) return;

  const states = new WeakMap();
  const validator = document.createElement('span').style;
  const save = prototype.save;
  const restore = prototype.restore;
  function state(context) {
    let entry = states.get(context);
    if (!entry) {
      entry = {font: font.get.call(context), stack: []};
      states.set(context, entry);
    }
    return entry;
  }
  Object.defineProperty(prototype, 'font', {
    ...font,
    get() { return state(this).font; },
    set(value) {
      font.set.call(this, value);
      validator.font = '';
      validator.font = String(value);
      if (validator.font) {
        // Explicit normal also separates old HMR cache entries from corrected ones.
        state(this).font = /^\d/.test(String(value)) ? 'normal ' + value : String(value);
      }
    }
  });
  prototype.save = function() {
    const entry = state(this);
    save.call(this);
    entry.stack.push(entry.font);
  };
  prototype.restore = function() {
    restore.call(this);
    const entry = state(this);
    if (entry.stack.length) entry.font = entry.stack.pop();
  };
  window.__quizforgeCanvasFontCompatibility = true;
}
