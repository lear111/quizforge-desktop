// 练习、草稿、历史、预览共用此脚本；写入能力由宿主上下文决定。
// 【1. 接收题目和作答状态】
// QF 是应用注入的接口对象。$ 查找 HTML 元素，on 绑定事件；它们不是 JS 内置函数。
// const 不能重新赋值；let 可以。详细的基础语法说明也可参照 editor.js。
// $('[data-prompt]') 找到 HTML 中带 data-prompt 属性的元素。
const $ = QF.dom.$, on = QF.dom.on;
// question 是作者的题目，answer 是用户作答，评分 result 则由应用返回。
// {} 是空对象，[] 是空数组。acceptedAnswer 记录最后保存成功的作答。
// writes 记录未完成的草稿写入数量，避免上下文刷新把新选择覆盖。
let context, question, answer = {}, acceptedAnswer = {}, writes = 0;
// initialized：是否已初始化；actionBusy：操作是否进行中；confirming：是否正在确认提交。
// controls 稍后存放两组选项的 DOM 元素和数据，便于统一更新。
let initialized = false, actionBusy = false, confirming = false, controls = [];
// await 等待异步注册完成；onLoad(next) 由应用在加载/更新上下文时调用。
// 回调是交给应用以后调用的函数，不是现在直接执行它。
await QF.page.register({
  onLoad(next) {
    context = next; question = next.question.data;
    // !writes 在数量为 0 时成立。?. 在 attempt 为空时安全返回 undefined。
    // || 取左侧为真值时的左侧值，否则取右侧；没有作答就使用 {}。
    // 连续赋值让 answer 和 acceptedAnswer 暂时指向同一份作答，选择后会换成新对象。
    if (!writes) answer = acceptedAnswer = next.attempt?.answer || {};
    if (initialized) refresh();
  },
  // () => ({...}) 是返回对象的箭头函数；SDK 已跟踪写入，这里无额外待保存内容。
  onBeforeLeave: () => ({ok: true, data: {pendingSave: null}})
});
// 设置练习页初始布局：宽 720，水平与垂直居中，留白 20。
QF.page.configure({initialLayout: {cardWidth: 720, maxCardWidth: '100%', horizontalAlign: 'center', verticalAlign: 'center', padding: 20}});
// 关闭这里列出的默认 UI，使用 practice.html 中自定义的标签、按钮和反馈区。
QF.ui.configure({typeLabel: false, position: false, score: false, state: false, submit: false, retry: false, confirmation: false, note: false, sources: false});
// textContent 设置普通文字，不把题干当 HTML 执行。
$('[data-type-label]').textContent = '判断题';
$('[data-choice-hint]').textContent = '请选择这句话的判断结果';
$('[data-prompt]').textContent = question.prompt.text;

// 【2. 提交、重试和来源按钮】
// async 函数可以使用 await；action 是传入的函数，action() 才调用它。
// try 执行操作；finally 在成功、失败或 return 后都执行，保证恢复按钮状态。
async function runAction(action) {
  if (actionBusy) return;
  actionBusy = true; refresh();
  try {
    const reply = await action();
    if (!reply.ok) QF.ui.notify(reply.error.message);
    return reply;
  } finally { actionBusy = false; refresh(); }
}
// on(元素, 'click', 回调) 表示点击时执行回调。
// 提交先显示确认区；focus 让确认按钮获得键盘焦点，取消则恢复。
on($('[data-submit-answer]'), 'click', () => { confirming = true; refresh(); $('[data-confirm-answer]').focus(); });
on($('[data-cancel-answer]'), 'click', () => { confirming = false; refresh(); });
on($('[data-confirm-answer]'), 'click', () => {
  confirming = false;
  // submit 正式提交当前作答，应用负责调用评分规则并保存此次尝试。
  // {answer} 是对象字段简写，等价于 {answer: answer}。
  return runAction(() => QF.save({purpose: 'submit', data: {answer}}));
});
// 重试由应用建立新的空白作答，已提交的尝试仍保存在历史中。
// 页面发送 retry 请求，不自行清空历史或直接写数据库。
on($('[data-retry-answer]'), 'click', () => runAction(() => QF.requestAction({action: 'retry'})));
// 监听来源列表父元素，用事件委托处理动态新增的按钮。
// target 是实际被点击的元素，closest 向上找来源按钮；Number 将属性值转为数字。
on($('[data-sources]'), 'click', event => {
  const button = event.target.closest('[data-source-index]');
  if (button && !button.disabled) return runAction(() => QF.requestAction({action: 'openSource', params: {index: Number(button.dataset.sourceIndex)}}));
});

