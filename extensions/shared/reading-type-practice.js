import {practiceBase,$,on,element,rich,payload,spec,groups,plain,decorateMarkers,stripBraces,selection,observeOptions,textContent} from './page.js';
import {richAnswer} from './rich-answer.js';
export async function startPractice(type,label){
  const choiceControls=[],inlineControls=[],slotControls=[],answerInputs=[],observers=[];let resultKey='';
  const objective=['CLOZE','READING','MATCHING'].includes(type);
  const api=await practiceBase(label,async (page,{answer,result,writes})=>{
    const correct=spec({answerSpec:result?.reference?.answerSpec}).answers||[];
    function right(id){return correct.find(a=>(a.blankId||a.itemId)===id)?.correctOptionId;}
    for(const control of choiceControls){
      if(!writes)control.input.checked=answer[control.group.id]===control.option.id;
      control.input.disabled=!page.writable;control.row.classList.toggle('selected',control.input.checked);
      control.row.classList.toggle('correct',Boolean(result)&&right(control.group.id)===control.option.id);
      control.row.classList.toggle('incorrect',Boolean(result)&&control.input.checked&&right(control.group.id)!==control.option.id);
    }
    for(const control of [...inlineControls,...slotControls]){
      if(control.given)continue;if(!writes)control.select.value=answer[control.group.id]||'';
      control.select.disabled=!page.writable;
      control.row.classList.toggle('correct',Boolean(result)&&right(control.group.id)===answer[control.group.id]);
      control.row.classList.toggle('incorrect',Boolean(result)&&right(control.group.id)!==answer[control.group.id]);
      control.row.title=result?(right(control.group.id)===answer[control.group.id]?'回答正确':'正确答案：'+letterFor(control.group,right(control.group.id))):'';
    }
    for(const {id,input} of answerInputs)await input.update(type==='ESSAY'?answer:answer[id],page.writable);
    const key=JSON.stringify(result?.reference||null);
    if(key!==resultKey){resultKey=key;const references=$('[data-references]');references.replaceChildren();
      if(result){
        if(objective){
          references.append(element('p','qf-correct-answer','正确答案：'+groups(question).filter(g=>!g.locked).map((g,index)=>page.number(g.id,index+1)+'. '+letterFor(g,right(g.id))).join('　')));
        }else if(type==='ESSAY'){
          references.append(element('strong','','参考答案'));const body=element('div','qf-reference');references.append(body);await rich(body,spec({answerSpec:result.reference?.answerSpec}).referenceAnswer);
        }else{
          for(const group of groups(question)){const section=element('section','qf-reference');section.append(element('strong','',page.number(group.id,group.number)+'. 参考译文'));const body=element('div');section.append(body);references.append(section);await rich(body,spec({answerSpec:result.reference?.answerSpec}).answers?.find(a=>a.itemId===group.id)?.referenceAnswer);}
        }
        const analysis=result.reference?.analysis||result.analysis||textContent('');
        await rich($('[data-analysis]'),type==='CLOZE'?textContent(plain(analysis)):analysis);
      }
    }
  });
  const question=api.question,data=payload(question),zone=$('[data-answer-zone]');
  function letterFor(group,id){const options=group.options||data.options||[];const index=options.findIndex(o=>o.id===id);return index<0?'未设置':options[index].label||String.fromCharCode(65+index);}
  function choose(group,value){const answer={...api.answer};if(value)answer[group.id]=value;else delete answer[group.id];return api.write(answer);}
  await rich($('[data-prompt]'),question.prompt);
  if(type==='CLOZE'){
    decorateMarkers($('[data-prompt]'),true,mark=>{
      const group=groups(question).find(g=>g.number===mark.number);if(!group)return document.createTextNode(mark.value);
      const number=api.number(group.id,mark.number),row=element('span','qf-inline-blank');row.append(element('span','',number+'.'));
      const select=selection(group.options,'','空位 '+number);select.options[0].textContent='______';group.options.forEach((o,index)=>select.options[index+1].textContent=String.fromCharCode(65+index)+'. '+o.content.text);
      row.append(select);inlineControls.push({group,select,row});on(select,'change',()=>choose(group,select.value));return row;
    });
  }
  if(type==='CLOZE'||type==='READING'){
    for(const group of groups(question)){
      const section=element('section','qf-subquestion');section.dataset.targetId=group.id;zone.append(section);
      const heading=element('h3');section.append(heading);
      if(type==='READING'){heading.append(element('span','',api.number(group.id,group.number)+'. '));const prompt=element('span');heading.append(prompt);await rich(prompt,group.prompt);}else heading.textContent=api.number(group.id,group.number)+'.';
      const options=element('div','qf-options');section.append(options);
      for(const [index,option] of group.options.entries()){
        const row=element('label','qf-choice-option'),input=element('input');input.type='radio';input.name='answer-'+group.id;input.value=option.id;input.setAttribute('aria-label',String.fromCharCode(65+index)+'. '+option.content.text);
        row.append(input,element('span','qf-option-letter',String.fromCharCode(65+index)+'.'),element('span','qf-option-text',option.content.text));options.append(row);choiceControls.push({group,option,input,row});
        on(input,'change',()=>{if(input.checked)return choose(group,option.id);});
      }
      observers.push(observeOptions(options));
    }
  }
  if(type==='MATCHING'){
    const slots=element('div','qf-matching-slots');zone.append(slots);
    const locked=new Set(groups(question).filter(g=>g.locked).map(g=>g.givenOptionId)),options=data.options.filter(o=>!locked.has(o.id));let number=0;
    for(const group of groups(question)){
      const row=element('div','qf-order-slot');slots.append(row);
      if(group.locked){row.classList.add('given');const given=data.options.find(o=>o.id===group.givenOptionId);row.textContent=given?.label||'提示缺失';row.title='已给出的提示';slotControls.push({group,row,given:true});continue;}
      row.dataset.targetId=group.id;number++;const displayNumber=api.number(group.id,number);row.append(element('span','',displayNumber+'.'));
      const select=selection(options,'','待答位置 '+displayNumber);row.append(select);slotControls.push({group,row,select});
      on(select,'change',()=>choose(group,select.value));
    }
  }
  if(type==='ESSAY'){
    zone.append(element('strong','','正式答案'));const root=element('div');zone.append(root);
    answerInputs.push({input:await richAnswer(root,value=>api.write(value),data.placeholder||'请输入作文')});
  }
  if(type==='TRANSLATION'){
    const marks=[];decorateMarkers($('[data-prompt]'),false,(mark,index,fragment)=>{
      const group=groups(question).find(g=>g.number===index+1),number=api.number(group?.id,index+1);
      const span=element('span','qf-translation-mark');span.append(element('sup','',number+'. '),stripBraces(fragment));span.tabIndex=0;span.setAttribute('role','button');span.setAttribute('aria-label','翻译句子 '+number);marks.push({span,index});return span;
    });
    for(const group of groups(question)){
      const number=api.number(group.id,group.number),section=element('section','qf-subquestion');section.dataset.targetId=group.id;section.append(element('h3','',number+'. '+group.text));zone.append(section);
      const root=element('div');section.append(root);const input=await richAnswer(root,value=>api.write({...api.answer,[group.id]:value}),'请输入第 '+number+' 句译文');answerInputs.push({id:group.id,input});
      const mark=marks.find(m=>m.index===group.number-1)?.span;if(mark){const focus=()=>{section.scrollIntoView({block:'nearest'});root.querySelector('[role=textbox]').focus();};on(mark,'click',focus);on(mark,'keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();focus();}});}
    }
  }
  window.addEventListener('pagehide',()=>observers.forEach(o=>o.disconnect()),{once:true});
  // Incomplete composite answers remain submit-able after confirmation, and receive zero per missing slot.
  if(type!=='ESSAY')$('[data-confirm-hint]').textContent='确认提交本张题卡？未填写的位置将保留为空。';
  await api.refresh();
}
