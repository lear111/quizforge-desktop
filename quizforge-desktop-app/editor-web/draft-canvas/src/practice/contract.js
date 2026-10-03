import { QuestionRendererRegistry } from '../shared/renderer/registry.js';
import { textContent as content, optionIds as ids } from '../renderer/choice/contract.js';
/** Presentation contract only: no scoring, transitions, Attempts or retry rules live here. */
const states = new Set(['UNANSWERED', 'DRAFT', 'SUBMITTED', 'RETRYING']);
function string(value, name) {
  if (typeof value !== 'string') throw new TypeError(`${name} must be text`);
  return value;
}
function id(value, name) { if (!string(value, name).trim()) throw new TypeError(`${name} is required`); return value; }
function number(value, name) { if (typeof value !== 'number' || !Number.isFinite(value)) throw new TypeError(`${name} must be finite`); return value; }
export function readPractice(value) {
  const vm = typeof value === 'string' ? JSON.parse(value) : value;
  if (vm?.schemaVersion !== '1.0') throw new TypeError('Unsupported Shared Practice schemaVersion');
  const s = vm.session, q = vm.question;
  const definition = QuestionRendererRegistry.require(q?.type);
  if (!states.has(q.state)) throw new TypeError('Unknown authoritative Practice state');
  if (!Number.isInteger(q.index) || !Number.isInteger(q.total) || q.index < 0 || q.index >= q.total)
    throw new TypeError('Question position is invalid');
  const choice = definition.parse(q);
  const { options, available, selectedOptionIds: selected } = choice;
  const maximum = number(q.maxScore, 'question.maxScore');
  let result = null;
  if (q.state === 'SUBMITTED') {
    const r = q.result;
    if (!r || !['CORRECT', 'INCORRECT'].includes(r.status) || !['INITIAL', 'RETRY', 'REVISION'].includes(r.attemptMode))
      throw new TypeError('Submitted question requires an authoritative result');
    if (!Number.isInteger(r.attemptNo) || r.attemptNo < 1) throw new TypeError('Invalid attemptNo');
    result = { status: r.status, score: number(r.score, 'result.score'), maxScore: number(r.maxScore, 'result.maxScore'),
      attemptId: id(r.attemptId, 'result.attemptId'), attemptNo: r.attemptNo, attemptMode: r.attemptMode,
      correctOptionIds: ids(r.correctOptionIds, available, 'correctOptionIds'), analysis: content(r.analysis, 'result.analysis') };
  } else if (q.result != null || options.some(o => o.feedback !== 'NONE')) {
    throw new TypeError('Unsubmitted question must not expose result feedback');
  }
  return { schemaVersion: '1.0', session: { sessionId: id(s?.sessionId, 'sessionId'), bankAssetId: id(s?.bankAssetId, 'bankAssetId'), bankContentId: id(s?.bankContentId, 'bankContentId') },
    question: { sessionQuestionId: id(q.sessionQuestionId, 'sessionQuestionId'), questionId: id(q.questionId, 'questionId'),
      type: definition.questionType, selectionMode: definition.selectionMode, index: q.index, total: q.total, prompt: content(q.prompt, 'prompt'), options,
      selectedOptionIds: selected, state: q.state, maxScore: maximum, result } };
}
