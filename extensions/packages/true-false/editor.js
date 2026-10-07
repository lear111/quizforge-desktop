// 编辑页直接使用公开接口，无需编译或包内客户端库。
// 【1. 常用语法与页面状态】
// QF 是 QuizForge 应用提供的接口对象，不是 JavaScript 自带的功能。
// DOM 是 HTML 元素组成的树；下面通过它读写输入框、修改文字、监听点击。
// const 声明不能重新赋值的变量。这里给两个常用函数取短名称。
// $ 是合法的 JS 变量名，此处指 QF.dom.$，不是 jQuery。
// $('[data-prompt]') 用 CSS 选择器找到 HTML 中带 data-prompt 属性的元素。
// on(元素, 事件名, 函数) 注册监听器，事件发生时才执行传入的函数。
const $ = QF.dom.$, on = QF.dom.on;
// value => 表达式 是箭头函数，等价于 function(value) { return 表达式; }。
// stringify 把对象转成 JSON 字符串，parse 再转回对象，得到独立的 JSON 数据副本。
// 这种复制适合题目数据，不适合含函数、Date 等特殊值的任意 JS 对象。
const copy = value => JSON.parse(JSON.stringify(value));
// let 声明可以重新赋值的变量；没有初值的变量暂时是 undefined。
// context：宿主上下文；question：当前修改内容；acceptedQuestion：最近保存成功的内容。
// edits：未完成的写入数量；actionBusy：是否正在操作；initialized：页面是否已初始化。
// false/true 是布尔值，表示否/是；分号结束一条语句。
let context, question, acceptedQuestion, edits = 0, actionBusy = false, initialized = false;

// 【2. 接收应用传来的题目】
// await 等待异步结果，等待期间其他事件仍能运行，不会堵住整个页面。
// register 接收一个对象；onLoad(next) 是对象中的方法，由应用加载/更新数据时调用。
// 花括号既可表示对象，也可表示代码块，要结合所在位置判断。
await QF.page.register({
  onLoad(next) {
    context = next;
    // ! 是取反；edits 为 0 时 !edits 为 true。写入期间不覆盖本地编辑。
    // 点号访问对象字段，如 next.question.data。
    // 连续赋值让两个变量暂时指向同一份副本；update 后会创建新的 question。
    if (!edits) question = acceptedQuestion = copy(next.question.data);
    if (initialized) refresh();
  },
  // 输入事件立即保存；SDK 等待这些写入完成后才允许切题或正式保存。
  // () => ({...}) 返回对象；外面的圆括号避免把花括号误认为函数代码块。
  // null 表示明确的空值；此处没有额外待保存数据，写入已交给 SDK 跟踪。
  onBeforeLeave: () => ({ ok: true, data: { pendingSave: null } })
});
// 配置初始布局：宽 720、最大宽度 100%、水平居中、垂直靠上、留白 20。
// 此处只设置初始布局；用户后续移动题卡的行为由应用管理。
QF.page.configure({ initialLayout: { cardWidth: 720, maxCardWidth: '100%', horizontalAlign: 'center', verticalAlign: 'top', padding: 20 } });
// 关闭这里列出的默认 UI，因为本拓展已在 editor.html 中提供自己的对应控件。
QF.ui.configure({ title: false, save: false, position: false, typeLabel: false, add: false, duplicate: false, delete: false, sources: false });

// 标准答案属于 question；不要把用户作答存入这里，也不要改题目 id/type。
// 【3. 保存编辑内容】
// async function 声明异步函数，内部可使用 await；调用后得到 Promise（未来的结果）。
// patch 是本次局部修改，例如 {prompt: {kind: 'TEXT', text: '新的题干'}}。
async function update(patch) {
  // ... 是展开语法：复制旧字段，再用 patch 的同名字段覆盖。这里只合并最外层。
  // 因此修改 scoreSpec 内的某个字段时，后文还要展开原来的 scoreSpec。
  question = { ...question, ...patch };
  // 复制本次要提交的数据，避免等待接口时，后续编辑改变这次提交的内容。
  const candidate = copy(question);
  // ++ 加 1；finally 中的 -- 减 1，用于记录尚未完成的写入数量。
  edits++;
  try {
    // purpose 区分保存用途；editDraft 暂存编辑草稿，正式保存按钮使用 edit。
    // {键: 值} 是对象；接口回复 reply.ok 表示是否成功。
    const reply = await QF.save({ purpose: 'editDraft', data: { questionData: candidate } });
    if (reply.ok) acceptedQuestion = candidate;
    else {
      // === 是严格相等比较；= 是赋值，两者不同。失败且只剩当前写入时回到已接受内容。
      if (edits === 1) question = copy(acceptedQuestion);
      QF.ui.notify(reply.error.message);
    }
    return reply;
  // try 内执行操作；finally 无论成功、失败、return 或异常都会执行，保证计数恢复。
  } finally { edits--; }
}
// action 是作为参数传入的函数；action 是函数本身，action() 才是调用它。
// runAction 统一管理按钮的忙碌状态，避免重复操作。
async function runAction(action) {
  // return 提前结束函数；不写返回值时返回 undefined。
  if (actionBusy) return;
  actionBusy = true; refresh();
  try {
    const reply = await action();
    if (!reply.ok) QF.ui.notify(reply.error.message);
    return reply;
  // 即使操作失败也恢复按钮状态；refresh 更新 DOM，不是重新加载网页。
  } finally { actionBusy = false; refresh(); }
}
// params = {} 是默认参数，不传时使用空对象。
// {action: name, params} 中 params 是简写，等价于 params: params。
const action = (name, params = {}) => QF.requestAction({ action: name, params });