// 【3. 绑定正确/错误选项，并自动保存草稿】
// map 对每项执行回调并组成新数组；forEach 只遍历，不用它的返回值生成数组。
// input 是当前单选框，index 是从 0 开始的位置。此处两项顺序与题目数据一致。
controls = [$('[data-answer-true]'), $('[data-answer-false]')].map((input, index) => {
  const option = question.payload.options[index], row = input.closest('label');
  // value 存选项 id；closest 找到外层 label；aria-label 为辅助阅读工具提供说明。
  input.value = option.id; input.setAttribute('aria-label', option.content.text);
  // 选择改变时执行回调。函数保留了创建它时的 option/input/row，这称为闭包。
  on(input, 'change', async () => {
    // 本次作答是一个对象，里面的 selectedOptionIds 是只含一个选项 id 的数组。
    const candidate = {selectedOptionIds: [option.id]};
    // ++ 加 1，-- 减 1；先立即显示选择，再异步保存。
    answer = candidate; writes++;
    row.classList.toggle('selected', input.checked);
    try {
      // draft 暂存用户作答，不触发正式评分；提交按钮使用 submit。
      // 接口成功才更新 acceptedAnswer，失败则显示错误提示。
      const reply = await QF.save({purpose: 'draft', data: {answer: candidate}});
      if (reply.ok) acceptedAnswer = candidate;
      else QF.ui.notify(reply.error.message);
    } finally {
      writes--;
      // 所有待保存写入结束后，按应用已接受的作答刷新；失败时不保留未保存的选择。
      if (!writes) { answer = acceptedAnswer; refresh(); }
    }
  });
  // map 回调返回每组选项的对象；{option} 等价于 {option: option}。
  // querySelector 在这一行中查找反馈元素，CSS 的 . 表示类名。
  return {option, input, row, feedback: row.querySelector('.qf-option-feedback')};
});

