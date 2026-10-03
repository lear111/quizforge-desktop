/** Presentation contract only: no scoring, transitions, Attempts or retry rules live here. */
const states = new Set(['UNANSWERED', 'DRAFT', 'SUBMITTED', 'RETRYING']);
const feedback = new Set(['NONE', 'CORRECT', 'INCORRECT']);
function string(value, name) {
  if (typeof value !== 'string') throw new TypeError(`${name} must be text`);
  return value;
}
function id(value, name) { if (!string(value, name).trim()) throw new TypeError(`${name} is required`); return value; }
function number(value, name) { if (typeof value !== 'number' || !Number.isFinite(value)) throw new TypeError(`${name} must be finite`); return value; }
function content(value, name) {
  if (value?.kind !== 'TEXT') throw new TypeError(`${name} supports TEXT only in v1`);
  return { kind: 'TEXT', text: string(value.text, `${name}.text`) };
}
function ids(value, available, name) {
  if (!Array.isArray(value) || new Set(value).size !== value.length || value.some(v => !available.has(v)))
    throw new TypeError(`${name} must contain unique known option ids`);
  return [...value];
}
export function readPractice(value) {
  const vm = typeof value === 'string' ? JSON.parse(value) : value;
  if (vm?.schemaVersion !== '1.0') throw new TypeError('Unsupported Shared Practice schemaVersion');
  const s = vm.session, q = vm.question;
  if (q?.type !== 'SINGLE_CHOICE') throw new TypeError('Shared Practice v1 supports SINGLE_CHOICE only');
  if (!states.has(q.state)) throw new TypeError('Unknown authoritative Practice state');
  if (!Number.isInteger(q.index) || !Number.isInteger(q.total) || q.index < 0 || q.index >= q.total)
    throw new TypeError('Question position is invalid');
  if (!Array.isArray(q.options) || q.options.length < 2) throw new TypeError('Question options are missing');
  const options = q.options.map(o => {
    if (!feedback.has(o.feedback)) throw new TypeError('Unknown authoritative option feedback');
    return { id: id(o.id, 'option.id'), content: content(o.content, 'option.content'), feedback: o.feedback };
  });
  const available = new Set(options.map(o => o.id));
  if (available.size !== options.length) throw new TypeError('Option ids must be unique');
  const selected = ids(q.selectedOptionIds, available, 'selectedOptionIds');
  if (selected.length > 1) throw new TypeError('SINGLE_CHOICE cannot display multiple selected options');
  const maximum = number(q.maxScore, 'question.maxScore');
  let result = null;
  if (q.state === 'SUBMITTED') {
    const r = q.result;
    if (!r || !['CORRECT', 'INCORRECT'].includes(r.status) || !['INITIAL', 'RETRY'].includes(r.attemptMode))
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
      type: 'SINGLE_CHOICE', index: q.index, total: q.total, prompt: content(q.prompt, 'prompt'), options,
      selectedOptionIds: selected, state: q.state, maxScore: maximum, result } };
}
