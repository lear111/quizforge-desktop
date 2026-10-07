import {connectPage} from './lib/page-client.js';
export async function start(){
const page=await connectPage(QF);
const multiple = true;
const $ = QF.dom.$, on = QF.dom.on;
const reply = await page.question(); if (!reply.ok) throw new Error(reply.error.message);
const question = reply.data;
QF.layout.configure({ cardWidth: 720, maxCardWidth: '100%', horizontalAlign: 'center', verticalAlign: 'center', padding: 20 });
QF.ui.configure({ typeLabel: false, position: false, score: false, state: false, submit: false, retry: false, confirmation: false, note: false, sources: false, draftToggle: true, draftToolbar: true, draftZoom: true });
let confirming = false, actionBusy = false, refreshRevision = 0, answerRevision = 0, answerWrites = 0;
$('[data-type-label]').textContent = multiple ? '多选题' : '单选题';
$('[data-choice-hint]').textContent = multiple ? '请选择所有符合题意的选项' : '请选择一个符合题意的选项';
async function runAction(action) {
  if (actionBusy) return; actionBusy = true;
  try { await refresh(); const response = await action(); if (!response.ok) QF.ui.notify(response.error.message); return response; }
  finally { actionBusy = false; await refresh(); }
}
on($('[data-submit-answer]'), 'click', () => { confirming = true; $('[data-answer-confirm]').hidden = false; refresh().then(() => $('[data-confirm-answer]').focus()); });
on($('[data-cancel-answer]'), 'click', () => { confirming = false; refresh(); });
on($('[data-confirm-answer]'), 'click', () => { confirming = false; return runAction(() => page.submit()); });
on($('[data-retry-answer]'), 'click', () => runAction(() => page.action('retry')));
on($('[data-sources]'), 'click', event => { const button = event.target.closest('[data-source-index]'); if (button && !button.disabled) return runAction(() => page.action('openSource', {index:Number(button.dataset.sourceIndex)})); });
$('[data-prompt]').textContent = question.prompt.text;
const controls = [];
question.payload.options.forEach((option, index) => {
  const row = document.createElement('label'); row.className = 'qf-choice-option'; row.dataset.optionId = option.id;
  const input = document.createElement('input'); input.type = multiple ? 'checkbox' : 'radio'; input.name = 'qf-choice-' + question.id; input.value = option.id;
  const letter = document.createElement('span'); letter.className = 'qf-option-letter'; letter.textContent = String.fromCharCode(65 + index) + '.'; letter.setAttribute('aria-hidden', 'true');
  const text = document.createElement('span'); text.className = 'qf-option-text'; text.textContent = option.content.text;
  input.setAttribute('aria-label', String.fromCharCode(65 + index) + '. ' + option.content.text);
  const feedback = document.createElement('span'); feedback.className = 'qf-option-feedback'; feedback.hidden = true;
  row.append(input, letter, text, feedback); $('[data-options]').append(row); controls.push({ option, input, row, feedback });
  on(input, 'change', async () => {
    // Invalidate reads already in flight; never repaint an older answer over a newer click.
    ++answerRevision; ++refreshRevision; ++answerWrites;
    const selectedOptionIds = controls.filter(c => c.input.checked).map(c => c.option.id);
    row.classList.toggle('selected', input.checked);
    try {
      const response = await page.write({ selectedOptionIds });
      if (!response.ok) QF.ui.notify(response.error.message);
    } finally {
      --answerWrites;
      if (!answerWrites) await refresh();
    }
  });
});
async function refresh() {
  const revision = ++refreshRevision, answerVersion = answerRevision;
  const [a, c, r, s, n] = await Promise.all([page.answer(), page.host(), page.result(), page.practiceState(), page.state()]);
  if (revision !== refreshRevision || answerVersion !== answerRevision) return;
  if (!a.ok || !c.ok || !r.ok || !s.ok) return;
  const selected = new Set(a.data.selectedOptionIds || []), result = r.data;
  const capabilities = c.data.capabilities, state = s.data;
  const readOnly = c.data.mode === 'HISTORY' || c.data.mode === 'PREVIEW';
  const permissions = c.data.permissions.granted;
  $('[data-question-position]').textContent = Number.isInteger(state.index) ? `第 ${state.index + 1} / ${state.total} 题` : '';
  $('[data-max-score]').textContent = `分值：${state.maxScore ?? '未设置'}`;
  $('[data-answer-state]').textContent = ({ UNANSWERED: '未作答', DRAFT: '已选择', SUBMITTED: '已提交', RETRYING: '重试中', REVISING: '修订中' })[state.state] || '';
  if (!capabilities.submit || result || c.data.mode === 'HISTORY' || c.data.mode === 'PREVIEW') confirming = false;
  $('[data-answer-confirm]').hidden = !confirming;
  // Temporary interaction locks disable actions without changing the card's layout.
  $('[data-submit-answer]').hidden = readOnly || !permissions.includes('practice.submit') || Boolean(result);
  $('[data-submit-answer]').disabled = actionBusy || confirming || !capabilities.submit || selected.size === 0;
  $('[data-retry-answer]').hidden = readOnly || !permissions.includes('practice.retry') || !result;
  $('[data-retry-answer]').disabled = actionBusy || !capabilities.retry;
  $('[data-confirm-answer]').disabled = actionBusy || !capabilities.submit || selected.size === 0;
  const sourceRows = $('[data-sources]'); sourceRows.replaceChildren();
  const sources = n.ok ? n.data.sources : []; $('[data-source-section]').hidden = !sources.length;
  sources.forEach((source, index) => {
    const row = document.createElement('div'); row.className = 'qf-type-source'; const button = document.createElement('button'); button.type = 'button'; button.textContent = source.label; button.disabled = actionBusy || !capabilities.viewSources || !source.navigable; button.dataset.sourceIndex = index;
    const message = document.createElement('span'); message.className = 'muted'; message.textContent = source.message; row.append(button, message); sourceRows.append(row);
  });
  $('[data-stage-note]').textContent = c.data.mode === 'HISTORY' ? '历史草稿 · 只读' : c.data.mode === 'PREVIEW' ? '只读预览' : '草稿自动保存；提交后冻结，重试从空白草稿开始。';
  const correct = result?.reference?.answerSpec?.correctOptionIds || result?.correctOptionIds || [];
  for (const { option, input, row, feedback } of controls) {
    if (!answerWrites) input.checked = selected.has(option.id);
    input.disabled = actionBusy || confirming || !capabilities.editAnswer;
    row.classList.toggle('selected', input.checked);
    row.classList.toggle('correct', Boolean(result) && correct.includes(option.id));
    row.classList.toggle('incorrect', Boolean(result) && input.checked && !correct.includes(option.id));
    feedback.hidden = !result || (!input.checked && !correct.includes(option.id));
    feedback.textContent = correct.includes(option.id) ? (input.checked ? '正确 · 已选' : '正确答案') : '你的答案';
  }
  $('[data-result]').hidden = !result;
  if (result) {
    $('[data-verdict]').textContent = result.status === 'CORRECT' ? '回答正确' : result.status === 'INCORRECT' ? '回答错误' : '未评分';
    $('[data-verdict]').className = result.status === 'CORRECT' ? 'correct' : 'incorrect';
    $('[data-score]').textContent = '得分：' + (result.score ?? '未评分') + ' / ' + result.maxScore;
    $('[data-correct-answer]').textContent = '正确答案：' + question.payload.options.map((o, i) => correct.includes(o.id) ? String.fromCharCode(65 + i) : null).filter(Boolean).join('、');
    QF.content.render($('[data-analysis]'), result.reference?.analysis || result.analysis || { kind: 'TEXT', text: '' });
  }
}
page.subscribe(refresh); await refresh();

}
