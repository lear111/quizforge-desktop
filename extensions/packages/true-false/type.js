// 独立、无 DOM 的规则文件。此处只有规则版 QF，不可使用页面的 editor/answer/dom 接口。
// 宿主载入 default.json，提供模板创建、复制、快照与默认单题大纲；作者只写业务规则。
// 【规则入口】QF 是应用提供的接口，不是 JS 自带对象。
// defineQuestionType 接收一个对象，里面有题型标识和三个方法。
// validate(question) 这种写法是对象的方法简写；现在只登记函数，之后由应用调用。
// 本文件只处理数据，不处理 HTML；页面布局在 editor/practice 文件中。
QF.defineQuestionType({
  // 与 manifest.types[].id、default.json.type、题目 Schema 中 type.const 保持一致。
  type: 'TRUE_FALSE',
  // Schema 先校验结构，这里补业务约束；不能通过返回 [] 取消宿主 Schema 检查。
  // 【1. 校验题目】question 是传入的题目对象；点号访问其字段。
  // const 声明不能重新赋值的变量，但数组本身仍可以添加元素。
  // [] 创建数组；errors.push(...) 添加错误；返回 [] 表示没有业务校验错误。
  validate(question) {
    const errors = [];
    // if (条件) { ... } 在条件成立时执行花括号里的语句。
    // ?. 是可选链，左侧为 null/undefined 时返回 undefined；typeof 检查值的类型。
    // !== 是严格不相等，=== 是严格相等，= 才是赋值。
    // || 表示“或者”，遇到成立的条件就停止计算；因此不存在的题干不会继续访问 text。
    // ! 是取反；trim 去掉两端空白，空字符串在条件中被视为 false。
    if (question.prompt?.kind !== 'TEXT' || typeof question.prompt.text !== 'string' || !question.prompt.text.trim()) {
      errors.push('请填写普通文本题干');
    }
    const options = question.payload?.options;
    // 固定两项及顺序，同时保证 ID 独立；页面只改变哪个选项是标准答案。
    // && 表示“并且”，遇到不成立的条件就停止计算。
    // Array.isArray 检查数组，length 是长度；先确认两项存在，再访问 options[0]/[1]。
    // every 检查所有项是否满足条件，回调中的 index 从 0 开始。
    // 箭头函数 (option, index) => 表达式 返回表达式的值。
    // startsWith 检查字符串前缀；['正确', '错误'][index] 按位置取期望文字。
    const validOptions = question.payload?.kind === 'CHOICE' && Array.isArray(options) && options.length === 2
      && options.every((option, index) => typeof option?.id === 'string' && option.id.startsWith('opt_')
        && option.content?.kind === 'TEXT' && option.content.text === ['正确', '错误'][index])
      && options[0].id !== options[1].id;
    if (!validOptions) errors.push('判断题需按顺序保留正确、错误两个固定选项和独立选项标识');
    const correct = question.answerSpec?.correctOptionIds;
    // 标准答案必须引用当前题目的有效选项，不能引用其他题目或默认模板的旧身份。
    // some 检查是否至少有一项匹配；correct[0] 是标准答案数组中的第一项。
    // 必须先确认数组和选项有效，才继续查找对应 id。
    if (question.answerSpec?.kind !== 'CHOICE' || !Array.isArray(correct) || correct.length !== 1
      || !validOptions || !options.some(option => option.id === correct[0])) errors.push('请选择有效的正确答案');
    // Number.isFinite 只接受有限数字，排除字符串、NaN（不是数字）和 Infinity（无穷大）。
    // <= 表示“小于或等于”，这里拒绝 0 和负数。
    if (!Number.isFinite(question.scoreSpec?.defaultMaxScore) || question.scoreSpec.defaultMaxScore <= 0) {
      errors.push('分值必须为正数');
    }
    // return 返回校验结果并结束这个方法；应用根据错误列表决定能否接受题目。
    return errors;
  },
  // 用户作答和标准答案分开校验；空数组可暂存，但 empty:true 会阻止直接提交。
  // 宿主保留的 {} 未作答状态由适配层处理，不会直接进入本函数。
  // 【2. 校验用户作答】question 与 answer 是两个独立参数。
  // 用户作答 selectedOptionIds 只能为空数组或包含一个当前题目的选项 id。
  // every 对空数组返回 true，因此空数组可以暂存；提交时还会检查 empty。
  validateAnswer(question, answer) {
    const selected = answer.selectedOptionIds;
    const valid = Array.isArray(selected) && selected.length <= 1
      && selected.every(id => question.payload.options.some(option => option.id === id));
    // 返回对象中的 empty 表示未作答；errors 表示错误列表。
    // 条件 ? 值1 : 值2 是三元表达式：合法返回 []，不合法返回包含提示的数组。
    return { empty: valid && selected.length === 0, errors: valid ? [] : ['请选择正确或错误中的一个选项'] };
  },
  // 同步评分使用应用提供的冻结题目、作答和满分，不能从页面读取 radio 或调用网络/AI。
  // 【3. 评分】这里使用对象解构参数：从传入的一个对象中取出四个字段。
  // 等价于先接收 context，再分别读取 context.question、context.answer 等。
  // reportResult 是应用提供的函数，调用它上报分数，不是拓展直接写数据库。
  grade({ question, answer, maxScore, reportResult }) {
    // || 在左侧为假值时取右侧；缺少选择列表时使用空数组。
    // right 是布尔值，只有选中一项且其 id 与标准答案一致时才为 true。
    const selected = answer.selectedOptionIds || [];
    const right = selected.length === 1 && selected[0] === question.answerSpec.correctOptionIds[0];
    // 每次上报一次，宿主验证 0～满分并在事务成功后保存；不是页面直接写入历史。
    // 规则适配层根据满分/非满分生成 CORRECT/INCORRECT，异步 Promise 评分尚未支持。
    // {score: ...} 是分数对象；判断正确得满分，否则得 0 分。
    // return 返回 reportResult 的执行结果；应用校验分数后保存并汇总到得分卡。
    return reportResult({ score: right ? maxScore : 0 });
  }
});
