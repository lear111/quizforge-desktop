import {payload,spec,groups,plain,markers,validateSelection,gradeSelection,allocateQuestion} from './legacy-data.js';
// These rules are extension code, bundled per package; there is no host type registry here.
export function defineReadingType(type){
  const objective=['CLOZE','READING','MATCHING'].includes(type);
  QF.defineQuestionType({type,allocateQuestion,
    maxScore(q){return q.scoreSpec.defaultMaxScore*(type==='ESSAY'?1:groups(q).filter(i=>!i.locked).length);},
    targets(q){return type==='ESSAY'?[{id:q.id,number:1,locked:false,gradable:true}]:groups(q).filter(i=>!i.locked).map((i,index)=>({id:i.id,number:index+1,locked:false,gradable:true}));},
    publicPayload(q){
      if(type!=='MATCHING')return q.payload;
      const data=payload(q),answers=spec(q).answers||[];
      return {kind:'EXTENSION',data:{...data,blanks:data.blanks.map(b=>({...b,...(b.locked?{givenOptionId:answers.find(a=>a.blankId===b.id)?.correctOptionId}:{} )}))}};
    },
    validate(q){
      const errors=[],data=payload(q),standard=spec(q),items=groups(q);
      if(!plain(q.prompt).trim())errors.push('请填写题干');
      if(!(q.scoreSpec.defaultMaxScore>0))errors.push('分值必须为正数');
      if(type==='ESSAY')return errors;
      if(!items.length)errors.push('至少需要一道小题');
      if(new Set(items.map(i=>i.id)).size!==items.length)errors.push('小题标识重复');
      if(items.some((i,n)=>i.number!==n+1))errors.push('小题编号需要连续');
      if(objective){
        const options=type==='MATCHING'?data.options:items.flatMap(i=>i.options);
        if(new Set(options.map(o=>o.id)).size!==options.length)errors.push('选项标识重复');
        if(type!=='MATCHING'&&options.some(o=>o.content?.kind!=='TEXT'||!o.content.text.trim()))errors.push('选项必须为非空普通文本');
        if(type!=='MATCHING'&&items.some(i=>i.options.length<2))errors.push('每道小题至少需要两个选项');
        if(standard.answers?.length!==items.length||items.some(i=>standard.answers.filter(a=>(a.blankId||a.itemId)===i.id).length!==1))errors.push('每道小题需要一个标准答案');
        if(items.some(i=>!((i.options||data.options)||[]).some(o=>o.id===standard.answers?.find(a=>(a.blankId||a.itemId)===i.id)?.correctOptionId)))errors.push('正确答案引用了无效选项');
      }
      if(type==='CLOZE'){
        const parsed=markers(plain(q.prompt),true);errors.push(...parsed.errors);
        const numbers=[...new Set(parsed.found.map(m=>m.number))].sort((a,b)=>a-b);
        if(numbers.length!==items.length||numbers.some((n,index)=>n!==index+1))errors.push('正文标记与空位须为连续的 {{1}}、{{2}}…');
      }
      if(type==='READING'&&items.some(i=>!plain(i.prompt).trim()))errors.push('请填写每道小题的题干');
      if(type==='MATCHING'){
        if(items.length!==8||data.options.length!==8||data.options.some((o,n)=>o.label!==String.fromCharCode(65+n)))errors.push('段落排序固定为 A–H 八个选项');
        if(items.filter(i=>i.locked).length!==3)errors.push('请设置三个提示位置');
        if(new Set((standard.answers||[]).map(a=>a.correctOptionId)).size!==8)errors.push('标准答案应为八个字母的完整排列');
      }
      if(type==='TRANSLATION'){
        const parsed=markers(plain(q.prompt));errors.push(...parsed.errors);
        if(parsed.found.length!==items.length||items.some((item,n)=>item.text!==parsed.found[n]?.value))errors.push('译文位置与正文标记不一致，请同步标记');
        if(standard.answers?.length!==items.length||items.some(i=>standard.answers.filter(a=>a.itemId===i.id).length!==1))errors.push('请为每句保留对应的参考译文');
      }
      return errors;
    },
    validateAnswer(q,a){
      if(objective)return validateSelection(q,a,type);
      const check=value=>value&&['TEXT','CANVAS_DOCUMENT'].includes(value.kind)&&typeof value.text==='string'&&(value.kind!=='CANVAS_DOCUMENT'||typeof value.document==='string');
      if(type==='ESSAY')return {errors:check(a)?[]:['无效的作文答案'],empty:!a.text?.trim()&&!(a.document&&a.document.includes('"type":"image"'))};
      const errors=[];for(const [id,value] of Object.entries(a))if(!groups(q).some(i=>i.id===id)||!check(value))errors.push('译文包含无效句子或答案');
      return {errors,empty:!Object.values(a).some(v=>v.text?.trim())};
    },
    grade(ctx){return objective?gradeSelection(ctx):ctx.reportResult({score:null});}
  });
}
