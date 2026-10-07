import {editorBase,$,on,element,editContent,payload,spec,groups,plain,markers,textContent,bodyData,answerData,clone} from './page.js';
import {synchronizeTranslations} from './legacy-data.js';
export async function startEditor(type,label){
  const editor=await editorBase(label),list=$('[data-items]'),mounted=[];
  $('[data-score-label]').textContent=type==='ESSAY'?'分值':'每小题分值（排序提示不计分）';
  const patchData=(data,standard)=>editor.update({...bodyData(editor.question,data),...(standard?answerData(standard):{})});
  function newGroup(number){const prefix=type==='CLOZE'?'blank_':'item_',id=QF.ids.create(prefix);const options=['A','B','C','D'].map(letter=>({id:QF.ids.create('opt_'),content:textContent('选项 '+letter)}));return {id,number,...(type==='READING'?{prompt:textContent('请输入小题题干')}:{}),options};}
  async function articleChange(prompt){
    const question=editor.question;
    if(type==='TRANSLATION'){
      const sync=synchronizeTranslations(question,prompt,QF.ids.create);
      const response=await editor.update({prompt,...(sync?{...bodyData(question,{...payload(question),items:sync.items}),...answerData({...spec(question),answers:sync.answers})}:{})});
      if(response.ok&&sync)items();return response;
    }
    if(type==='CLOZE'){
      const parsed=markers(plain(prompt),true),numbers=[...new Set(parsed.found.map(m=>m.number))].sort((a,b)=>a-b);
      if(!parsed.errors.length&&numbers.every((n,index)=>n===index+1)){
        const previous=groups(question),blanks=numbers.map(number=>previous.find(b=>b.number===number)||newGroup(number));
        const answers=blanks.map(blank=>spec(question).answers.find(a=>a.blankId===blank.id)||{blankId:blank.id,correctOptionId:blank.options[0].id});
        const structural=blanks.length!==previous.length||blanks.some((b,n)=>b.id!==previous[n]?.id);
        const response=await editor.update({prompt,...bodyData(question,{...payload(question),blanks}),...answerData({...spec(question),answers})});
        if(response.ok&&structural)items();return response;
      }
    }
    return editor.update({prompt});
  }
  editContent($('[data-prompt]'),editor.question.prompt,articleChange);
  editContent($('[data-analysis]'),type==='CLOZE'?textContent(plain(editor.question.analysis)):editor.question.analysis,analysis=>editor.update({analysis}),type!=='CLOZE');
  function current(id){return groups(editor.question).find(item=>item.id===id);}
  function updateGroup(id,patch){const data=payload(editor.question),key=type==='CLOZE'?'blanks':'items';return patchData({...data,[key]:groups(editor.question).map(item=>item.id===id?{...item,...patch}:item)});}
  function setCorrect(id,optionId){const data=payload(editor.question),standard=spec(editor.question),key=type==='READING'?'itemId':'blankId';
    const answers=standard.answers.map(a=>a[key]===id?{...a,correctOptionId:optionId}:a);
    return patchData(type==='MATCHING'?{...data,blanks:data.blanks.map(b=>b.id===id&&b.locked?{...b,givenOptionId:optionId}:b)}:data,{...standard,answers});
  }
  function items(){
    mounted.splice(0).forEach(editor=>editor.destroy());list.replaceChildren();
    if(type==='ESSAY'){
      const reference=element('section','qf-editor-section');reference.append(element('strong','','参考答案'));const body=element('div');reference.append(body);list.append(reference);
      mounted.push(editContent(body,spec(editor.question).referenceAnswer,value=>editor.update(answerData({...spec(editor.question),referenceAnswer:value}))));return;
    }
    if(type==='MATCHING'){
      const slots=element('div','qf-matching-slots');list.append(slots);
      groups(editor.question).forEach((item,index)=>{
        const box=element('div','qf-editor-order'),line=element('div','qf-order-slot');line.append(element('span','',index+1+'.'));
        const select=element('select','qf-letter-select');select.setAttribute('aria-label','位置 '+(index+1)+' 的正确答案');payload(editor.question).options.forEach(option=>select.append(new Option(option.label,option.id)));
        select.value=spec(editor.question).answers.find(a=>a.blankId===item.id)?.correctOptionId||'';line.append(select);box.append(line);
        const lock=element('button','',item.locked?'🔒 已给出':'设为提示');lock.type='button';lock.setAttribute('aria-pressed',String(item.locked));box.append(lock);slots.append(box);
        on(select,'change',()=>setCorrect(item.id,select.value));
        on(lock,'click',async()=>{const data=payload(editor.question),old=data.blanks.find(b=>b.id===item.id),locked=!old.locked,givenOptionId=spec(editor.question).answers.find(a=>a.blankId===old.id)?.correctOptionId;
          await patchData({...data,blanks:data.blanks.map(b=>b.id===old.id?{id:b.id,number:b.number,locked,...(locked?{givenOptionId}: {})}:b)});items();});
      });
      list.append(element('p','muted',`已给出 ${groups(editor.question).filter(b=>b.locked).length} 个位置；保存时需三个提示，剩余五个位置计分。`));editor.refresh();return;
    }
    groups(editor.question).forEach(item=>{
      const section=element('section','qf-editor-item');section.dataset.targetId=item.id;const heading=element('div','qf-editor-item-header');heading.append(element('strong','',(type==='CLOZE'?'空位 ':type==='TRANSLATION'?'句子 ':'小题 ')+item.number));section.append(heading);list.append(section);
      if(type==='TRANSLATION'){
        section.append(element('p','',item.text),element('strong','','参考译文'));const reference=element('div');section.append(reference);
        mounted.push(editContent(reference,spec(editor.question).answers.find(a=>a.itemId===item.id)?.referenceAnswer,value=>editor.update(answerData({...spec(editor.question),answers:spec(editor.question).answers.map(a=>a.itemId===item.id?{...a,referenceAnswer:value}:a)}))));return;
      }
      if(type==='READING'){
        const remove=element('button','','删除小题');remove.type='button';heading.append(remove);
        on(remove,'click',async()=>{const data=payload(editor.question),items=data.items.filter(i=>i.id!==item.id).map((i,n)=>({...i,number:n+1}));await patchData({...data,items},{...spec(editor.question),answers:spec(editor.question).answers.filter(a=>a.itemId!==item.id)});itemsRender();});
        const prompt=element('div');section.append(prompt);mounted.push(editContent(prompt,item.prompt,value=>updateGroup(item.id,{prompt:value})));
      }
      item.options.forEach((option,index)=>{
        const row=element('div','qf-editor-row'),correct=element('input');correct.type='radio';correct.name='correct-'+item.id;correct.checked=spec(editor.question).answers.some(a=>(a.itemId||a.blankId)===item.id&&a.correctOptionId===option.id);correct.setAttribute('aria-label','将 '+String.fromCharCode(65+index)+' 设为正确答案');
        const input=element('input');input.type='text';input.value=option.content.text;input.setAttribute('aria-label','选项 '+String.fromCharCode(65+index));row.append(correct,element('span','',String.fromCharCode(65+index)+'.'),input);section.append(row);
        on(input,'input',()=>updateGroup(item.id,{options:current(item.id).options.map(o=>o.id===option.id?{...o,content:textContent(input.value)}:o)}));
        on(correct,'change',()=>setCorrect(item.id,option.id));
      });
    });
    editor.refresh();
  }
  const itemsRender=items;items();
  const add=$('[data-add-item]');add.hidden=type!=='READING';on(add,'click',async()=>{const data=payload(editor.question),item=newGroup(data.items.length+1);await patchData({...data,items:[...data.items,item]},{...spec(editor.question),answers:[...spec(editor.question).answers,{itemId:item.id,correctOptionId:item.options[0].id}]});items();});
  const sync=$('[data-sync]');sync.hidden=!['CLOZE','TRANSLATION'].includes(type);on(sync,'click',()=>articleChange(editor.question.prompt));
  $('[data-hint]').textContent=({CLOZE:'使用 {{1}}、{{2}} 标记空位。修改正文标记后自动同步选项组；重复编号只计一次。',READING:'文章与小题题干支持富文本，选项使用普通文字。',MATCHING:'题干直接填写 A–H 八段材料；设置完整顺序，并锁定三个已有提示。',TRANSLATION:'使用 {{需要翻译的句子}} 标记目标句，不写序号；自动按顺序编号。参考译文跟随句子身份。',ESSAY:'题干、正式回答、参考答案及解析均支持富文本。小作文和大作文复用同一题型。'})[type];
  await editor.refresh();
}
