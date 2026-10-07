import {payload,spec,groups,plain,markers,clone,textContent} from './legacy-data.js';
import {connectPage} from './page-client.js';
export {payload,spec,groups,plain,markers,clone,textContent};
export const $=selector=>QF.dom.$(selector);
// These nodes are created by this package, sometimes before insertion. Frame disposal
// releases their listeners; no trusted host object is attached to them.
export const on=(node,event,handler)=>node.addEventListener(event,handler);
export function element(tag,cls='',text){const node=document.createElement(tag);node.className=cls;if(text!==undefined)node.textContent=text;return node;}
export async function rich(node,value){await QF.content.renderAsync(node,value||textContent(''));}
export function editContent(node,value,onChange,formatting=true){return QF.content.mountEditor(node,{value,onChange,formatting});}
export function selection(options,value,label){const select=element('select','qf-letter-select');select.setAttribute('aria-label',label);select.append(new Option('—',''));options.forEach((option,index)=>select.append(new Option(option.label||String.fromCharCode(65+index),option.id)));select.value=value||'';return select;}
export function bodyData(q,data){return {payload:{kind:'EXTENSION',data}};}
export function answerData(data){return {answerSpec:{kind:'EXTENSION',data}};}
export async function editorBase(label){
  const page=await connectPage(QF);
  const reply=await page.question();if(!reply.ok)throw new Error(reply.error.message);
  let question=reply.data,revision=0,capabilities={};
  QF.layout.configure({cardWidth:820,maxCardWidth:'100%',horizontalAlign:'center',verticalAlign:'top',padding:20});
  // Retain the stable host's save/navigation/source controls; package owns its content fields.
  QF.ui.configure({typeLabel:false});
  $('[data-title]').textContent=label+'编辑';
  async function update(patch){
    const current=++revision;question={...question,...clone(patch)};
    const response=await page.edit(patch);
    if(current===revision){if(!response.ok){QF.ui.notify(response.error.message);const accepted=await page.question();if(accepted.ok)question=accepted.data;}else question=response.data;}
    return response;
  }
  const score=$('[data-score]');score.value=question.scoreSpec.defaultMaxScore;
  on(score,'input',()=>{const value=Number(score.value);if(score.value.trim()&&Number.isFinite(value)&&value>0)return update({scoreSpec:{defaultMaxScore:value}});});
  async function refresh(){const context=await page.host();if(!context.ok)return;capabilities=context.data.capabilities;
    for(const node of QF.dom.root.querySelectorAll('input,select,textarea,button'))node.disabled=!capabilities.editQuestion;
  }
  page.subscribe(refresh);
  return {get question(){return question;},update,refresh,get writable(){return capabilities.editQuestion;}};
}
export async function practiceBase(label,onState){
  const page=await connectPage(QF);
  const reply=await page.question();if(!reply.ok)throw new Error(reply.error.message);
  const question=reply.data;let answer={},context={},result=null,state={},confirming=false,busy=false,writes=0,revision=0;
  QF.layout.configure({cardWidth:820,maxCardWidth:'100%',horizontalAlign:'center',verticalAlign:'center',padding:20});
  QF.ui.configure({typeLabel:false,position:false,score:false,state:false,submit:false,retry:false,confirmation:false,note:false,draftToggle:true,draftToolbar:true,draftZoom:true});
  $('[data-type-label]').textContent=label;
  const api={question,get answer(){return answer;},get result(){return result;},get writable(){return !busy&&!confirming&&context.capabilities?.editAnswer;},
    number(id,fallback){return page.context.navigation.targets?.find(target=>target.id===id)?.number??fallback;},
    async write(value){answer=clone(value);writes++;revision++;const response=await page.write(value);if(!response.ok)QF.ui.notify(response.error.message);writes--;if(!writes)await refresh();return response;},
    async refresh(){return refresh();}};
  async function refresh(){
    const current=++revision;
    const [a,c,r,s]=await Promise.all([page.answer(),page.host(),page.result(),page.practiceState()]);
    if(current!==revision||!a.ok||!c.ok||!r.ok||!s.ok)return;
    if(!writes)answer=a.data;context=c.data;result=r.data;state=s.data;
    const readOnly=['HISTORY','PREVIEW'].includes(context.mode),permissions=context.permissions.granted;
    $('[data-position]').textContent=Number.isInteger(state.index)?`第 ${state.index+1} / ${state.total} 题`:'';
    $('[data-max-score]').textContent='分值：'+(state.maxScore??'未设置');
    $('[data-state]').textContent=({UNANSWERED:'未作答',DRAFT:'作答中',SUBMITTED:'已提交',RETRYING:'重试中',REVISING:'修订中'})[state.state]||'';
    if(readOnly||result)confirming=false;
    $('[data-confirm]').hidden=!confirming;
    const remaining=groups(question).filter(g=>!g.locked&&!answer[g.id]?.text?.trim()&&! (typeof answer[g.id]==='string'&&answer[g.id])).length;
    $('[data-confirm-hint]').textContent=remaining?`还有 ${remaining} 道未完成，确认提交当前答案？`:'确认提交当前答案？';
    $('[data-submit]').hidden=readOnly||!permissions.includes('practice.submit')||Boolean(result);
    $('[data-submit]').disabled=busy||confirming||!context.capabilities.submit||!Object.keys(answer).length;
    $('[data-accept]').disabled=busy||!context.capabilities.submit;
    $('[data-retry]').hidden=readOnly||!result||!permissions.includes('practice.retry');
    $('[data-retry]').disabled=busy||!context.capabilities.retry;
    $('[data-note]').textContent=readOnly?'只读记录':'草稿自动保存；提交后冻结，重试从空白草稿开始。';
    $('[data-result]').hidden=!result;
    if(result){$('[data-verdict]').textContent=({CORRECT:'回答正确',INCORRECT:'回答错误',UNSCORED:'待评分'})[result.status]||'待评分';$('[data-verdict]').className=result.status==='CORRECT'?'correct':result.status==='INCORRECT'?'incorrect':'muted';$('[data-result-score]').textContent=result.score==null?'待评分':`得分：${result.score} / ${result.maxScore}`;}
    await onState(api,{answer,context,result,state,writes});
  }
  async function action(fn){if(busy)return;busy=true;try{await refresh();const reply=await fn();if(!reply.ok)QF.ui.notify(reply.error.message);}finally{busy=false;await refresh();}}
  on($('[data-submit]'),'click',()=>{confirming=true;$('[data-confirm]').hidden=false;refresh();});
  on($('[data-cancel]'),'click',()=>{confirming=false;refresh();});
  on($('[data-accept]'),'click',()=>{confirming=false;return action(()=>page.submit());});
  on($('[data-retry]'),'click',()=>action(()=>page.action('retry')));
  page.subscribe(refresh);return api;
}
/** Replace markers across formatted runs, retaining all surrounding DOM and sentence styles. */
export function decorateMarkers(root,numeric,create){
  const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT),nodes=[];let text='',node;
  while((node=walker.nextNode())){nodes.push({node,start:text.length,end:text.length+node.data.length});text+=node.data;}
  const {found}=markers(text,numeric);
  for(let i=found.length-1;i>=0;i--){const mark=found[i],first=nodes.find(n=>n.end>mark.start),last=nodes.find(n=>n.end>=mark.end);if(!first||!last)continue;
    const range=document.createRange();range.setStart(first.node,mark.start-first.start);range.setEnd(last.node,mark.end-last.start);
    const fragment=range.extractContents();range.insertNode(create(mark,i,fragment));range.detach();
  }
  // Escaped markers remain literal rather than creating controls.
  const literal=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);while((node=literal.nextNode()))node.data=node.data.replace(/\\\{\{/g,'{{');
}
export function stripBraces(fragment){
  const walker=document.createTreeWalker(fragment,NodeFilter.SHOW_TEXT),nodes=[];let node;
  while((node=walker.nextNode()))nodes.push(node);
  let count=2;for(const n of nodes){const size=Math.min(count,n.data.length);n.data=n.data.slice(size);count-=size;if(!count)break;}
  count=2;for(const n of [...nodes].reverse()){const size=Math.min(count,n.data.length);n.data=n.data.slice(0,n.data.length-size);count-=size;if(!count)break;}
  return fragment;
}
/** Try 4, then 2, then 1 columns using measured unwrapped option text. */
export function fitOptions(root){
  const rows=[...root.children];if(!rows.length)return;
  const gap=12,width=root.clientWidth;
  if(width<=0)return;
  // Measure outside the observed grid. Inserting probes into the grid used to
  // trigger its layout observer while that observer was still being delivered.
  const probe=document.createElement('span');probe.style.cssText='position:fixed;visibility:hidden;white-space:pre;width:max-content;pointer-events:none';document.body.append(probe);
  let widest=0;
  try{for(const row of rows){const label=row.querySelector('.qf-option-text');if(!label)continue;const style=getComputedStyle(label);
    probe.style.font=style.font;probe.style.letterSpacing=style.letterSpacing;probe.style.wordSpacing=style.wordSpacing;probe.textContent=label.textContent;
    widest=Math.max(widest,probe.getBoundingClientRect().width+64);
  }}finally{probe.remove();}
  const columns=`repeat(${[4,2,1].find(n=>n===1||(width-gap*(n-1))/n>=widest)}, minmax(0px, 1fr))`;
  if(root.style.gridTemplateColumns!==columns)root.style.gridTemplateColumns=columns;
}
/** Observe width only; changing column count is deferred to the next frame. */
export function observeOptions(root){
  let frame=0,lastWidth=-1,disposed=false;
  const schedule=()=>{if(!disposed&&!frame)frame=requestAnimationFrame(()=>{frame=0;if(!disposed)fitOptions(root);});};
  const resize=new ResizeObserver(entries=>{
    const width=entries[0]?.contentRect.width??root.clientWidth;
    if(Math.abs(width-lastWidth)<.5)return;lastWidth=width;schedule();
  });
  resize.observe(root);fitOptions(root);
  document.fonts?.ready.then(schedule);
  return {disconnect(){disposed=true;resize.disconnect();if(frame)cancelAnimationFrame(frame);frame=0;}};
}
