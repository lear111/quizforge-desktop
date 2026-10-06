// 判断题编辑页：宿主已注入 QF；HTML 负责结构，此文件负责数据与事件绑定。
// 建议与 editor.html、default.json 对照阅读。更换题型时先改数据契约，再替换字段绑定。
// QF.dom.on 注册的监听器会在页面销毁时清理，不需要自己实现 WebView 生命周期。
const $ = QF.dom.$, on = QF.dom.on;
// 编辑页可以读取完整题目，包括标准答案；练习页提交前不能读取这些保密字段。
const reply = await QF.editor.getData();
if (!reply.ok) throw new Error(reply.error.message);
let question = reply.data;
// 初始布局：编辑页顶部自然排列；已有草稿几何由宿主恢复，不被初始配置覆盖。
QF.layout.configure({ cardWidth: 720, maxCardWidth: '100%', horizontalAlign: 'center', verticalAlign: 'top', padding: 20 });
// 本模板自己绘制保存、增删与来源 UI，因此隐藏对应默认控件。上一题/下一题仍由宿主保留。
// 简单新题型可保留默认 UI，并删除下面不需要的操作区和绑定，不必复制全部管理代码。
QF.ui.configure({ title: false, save: false, position: false, typeLabel: false, add: false, duplicate: false, delete: false, sources: false });
$('[data-type-title]').textContent = '判断题编辑';
let actionBusy = false, shellRevision = 0;
let capabilities = {};
// 正式题库编辑会话提供 QF.bank。独立开发预览没有该能力，此时隐藏正式题库操作。
// response.ok 只表示接口可用；具体操作仍会经过声明权限、用户授权和宿主校验。
async function shellState() {
  const revision = ++shellRevision;
  const [response, context] = await Promise.all([QF.bank.getState(), QF.host.getContext()]);
  if (revision !== shellRevision) return;
  capabilities = context.ok ? context.data.capabilities : {};
  refreshFields();
  const available = response.ok;
  $('[data-bank-save]').hidden = !available; $('[data-bank-actions]').hidden = !available; $('[data-source-section]').hidden = !available;
  if (!available) return;
  const state = response.data;
  $('[data-bank-position]').textContent = `第 ${state.index + 1} / ${state.count} 题`;
  const select = $('[data-bank-add]'); select.replaceChildren(new Option('＋ 新增题目', ''));
  state.types.forEach(type => select.append(new Option(type.label, type.id)));
  const rows = $('[data-sources]'); rows.replaceChildren();
  state.sources.forEach((source, index) => {
    const row = document.createElement('div'); row.className = 'qf-type-source';
    // 来源文本使用 textContent；不把题库中的文字拼接成 HTML。
    const open = document.createElement('button'); open.type = 'button'; open.textContent = source.label; open.disabled = actionBusy || !capabilities.viewSources || !source.navigable; open.title = source.message; open.dataset.sourceAction = 'open'; open.dataset.sourceIndex = index;
    const message = document.createElement('span'); message.className = 'muted'; message.textContent = source.message;
    const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '移除'; remove.setAttribute('aria-label', '移除来源 ' + source.label); remove.dataset.sourceAction = 'remove'; remove.dataset.sourceIndex = index; remove.disabled = actionBusy || !capabilities.manageSources;
    row.append(open, message, remove); rows.append(row);
  });
  const granted = context.ok ? context.data.permissions.granted : [];
  for (const [selector, permission] of [['[data-bank-save]', 'bank.save'], ['[data-bank-add]', 'bank.add'], ['[data-bank-duplicate]', 'bank.duplicate'], ['[data-bank-delete]', 'bank.delete'], ['[data-delete-accept]', 'bank.delete'], ['[data-source-add]', 'sources.manage']])
    $(selector).disabled = actionBusy || !state.editable || !granted.includes(permission);
  $('[data-source-link]').disabled = actionBusy || !capabilities.manageSources;
}
// 防止重复操作；失败提示错误，finally 恢复控件并从应用读取最新状态。
async function runAction(action) {
  if (actionBusy) return; actionBusy = true;
  try { await shellState(); const response = await action(); if (!response.ok) QF.ui.notify(response.error.message); return response; }
  finally { actionBusy = false; await shellState(); }
}
// bank.save 等待当前编辑写入并保存整个 .qbank；editor.save 只是同步编辑草稿。
on($('[data-bank-save]'), 'click', () => runAction(() => QF.bank.save()));
// 刷新会重建新增下拉框，所以在进入 runAction 前记录选择的题型。
on($('[data-bank-add]'), 'change', () => { const type = $('[data-bank-add]').value; if (type) return runAction(() => QF.bank.addQuestion(type)); });
on($('[data-bank-duplicate]'), 'click', () => runAction(() => QF.bank.duplicateQuestion()));
on($('[data-bank-delete]'), 'click', () => { $('[data-delete-confirm]').hidden = false; });
on($('[data-delete-cancel]'), 'click', () => { $('[data-delete-confirm]').hidden = true; });
// 删除由宿主执行，针对当前整张题卡；页面只负责确认，不直接删除文件或数据库记录。
on($('[data-delete-accept]'), 'click', () => runAction(() => QF.bank.deleteQuestion()));
// 来源链接交给宿主解析，页面不自行请求网络或改写 sourceRefs 身份。
async function addSource() {
  const link = $('[data-source-link]').value;
  const response = await runAction(() => QF.sources.add(link));
  if (response?.ok) $('[data-source-link]').value = '';
}
on($('[data-source-add]'), 'click', addSource);
on($('[data-sources]'), 'click', event => { const button = event.target.closest('[data-source-action]'); if (button && !button.disabled) return runAction(() => button.dataset.sourceAction === 'open' ? QF.sources.open(Number(button.dataset.sourceIndex)) : QF.sources.remove(Number(button.dataset.sourceIndex))); });
on($('[data-source-link]'), 'keydown', event => { if (event.key === 'Enter') { event.preventDefault(); return addSource(); } });
// 权限或会话状态变化后刷新操作区；订阅用于 UI 更新，不等于保存。

