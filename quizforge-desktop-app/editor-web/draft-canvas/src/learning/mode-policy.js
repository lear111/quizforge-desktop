export const SharedLearningSurfaceMode = Object.freeze({ PRACTICE: 'PRACTICE', DRAFT: 'DRAFT' });
const policies = Object.freeze({
  PRACTICE: Object.freeze({ answerInteractive: true, annotationsVisible: true, annotationEditing: false, userViewport: false }),
  DRAFT: Object.freeze({ answerInteractive: true, annotationsVisible: true, annotationEditing: true, userViewport: true }),
  HISTORY: Object.freeze({ answerInteractive: false, annotationsVisible: true, annotationEditing: false, userViewport: true })
});
export function modePolicy(mode) {
  if (!Object.prototype.hasOwnProperty.call(policies, mode)) throw new TypeError('Unknown learning surface mode');
  return policies[mode];
}
/** Computed display transform; never changes the persisted World or draft viewport. */
export function practiceViewport(card, width, scroll = 0, height = 0, cardHeight = 0, layout = defaultLayout()) {
  const zoom = Math.min(1, Math.max(0.1, availableCardWidth(layout,width) / card.width));
  const top = alignedOffset(layout.verticalAlign,height,cardHeight*zoom,layout.padding);
  const left = alignedOffset(layout.horizontalAlign,width,card.width*zoom,layout.padding);
  return { x: left/zoom-card.x, y: (top - scroll) / zoom - card.y, zoom };
}
import {defaultLayout,availableCardWidth,alignedOffset} from '../shared/ui/layout.js';
