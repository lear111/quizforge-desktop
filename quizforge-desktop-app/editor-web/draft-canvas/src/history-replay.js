import { readPractice } from './practice/contract.js';
import { parseDraftCanvasDocument } from './canvas/document.js';

/** A frozen Attempt projection, never an Active Practice operation envelope. */
export function readHistoryReplay(viewModel, document) {
  const practice = readPractice(viewModel);
  if (!['SUBMITTED', 'UNANSWERED', 'DRAFT', 'RETRYING'].includes(practice.question.state)) throw new TypeError('Unsupported archived question state');
  const source = typeof document === 'string' ? JSON.parse(document) : document;
  const parsed = parseDraftCanvasDocument(source);
  const known = (object, fields) => {
    if (Object.keys(object).some(key => !fields.includes(key))) throw new TypeError('Unknown frozen Draft field');
  };
  known(source, ['schemaVersion', 'layoutVersion', 'viewport', 'questionCard', 'strokes', 'texts', 'paper']);
  known(source.viewport, ['x', 'y', 'zoom']); known(source.questionCard, ['x', 'y', 'width']);
  source.strokes.forEach(stroke => {
    known(stroke, ['id', 'tool', 'color', 'width', 'points']);
    stroke.points.forEach(point => known(point, ['x', 'y', 'pressure']));
  });
  source.texts?.forEach(text=>known(text,['id','x','y','width','size','color','text']));
  if(source.paper)known(source.paper,['color','pattern']);
  return { viewModel: practice, document: parsed };
}
