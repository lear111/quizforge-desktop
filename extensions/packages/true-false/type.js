// 独立、无 DOM 的规则文件。此处只有规则版 QF，不可使用页面的 editor/answer/dom 接口。
// 宿主载入 default.json，提供模板创建、复制、快照与默认单题大纲；作者只写业务规则。
QF.defineQuestionType({
  // 与 manifest.types[].id、default.json.type、题目 Schema 中 type.const 保持一致。
  type: 'TRUE_FALSE',
  // Schema 先校验结构，这里补业务约束；不能通过返回 [] 取消宿主 Schema 检查。
  validate(question) {
    const errors = [];
    if (question.prompt?.kind !== 'TEXT' || typeof question.prompt.text !== 'string' || !question.prompt.text.trim()) {
      errors.push('请填写普通文本题干');
    }
    const options = question.payload?.options;
    // 固定两项及顺序，同时保证 ID 独立；页面只改变哪个选项是标准答案。
    const validOptions = question.payload?.kind === 'CHOICE' && Array.isArray(options) && options.length === 2
      && options.every((option, index) => typeof option?.id === 'string' && option.id.startsWith('opt_')
        && option.content?.kind === 'TEXT' && option.content.text === ['正确', '错误'][index])
      && options[0].id !== options[1].id;
    if (!validOptions) errors.push('判断题需按顺序保留正确、错误两个固定选项和独立选项标识');
    const correct = question.answerSpec?.correctOptionIds;
    // 标准答案必须引用当前题目的有效选项，不能引用其他题目或默认模板的旧身份。
    if (question.answerSpec?.kind !== 'CHOICE' || !Array.isArray(correct) || correct.length !== 1
      || !validOptions || !options.some(option => option.id === correct[0])) errors.push('请选择有效的正确答案');
    if (!Number.isFinite(question.scoreSpec?.defaultMaxScore) || question.scoreSpec.defaultMaxScore <= 0) {
      errors.push('分值必须为正数');
    }
    return errors;
  },
  // 用户作答和标准答案分开校验；空数组可暂存，但 empty:true 会阻止直接提交。
  // 宿主保留的 {} 未作答状态由适配层处理，不会直接进入本函数。
  validateAnswer(question, answer) {
    const selected = answer.selectedOptionIds;
    const valid = Array.isArray(selected) && selected.length <= 1
      && selected.every(id => question.payload.options.some(option => option.id === id));
    return { empty: valid && selected.length === 0, errors: valid ? [] : ['请选择正确或错误中的一个选项'] };
  },
  // 同步评分使用应用提供的冻结题目、作答和满分，不能从页面读取 radio 或调用网络/AI。
  grade({ question, answer, maxScore, reportResult }) {
    const selected = answer.selectedOptionIds || [];
    const right = selected.length === 1 && selected[0] === question.answerSpec.correctOptionIds[0];
    // 每次上报一次，宿主验证 0～满分并在事务成功后保存；不是页面直接写入历史。
    // 规则适配层根据满分/非满分生成 CORRECT/INCORRECT，异步 Promise 评分尚未支持。
    return reportResult({ score: right ? maxScore : 0 });
  }
});