// update 是完整 Question 的顶层合并；子对象要保留需要的全部字段。
// 错误时 question 保留上次接受的值。不能借此更改当前题目的 id/type。
// Apply a local patch immediately so the next keystroke never builds on an old reply.
let editRevision = 0;
async function update(patch) {
  const revision = ++editRevision;
  question = { ...question, ...patch };
  const response = await QF.editor.update(patch);
  if (response.ok) {
    if (revision === editRevision) question = response.data;
  } else {
    QF.ui.notify(response.error.message);
    const saved = await QF.editor.getData();
    if (revision === editRevision && saved.ok) question = saved.data;
  }
  return response;
}
function refreshFields() {
  const disabled = actionBusy || !capabilities.editQuestion;
  for (const input of QF.dom.root.querySelectorAll('textarea, input, [data-add-option]')) {
    if (!input.matches('[data-source-link]')) input.disabled = disabled;
  }
  for (const button of QF.dom.root.querySelectorAll('[data-option-remove]'))
    button.disabled = disabled || question.payload.options.length <= 2;
}
function updateScore() {
  const value = $('[data-score]').value;
  const score = Number(value);
  if (!value.trim() || !Number.isFinite(score) || score <= 0) {
    QF.ui.notify('分值必须是大于 0 的数字'); return;
  }
  return update({ scoreSpec: { ...question.scoreSpec, defaultMaxScore: score } });
}
$('[data-prompt]').value = question.prompt.text;
on($('[data-prompt]'), 'input', () => update({ prompt: { kind: 'TEXT', text: $('[data-prompt]').value } }));
$('[data-score]').value = question.scoreSpec.defaultMaxScore;
on($('[data-score]'), 'input', updateScore);
// 两个选项固定为“正确／错误”；身份从当前题目读取，新建和复制会重新分配选项 ID。
// 不要写死 default.json 的 opt_template_*，否则复制后的标准答案将引用错误身份。
const answers = [$('[data-correct-true]'), $('[data-correct-false]')];
function refreshAnswer() {
  answers.forEach((input, index) => {
    input.checked = question.answerSpec.correctOptionIds.includes(question.payload.options[index].id);
    input.closest('label').classList.toggle('selected', input.checked);
  });
}
answers.forEach((input, index) => on(input, 'change', async () => {
  // answerSpec 是标准答案；用户在练习中的选择另存于 QF.answer。
  await update({ answerSpec: { kind: 'CHOICE', correctOptionIds: [question.payload.options[index].id] } });
  refreshAnswer();
}));
refreshAnswer();
// 公共内容编辑器支持富文本；onChange 明确将内容交给 editor.update，挂载本身不会保存。
QF.content.mountEditor($('[data-analysis]'), { value: question.analysis, formatting: true, onChange: analysis => update({ analysis }) });

QF.host.subscribe(shellState); await shellState();