// 【4. 根据权限和上下文更新 UI】
// permissions 表示当前状态允许的操作；grantedPermissions 是已授权的接口权限名称列表。
// 页面禁用按钮便于提示用户；真正的权限校验仍由应用执行。
function refresh() {
  const permissions = context.permissions, granted = context.grantedPermissions;
  // Boolean(...) 把值转成 true/false。hidden = true 隐藏元素，disabled = true 禁用控件。
  const available = Boolean(permissions.manageQuestions);
  $('[data-bank-save]').hidden = $('[data-bank-actions]').hidden = $('[data-source-section]').hidden = !available;
  // + 在这里拼接文字；index 从 0 开始，显示题号时加 1。
  // textContent 设置普通文字，不把内容当 HTML 执行。
  $('[data-bank-position]').textContent = '第 ' + (context.navigation.index + 1) + ' / ' + context.navigation.total + ' 题';
  const select = $('[data-bank-add]');
  // replaceChildren 替换已有子元素；new Option(显示文字, 值) 创建下拉选项。
  select.replaceChildren(new Option('＋ 新增题目', ''));
  // forEach 对数组每一项执行回调；type 是当前处理的题型，append 将元素加进父元素。
  context.types.forEach(type => select.append(new Option(type.label, type.id)));
  // for...of 遍历列表；[selector, permission] 是数组解构，取出每项的两个值。
  // 这里循环没有花括号，紧跟的下一条语句就是循环体。
  // || 表示“或者”，includes 判断数组是否包含指定值。
  for (const [selector, permission] of [['[data-bank-save]', 'bank.save'], ['[data-bank-add]', 'bank.add'], ['[data-bank-duplicate]', 'bank.duplicate'], ['[data-bank-delete]', 'bank.delete'], ['[data-delete-accept]', 'bank.delete'], ['[data-source-add]', 'sources.manage']])
    $(selector).disabled = actionBusy || !available || !granted.includes(permission);
  // querySelectorAll 找出全部匹配元素；选择器中的逗号表示 textarea 或 input。
  for (const input of QF.dom.root.querySelectorAll('textarea,input'))
    input.disabled = actionBusy || !permissions.editQuestion;
  $('[data-source-link]').disabled = actionBusy || !permissions.manageSources;
  const rows = $('[data-sources]'); rows.replaceChildren();
  // source 是当前来源，index 是从 0 开始的位置。
  // createElement 创建元素；className 设置 CSS 类；元素要 append 后才放进页面。
  // dataset.sourceIndex 对应 HTML 的 data-source-index；属性中的值会保存为字符串。
  // setAttribute 设置 HTML 属性；aria-label 给辅助阅读工具提供控件说明。
  context.sources.forEach((source, index) => {
    const row = document.createElement('div'); row.className = 'qf-type-source';
    const open = document.createElement('button'); open.type = 'button'; open.textContent = source.label;
    open.disabled = actionBusy || !permissions.viewSources || !source.navigable; open.title = source.message;
    open.dataset.sourceAction = 'open'; open.dataset.sourceIndex = index;
    const message = document.createElement('span'); message.className = 'muted'; message.textContent = source.message;
    const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '移除';
    remove.disabled = actionBusy || !permissions.manageSources; remove.setAttribute('aria-label', '移除来源 ' + source.label);
    remove.dataset.sourceAction = 'remove'; remove.dataset.sourceIndex = index;
    row.append(open, message, remove); rows.append(row);
  });
}

