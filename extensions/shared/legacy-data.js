// Offline extension helpers. This module is bundled into each independent package.
export const clone=value=>JSON.parse(JSON.stringify(value));
export const textContent=text=>({kind:'TEXT',text});
export const payload=q=>q.payload.data||q.payload;
export const spec=q=>q.answerSpec?.data||q.answerSpec||{};
export const groups=q=>payload(q).blanks||payload(q).items||[];
export function plain(content){
  if(!content)return '';
  if(typeof content.text==='string')return content.text;
  const walk=node=>{
    if(Array.isArray(node))return node.map(walk).join('');
    if(!node||typeof node!=='object')return '';
    if(node.type==='image'||node.type==='IMAGE')return '';
    if(typeof node.text==='string')return node.text;
    if(typeof node.value==='string')return node.value.replace(/\u200b/g,'');
    return walk(node.children||node.valueList||node.blocks||node.items||node.trList||node.tdList||node.value||[]);
  };
  return content.kind==='RICH'?content.document.blocks.map(walk).join('\n'):walk(content.document?.data?.main||[]);
}
/** Linear scan: escapes are literal; malformed/nested markers never become answer targets. */
export function markers(text,numeric=false){
  const found=[],errors=[];
  for(let i=0;i<text.length;i++){
    if(text[i]==='\\'){
      if(text.slice(i+1,i+3)==='{{'){const end=text.indexOf('}}',i+3);i=end<0?text.length:end+1;}
      else i++;
      continue;
    }
    if(text.slice(i,i+2)==='}}'){errors.push('存在多余的结束标记');i++;continue;}
    if(text.slice(i,i+2)!=='{{')continue;
    const start=i;let end=i+2;
    while(end<text.length&&text.slice(end,end+2)!=='}}')end++;
    if(end===text.length){errors.push('标记缺少 }}');break;}
    const value=text.slice(start+2,end);
    if(value.includes('{{')||!value.trim()||(numeric&&!/^[1-9][0-9]*$/.test(value)))errors.push('标记内容无效');
    else found.push({start,end:end+2,value,number:numeric?Number(value):found.length+1});
    i=end+1;
  }
  return {found,errors};
}
// The package owns its identities and references. The host supplies fresh IDs only.
export function allocateQuestion(source,ids){
  const q=clone(source),mapping=new Map([[q.id,ids.question]]),counters={opt:0,blank:0,item:0};q.id=ids.question;
  function walk(value){
    if(Array.isArray(value)){value.forEach(walk);return;}
    if(!value||typeof value!=='object'||['TEXT','RICH','DOCUMENT'].includes(value.kind))return;
    const prefix=typeof value.id==='string'?Object.keys(counters).find(p=>value.id.startsWith(p+'_')):null;
    if(prefix){const n=counters[prefix]++,fresh=ids[prefix+'s']?.[n]||`${prefix}_${ids.question}_${n+1}`;mapping.set(value.id,fresh);value.id=fresh;}
    Object.values(value).forEach(walk);
  }
  function references(value){
    if(typeof value==='string')return mapping.get(value)||value;
    if(Array.isArray(value))return value.map(references);
    if(!value||typeof value!=='object'||['TEXT','RICH','DOCUMENT'].includes(value.kind))return value;
    return Object.fromEntries(Object.entries(value).map(([key,v])=>[key,references(v)]));
  }
  walk(q.payload);q.payload=references(q.payload);q.answerSpec=references(q.answerSpec);return q;
}
export function synchronizeTranslations(q,prompt,newId){
  const parsed=markers(plain(prompt));if(parsed.errors.length)return null;
  const previous=groups(q),used=new Set(),answers=spec(q).answers||[];
  const items=parsed.found.map((mark,index)=>{
    const old=previous.find(item=>!used.has(item.id)&&item.text===mark.value);
    if(old)used.add(old.id);
    return {id:old?.id||newId('item_'),number:index+1,text:mark.value};
  });
  return {items,answers:items.map(item=>answers.find(a=>a.itemId===item.id)||{itemId:item.id,referenceAnswer:textContent('')})};
}
export function validateSelection(question,answer,key){
  const errors=[],items=groups(question),p=payload(question);
  for(const [id,value] of Object.entries(answer)){
    const group=items.find(item=>item.id===id);
    if(!group||typeof value!=='string')errors.push('作答包含未知位置');
    else if(group.locked)errors.push('提示位置不可提交作答');
    else if(!(group.options||p.options||[]).some(o=>o.id===value))errors.push('作答引用未知选项');
    else if(key==='MATCHING'&&items.some(b=>b.locked&&spec(question).answers?.some(a=>a.blankId===b.id&&a.correctOptionId===value)))errors.push('提示字母不可重复选择');
  }
  return {errors,empty:Object.keys(answer).length===0};
}
export function gradeSelection({question,answer,maxScore,reportResult}){
  const items=groups(question).filter(i=>!i.locked),answers=spec(question).answers||[];
  const right=items.filter(item=>answers.some(a=>(a.blankId||a.itemId)===item.id&&a.correctOptionId===answer[item.id])).length;
  return reportResult({score:right===items.length?maxScore:Number((maxScore*right/items.length).toFixed(8))});
}
