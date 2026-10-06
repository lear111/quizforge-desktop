QF.defineQuestionType({
  type: "MULTIPLE_CHOICE",
  validate(q) {
    const errors=[];
    if(q.prompt?.kind!=='TEXT'||!(q.prompt.text||'').trim()) errors.push('请填写普通文本题干');
    const options=q.payload?.options||[],ids=new Set(options.map(o=>o.id));
    if(options.length<2||ids.size!==options.length) errors.push('至少需要两个具有独立标识的选项');
    if(options.some(o=>o.content?.kind!=='TEXT'||!(o.content.text||'').trim())) errors.push('请填写普通文本选项');
    const correct=q.answerSpec?.correctOptionIds||[];
    if(!correct.length||new Set(correct).size!==correct.length||correct.some(id=>!ids.has(id))) errors.push('请选择有效的正确答案');
    if(!Number.isFinite(q.scoreSpec?.defaultMaxScore)||q.scoreSpec.defaultMaxScore<=0) errors.push('分值必须为正数');
    return errors;
  },
  validateAnswer(q,a) {
    const ids=a.selectedOptionIds||[],available=new Set(q.payload.options.map(o=>o.id));
    if(!Array.isArray(ids)) return {empty:false,errors:['所选答案无效']};
    return {empty:ids.length===0,errors:!Array.isArray(ids)||new Set(ids).size!==ids.length||ids.some(id=>!available.has(id))?['所选答案无效']:[]};
  },
  grade(ctx) {
    const selected=new Set(ctx.answer.selectedOptionIds||[]),correct=ctx.question.answerSpec.correctOptionIds;
    const same=selected.size===correct.length&&correct.every(id=>selected.has(id));
    return ctx.reportResult({score:same?ctx.maxScore:0});
  }
});