// 【4. 根据上下文更新选中状态、权限和评分】
// refresh 更新已有 DOM 元素，不是重新加载网页。
// permissions 是当前状态允许的操作；grantedPermissions 是已授权的接口权限名称。
function refresh() {
  const permissions = context.permissions, granted = context.grantedPermissions;
  // result 是评分结果，提交前可能不存在。
  // new Set(数组) 创建去重集合；has(id) 检查成员，size 是集合中元素数量。
  const result = context.attempt?.result, selected = new Set(answer.selectedOptionIds || []);
  // === 是严格相等；|| 表示“或者”。历史和预览都是只读。
  const readOnly = context.mode === 'history' || context.mode === 'preview';
  const navigation = context.navigation;
  $('[data-question-position]').textContent = '第 ' + (navigation.index + 1) + ' / ' + navigation.total + ' 题';
  // ?? 只在左边为 null/undefined 时使用右边。与 || 不同，它保留 0 等有效值。
  // 下一行把状态名称当对象的键，通过 [状态] 查到中文提示；|| '' 为未知状态留空。
  $('[data-max-score]').textContent = '分值：' + (context.question.maxScore ?? '未设置');
  $('[data-answer-state]').textContent = ({unanswered: '未作答', draft: '已选择', submitted: '已提交', retrying: '重试中', revising: '修订中'})[context.attempt?.status] || '';
  if (!permissions.submit || result || readOnly) confirming = false;
  // hidden = true 隐藏控件；disabled = true 禁用控件。
  // ! 是取反，&& 表示“并且”；Boolean(result) 将是否有结果转为布尔值。
  $('[data-answer-confirm]').hidden = !confirming;
  // 暂时失去交互能力时只禁用按钮，不改变题卡布局。
  $('[data-submit-answer]').hidden = readOnly || !granted.includes('practice.submit') || Boolean(result);
  $('[data-submit-answer]').disabled = actionBusy || confirming || !permissions.submit || !selected.size;
  $('[data-confirm-answer]').disabled = actionBusy || !permissions.submit || !selected.size;
  $('[data-retry-answer]').hidden = readOnly || !granted.includes('practice.retry') || !result;
  $('[data-retry-answer]').disabled = actionBusy || !permissions.retry;
  const rows = $('[data-sources]'); rows.replaceChildren();
  $('[data-source-section]').hidden = !context.sources.length;
  // forEach 遍历来源；createElement 创建元素，append 再将它放到页面。
  // dataset.sourceIndex 对应 HTML 的 data-source-index，属性值会保存为字符串。
  context.sources.forEach((source, index) => {
    const row = document.createElement('div'); row.className = 'qf-type-source';
    const button = document.createElement('button'); button.type = 'button'; button.textContent = source.label;
    button.disabled = actionBusy || !permissions.viewSources || !source.navigable; button.dataset.sourceIndex = index;
    const message = document.createElement('span'); message.className = 'muted'; message.textContent = source.message;
    row.append(button, message); rows.append(row);
  });
  // 条件 ? 值1 : 值2 是三元表达式；这里连续判断历史、预览、普通练习三种提示。
  $('[data-stage-note]').textContent = context.mode === 'history' ? '历史草稿 · 只读' : context.mode === 'preview' ? '只读预览' : '草稿自动保存；提交后冻结，重试从空白草稿开始。';
  // 优先从提交结果的题目快照取标准答案，再兼容结果直接提供的答案。
  // 连续 ?. 允许中间字段不存在；没有结果时使用空数组。
  const correct = result?.reference?.answerSpec?.correctOptionIds || result?.correctOptionIds || [];
  // for...of 遍历 controls；{option, input, row, feedback} 是对象解构，取出四个字段。
  for (const {option, input, row, feedback} of controls) {
    // checked 控制原生单选框的选中点；写入过程中不被旧上下文覆盖。
    if (!writes) input.checked = selected.has(option.id);
    input.disabled = actionBusy || confirming || readOnly || !permissions.writeAnswer;
    row.classList.toggle('selected', input.checked);
    // toggle(类名, 布尔值) 根据 true/false 添加或移除类，类对应 style.css 中的样式。
    // 选中、正确、错误分别设置，不会因为提交后变成只读就清掉选中点。
    // && 会短路：前面的条件不成立，后面的条件就不用再计算。
    row.classList.toggle('correct', Boolean(result) && correct.includes(option.id));
    row.classList.toggle('incorrect', Boolean(result) && input.checked && !correct.includes(option.id));
    feedback.hidden = !result || (!input.checked && !correct.includes(option.id));
    feedback.textContent = correct.includes(option.id) ? (input.checked ? '正确 · 已选' : '正确答案') : '你的答案';
  }
  // 【5. 显示评分与解析】
  // 有结果才展示反馈；页面负责渲染，评分逻辑在 type.js 中。
  $('[data-result]').hidden = !result;
  if (result) {
    // ({CORRECT: ...})[result.status] 是用状态作为键查找文字。
    // 下面的三元表达式选 CSS 类；得分使用 ??，确保 0 分显示为 0 而不是“未评分”。
    $('[data-verdict]').textContent = ({CORRECT: '回答正确', INCORRECT: '回答错误', UNSCORED: '未评分'})[result.status] || '未评分';
    $('[data-verdict]').className = result.status === 'CORRECT' ? 'correct' : result.status === 'INCORRECT' ? 'incorrect' : 'muted';
    $('[data-score]').textContent = '得分：' + (result.score ?? '未评分') + ' / ' + result.maxScore;
    // filter 保留正确选项，map 取显示文字，join('、') 将数组连接成一句话。
    // o => ... 是简短箭头函数，o 只是当前选项的变量名。
    $('[data-correct-answer]').textContent = '正确答案：' + question.payload.options.filter(o => correct.includes(o.id)).map(o => o.content.text).join('、');
    // 公共内容接口显示提交时的解析快照；没有内容时传入空普通文本。
    QF.content.render($('[data-analysis]'), result.reference?.analysis || result.analysis || {kind: 'TEXT', text: ''});
  }
}
// 全部事件与控件准备好后完成初始化，后续 onLoad 可以安全刷新。
initialized = true; refresh();
