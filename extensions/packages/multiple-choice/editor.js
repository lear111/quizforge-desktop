const multiple = true;
const $ = QF.dom.$, on = QF.dom.on;
const reply = await QF.editor.getData();
if (!reply.ok) throw new Error(reply.error.message);
let question = reply.data;
QF.layout.configure({cardWidth:720,maxCardWidth:'100%',horizontalAlign:'center',verticalAlign:'top',padding:20});
QF.ui.configure({ title: false, save: false, position: false, typeLabel: false, add: false, duplicate: false, delete: false, sources: false });
$('[data-type-title]').textContent = multiple ? '多选题编辑' : '单选题编辑';
$('[data-options-hint]').textContent = multiple ? '勾选所有正确答案；题干与选项使用普通文本。' : '点击圆圈设置正确答案；题干与选项使用普通文本。';
let actionBusy = false, shellRevision = 0;
let capabilities = {};
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
async function runAction(action) {
  if (actionBusy) return; actionBusy = true;
  try { await shellState(); const response = await action(); if (!response.ok) QF.ui.notify(response.error.message); return response; }
  finally { actionBusy = false; await shellState(); }
}
on($('[data-bank-save]'), 'click', () => runAction(() => QF.bank.save()));
on($('[data-bank-add]'), 'change', () => { const type = $('[data-bank-add]').value; if (type) return runAction(() => QF.bank.addQuestion(type)); });
on($('[data-bank-duplicate]'), 'click', () => runAction(() => QF.bank.duplicateQuestion()));
on($('[data-bank-delete]'), 'click', () => { $('[data-delete-confirm]').hidden = false; });
on($('[data-delete-cancel]'), 'click', () => { $('[data-delete-confirm]').hidden = true; });
on($('[data-delete-accept]'), 'click', () => runAction(() => QF.bank.deleteQuestion()));
async function addSource() {
  const link = $('[data-source-link]').value;
  const response = await runAction(() => QF.sources.add(link));
  if (response?.ok) $('[data-source-link]').value = '';
}
on($('[data-source-add]'), 'click', addSource);
on($('[data-sources]'), 'click', event => { const button = event.target.closest('[data-source-action]'); if (button && !button.disabled) return runAction(() => button.dataset.sourceAction === 'open' ? QF.sources.open(Number(button.dataset.sourceIndex)) : QF.sources.remove(Number(button.dataset.sourceIndex))); });
on($('[data-source-link]'), 'keydown', event => { if (event.key === 'Enter') { event.preventDefault(); return addSource(); } });

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
function refreshCorrect() {
  question.payload.options.forEach((option, index) => {
    const row = $('[data-options]').children[index];
    const input = row.querySelector('input');
    input.checked = question.answerSpec.correctOptionIds.includes(option.id);
    row.classList.toggle('is-correct', input.checked);
  });
}
function options() {
  const box = $('[data-options]'); box.replaceChildren();
  question.payload.options.forEach((option, index) => {
    const row = document.createElement('div'); row.className = 'qf-choice-edit-row';
    const correct = document.createElement('input'); correct.type = multiple ? 'checkbox' : 'radio'; correct.name = 'qf-correct-' + question.id; correct.checked = question.answerSpec.correctOptionIds.includes(option.id); correct.title = '设为正确答案';
    row.classList.toggle('is-correct',correct.checked);
    const letter = document.createElement('span'); letter.className='qf-option-letter';letter.textContent = String.fromCharCode(65 + index) + '.';
    correct.setAttribute('aria-label',letter.textContent+' 正确答案');
    const text = document.createElement('textarea'); text.rows = 1; text.value = option.content.text; text.setAttribute('aria-label', letter.textContent + ' 选项');
    const remove = document.createElement('button'); remove.type = 'button'; remove.dataset.optionRemove = ''; remove.textContent = '删除'; remove.disabled = question.payload.options.length <= 2;
    row.append(correct, letter, text, remove); box.append(row);
    on(text, 'input', () => { const next = question.payload.options.map(o => o.id === option.id ? { ...o, content: { kind: 'TEXT', text: text.value } } : o); return update({ payload: { kind: 'CHOICE', options: next } }); });
    on(correct, 'change', async () => { const ids = new Set(question.answerSpec.correctOptionIds); if (multiple) { correct.checked ? ids.add(option.id) : ids.delete(option.id); } else { ids.clear(); ids.add(option.id); } await update({ answerSpec: { kind: 'CHOICE', correctOptionIds: [...ids] } }); refreshCorrect(); });
    on(remove, 'click', async () => { await update({ payload: { kind: 'CHOICE', options: question.payload.options.filter(o => o.id !== option.id) }, answerSpec: { kind: 'CHOICE', correctOptionIds: question.answerSpec.correctOptionIds.filter(id => id !== option.id) } }); options(); });
  });
  refreshFields();
}
options();
on($('[data-add-option]'), 'click', async () => { await update({ payload: { kind: 'CHOICE', options: [...question.payload.options, { id: QF.ids.create('opt_'), content: { kind: 'TEXT', text: '' } }] } }); options(); });
QF.content.mountEditor($('[data-analysis]'), { value: question.analysis, formatting: true, onChange: analysis => update({ analysis }) });

QF.host.subscribe(shellState); await shellState();
