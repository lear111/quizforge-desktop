// 练习、草稿、历史和网页预览共用此页：宿主给出数据、状态与能力，页面按能力绘制。
// 草稿白板、正式尝试保存和只读限制由宿主管理，不要再写第二套记录或白板存储。
const $ = QF.dom.$, on = QF.dom.on;
// 提交前 getQuestion 不含 answerSpec/analysis；标准答案只能在允许显示结果后读取。
const reply = await QF.practice.getQuestion(); if (!reply.ok) throw new Error(reply.error.message);
const question = reply.data;
// 声明题卡首次显示的宽度、对齐和留白；恢复过的草稿位置与尺寸优先。
QF.layout.configure({ cardWidth: 720, maxCardWidth: '100%', horizontalAlign: 'center', verticalAlign: 'center', padding: 20 });
// 自己绘制提交、确认、重试与反馈，隐藏对应默认 UI；仍保留默认草稿入口和悬浮工具。
// 隐藏按钮不会增加权限；confirmation:false 只省略宿主确认，不省略提交事务。
QF.ui.configure({ typeLabel: false, position: false, score: false, state: false, submit: false, retry: false, confirmation: false, note: false, sources: false, draftToggle: true, draftToolbar: true, draftZoom: true });
let confirming = false, actionBusy = false, refreshRevision = 0, answerRevision = 0, answerWrites = 0;
$('[data-type-label]').textContent = '判断题';
$('[data-choice-hint]').textContent = '请选择这句话的判断结果';
// 暂时禁用操作，避免连点；结果来自应用，不能通过页面自己修改 score/state。
async function runAction(action) {
  if (actionBusy) return; actionBusy = true;
  try { await refresh(); const response = await action(); if (!response.ok) QF.ui.notify(response.error.message); return response; }
  finally { actionBusy = false; await refresh(); }
}
// 页面中的确认面板只处理 UX；真正的校验、评分和尝试记录保存由 practice.submit 执行。
on($('[data-submit-answer]'), 'click', () => { confirming = true; $('[data-answer-confirm]').hidden = false; refresh().then(() => $('[data-confirm-answer]').focus()); });
on($('[data-cancel-answer]'), 'click', () => { confirming = false; refresh(); });
on($('[data-confirm-answer]'), 'click', () => { confirming = false; return runAction(() => QF.practice.submit()); });
// retry 保留旧尝试，开始空白作答/草稿；下一次 submit 成功才创建新的尝试记录。
on($('[data-retry-answer]'), 'click', () => runAction(() => QF.practice.retry()));
on($('[data-sources]'), 'click', event => { const button = event.target.closest('[data-source-index]'); if (button && !button.disabled) return runAction(() => QF.sources.open(Number(button.dataset.sourceIndex))); });
$('[data-prompt]').textContent = question.prompt.text;
// UI 显示固定选项，但选择保存当前题目中的选项 ID，不保存“正确/错误”字符串或 DOM。
const controls = [$('[data-answer-true]'), $('[data-answer-false]')].map((input, index) => {
  const option = question.payload.options[index], row = input.closest('label');
  input.value = option.id; input.setAttribute('aria-label', option.content.text);
  on(input, 'change', async () => {
    // Invalidate reads already in flight; never repaint an older answer over a newer click.
    ++answerRevision; ++refreshRevision; ++answerWrites;
    const selectedOptionIds = [option.id];
    row.classList.toggle('selected', input.checked);
    try {
      const response = await QF.answer.update({ selectedOptionIds });
      if (!response.ok) QF.ui.notify(response.error.message);
    } finally {
      --answerWrites;
      if (!answerWrites) await refresh();
    }
  });
  return { option, input, row, feedback: row.querySelector('.qf-option-feedback') };
});
// 从宿主重新读取权威状态。历史返回所选尝试的答案、结果；权限变化也会触发订阅。
// 文本输入题复用此流程时，需要处理输入法和未完成写入，避免异步旧状态覆盖新输入。
async function refresh() {
  const revision = ++refreshRevision, answerVersion = answerRevision;
  const [a, c, r, s, n] = await Promise.all([QF.answer.get(), QF.host.getContext(), QF.practice.getResult(), QF.practice.getState(), QF.navigation.getState()]);
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
  // 模式、权限和结果决定展示；临时禁用只影响操作，避免切题时按钮消失、题卡抖动。
  // 即使控件漏禁用，宿主仍会拒绝只读写入。
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
  // reference 是提交后公开的标准答案快照；未提交时 result 为 null，不展示正确性。
  const correct = result?.reference?.answerSpec?.correctOptionIds || result?.correctOptionIds || [];
  for (const { option, input, row, feedback } of controls) {
    // 只读/提交后仍设置 checked，禁用只是禁止改答，不能丢掉用户已选标记。
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
    // 得分和判定只使用 getResult 返回值；页面没有评分函数，也不直接写入历史。
    $('[data-verdict]').textContent = result.status === 'CORRECT' ? '回答正确' : result.status === 'INCORRECT' ? '回答错误' : '未评分';
    $('[data-verdict]').className = result.status === 'CORRECT' ? 'correct' : 'incorrect';
    $('[data-score]').textContent = '得分：' + (result.score ?? '未评分') + ' / ' + result.maxScore;
    $('[data-correct-answer]').textContent = '正确答案：' + question.payload.options.map(o => correct.includes(o.id) ? o.content.text : null).filter(Boolean).join('、');
    // 解析使用公共内容渲染器，支持 TEXT/RICH 等宿主内容结构。
    QF.content.render($('[data-analysis]'), result.reference?.analysis || result.analysis || { kind: 'TEXT', text: '' });
  }
}
// 初始化一次，随后随提交、重试、尝试查看和权限变化刷新；不自己重建整张题卡模拟轮次。
QF.host.subscribe(refresh); await refresh();