// 页面绘制确认与管理 UI，实际保存、增删、来源解析由应用执行。
// 【5. 注册管理按钮事件】
// 传入 () => ... 是登记“点击后做什么”，不是现在就执行保存。
// 两层箭头函数：外层是点击回调，内层是交给 runAction 执行的具体操作。
on($('[data-bank-save]'), 'click', () => runAction(() => QF.save({ purpose: 'edit', data: { questionData: copy(question) } })));
// change 在选择改变时触发；value 是下拉框的值；{type} 等价于 {type: type}。
on($('[data-bank-add]'), 'change', () => { const type = $('[data-bank-add]').value; if (type) return runAction(() => action('addQuestion', { type })); });
on($('[data-bank-duplicate]'), 'click', () => runAction(() => action('duplicateQuestion')));
// 删除先显示确认区，取消隐藏确认区，确认后才真正请求删除。
on($('[data-bank-delete]'), 'click', () => { $('[data-delete-confirm]').hidden = false; });
on($('[data-delete-cancel]'), 'click', () => { $('[data-delete-confirm]').hidden = true; });
on($('[data-delete-accept]'), 'click', () => runAction(() => action('deleteQuestion')));
async function addSource() {
  const link = $('[data-source-link]').value;
  const reply = await runAction(() => action('addSource', { link }));
  // ?. 是可选链：reply 为 null/undefined 时返回 undefined，不继续访问 ok 而报错。
  if (reply?.ok) $('[data-source-link]').value = '';
}
// 直接传 addSource 函数；若写 addSource() 就会立即执行，而不是登记回调。
on($('[data-source-add]'), 'click', addSource);
// event 是浏览器传入的事件对象；key 是按键名；preventDefault 阻止按键默认行为。
on($('[data-source-link]'), 'keydown', event => { if (event.key === 'Enter') { event.preventDefault(); return addSource(); } });
// 事件委托：监听父元素，动态新增的来源按钮也由同一个监听器处理。
// event.target 是实际点击的元素；closest 从它开始向上找匹配元素。
// && 表示“并且”，左侧不成立时不计算右侧，防止 button 为空时访问属性。
// 条件 ? 值1 : 值2 是三元表达式；Number(...) 将属性字符串转回数字。
on($('[data-sources]'), 'click', event => {
  const button = event.target.closest('[data-source-action]');
  if (button && !button.disabled) return runAction(() => action(button.dataset.sourceAction === 'open' ? 'openSource' : 'removeSource', { index: Number(button.dataset.sourceIndex) }));
});

// 【6. 绑定题干、分值、标准答案与解析】
// value 读写输入框内容；input 事件在用户输入时触发。
$('[data-type-title]').textContent = '判断题编辑';
$('[data-prompt]').value = question.prompt.text;
on($('[data-prompt]'), 'input', () => update({ prompt: { kind: 'TEXT', text: $('[data-prompt]').value } }));
$('[data-score]').value = question.scoreSpec.defaultMaxScore;
on($('[data-score]'), 'input', () => {
  // 即使是数字输入框，value 仍是字符串；Number 把它转成数字。
  // trim 去掉两端空白；Number.isFinite 排除 NaN（不是数字）和 Infinity（无穷大）。
  const value = $('[data-score]').value, score = Number(value);
  if (!value.trim() || !Number.isFinite(score) || score <= 0) { QF.ui.notify('分值必须是大于 0 的数字'); return; }
  return update({ scoreSpec: { ...question.scoreSpec, defaultMaxScore: score } });
});
// [] 创建数组，下标从 0 开始：第 0 项为正确，第 1 项为错误。
// 标准答案存的是选项 id，不是“正确/错误”的显示文字。
const answers = [$('[data-correct-true]'), $('[data-correct-false]')];
function refreshAnswer() {
  answers.forEach((input, index) => {
    // checked 为 true 时原生单选框选中；includes 检查该 id 是否属于标准答案。
    // classList.toggle(类名, 布尔值) 根据 true/false 添加或移除 CSS 类，切换选中样式。
    input.checked = question.answerSpec.correctOptionIds.includes(question.payload.options[index].id);
    input.closest('label').classList.toggle('selected', input.checked);
  });
}
// async 回调先等待标准答案保存，再刷新选中状态。
answers.forEach((input, index) => on(input, 'change', async () => {
  await update({ answerSpec: { kind: 'CHOICE', correctOptionIds: [question.payload.options[index].id] } });
  refreshAnswer();
}));
refreshAnswer();
// 挂载应用提供的富文本编辑器；onChange 接收编辑后的解析。
// analysis => update({analysis}) 把新解析交给保存函数；{analysis} 是同名字段简写。
QF.content.mountEditor($('[data-analysis]'), { value: question.analysis, formatting: true, onChange: analysis => update({ analysis }) });
// 至此控件与监听器准备好；后续 onLoad 可以安全调用 refresh。
initialized = true; refresh();
