// Create distributable example banks; never writes into a user's workspace.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {zip} from './pack.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../packages');
const names=['single-choice','multiple-choice','true-false','cloze','reading','matching','translation','essay'];
function rekey(question,prefix){
  const ids=new Map();
  function collect(v){if(Array.isArray(v))v.forEach(collect);else if(v&&typeof v==='object'){if(typeof v.id==='string'){const split=v.id.indexOf('_');ids.set(v.id,(split<0?'id':v.id.slice(0,split))+'_'+prefix+'_'+(split<0?v.id:v.id.slice(split+1)));}Object.values(v).forEach(collect);}}
  function rewrite(v){if(typeof v==='string')return ids.get(v)||v;if(Array.isArray(v))return v.map(rewrite);if(v&&typeof v==='object')return Object.fromEntries(Object.entries(v).map(([k,x])=>[k,rewrite(x)]));return v;}
  collect(question);return rewrite(question);
}
for(const slug of names){
  const directory=resolve(root,slug),manifest=JSON.parse(await readFile(resolve(directory,'manifest.json'),'utf8'));
  const type=manifest.types[0],question=rekey(JSON.parse(await readFile(resolve(directory,type.defaultQuestion),'utf8')),'example_'+slug.replaceAll('-','_'));
  if(type.id==='CLOZE'){
    question.prompt.text='Read the passage and choose the best word for each blank.\nRegular practice helps learners {{1}} confidence. When they make a mistake, they should {{2}} on what went wrong. Sharing ideas with others can {{3}} their understanding.';
    const choices=[['build','break','hide','lose'],['reflect','sleep','depend','insist'],['broaden','reduce','forget','replace']];
    question.payload.data.blanks.forEach((blank,i)=>blank.options.forEach((option,j)=>{option.content={kind:'TEXT',text:choices[i][j]};}));
    question.analysis={kind:'TEXT',text:'1. build confidence：建立信心。2. reflect on：反思。3. broaden understanding：拓宽理解。'};
  }
  if(type.id==='MATCHING'){
    const paragraphs={F:'Last Saturday, I decided to spend the morning at the city library.',B:'Before leaving home, I packed a notebook into my bag.',E:'With my bag ready, I caught a bus to the library.',H:'After arriving, I entered the library and looked for the reading room.',A:'Inside the reading room, I chose a book and read it for an hour.',C:'Once I had finished reading, I returned the book to its shelf.',G:'On my way home from the library, I met a friend and told her about the book.',D:'That evening, I wrote a short review of the book in my notebook.'};
    question.prompt.text='Arrange the paragraphs into a chronological story. Three positions are already given.\n\n'+question.payload.data.options.map(o=>o.label+'. '+paragraphs[o.label]).join('\n\n');
    question.analysis={kind:'TEXT',text:'完整顺序：F → B → E → H → A → C → G → D。按决定出门、准备、乘车、到馆、阅读、归还、回家、晚间写书评的时间顺序排列；F、H、C 为固定提示。'};
  }
  // Replace placeholder reading items with a complete, answerable example.
  if(type.id==='READING'){
    const prompts=['What does practice help learners build?','What helps learners understand mistakes?','How can sharing ideas help learners?'];
    const choices=[['Confidence','Money','Competition','Silence'],['Reflection','Speed','Guessing','Ignoring them'],['It broadens understanding','It replaces practice','It avoids reflection','It prevents mistakes']];
    question.payload.data.items.forEach((item,i)=>{item.prompt={kind:'TEXT',text:prompts[i%3]};item.options.forEach((o,j)=>{o.content={kind:'TEXT',text:choices[i%3][j]};});});
    question.analysis={kind:'TEXT',text:'三题均选 A。文章分别说明练习建立信心，反思帮助理解错误，交流想法拓宽理解。'};
  }
  if(type.id==='TRANSLATION'){
    const references=['阅读帮助我们了解世界。','一个好问题能够带来新的发现。','学习需要耐心和练习。','不同的观点能够拓宽我们的视野。','微小的努力能够产生持久的影响。'];
    question.answerSpec.data.answers.forEach((answer,i)=>{answer.referenceAnswer={kind:'TEXT',text:references[i]};});
    question.analysis={kind:'TEXT',text:'参考译文供对照理解；关注 helps、lead to、requires、broaden 和 make a difference 等表达。本示例不自动判分。'};
  }
  if(type.id==='ESSAY'){
    question.answerSpec.data.referenceAnswer={kind:'TEXT',text:'Dear Paul,\nI recently read The Little Prince and would like to recommend it to you. Although the story seems simple, it offers thoughtful ideas about friendship and responsibility. My favorite part is the prince’s conversation with the fox, which reminds us that relationships grow through time and care. The book also encourages readers to notice things that are easy to overlook in everyday life. I think you would enjoy its gentle humor and memorable illustrations. If you are interested, I can lend you my copy when we meet next week.\nYours,\nLi Ming'};
    question.analysis={kind:'TEXT',text:'写作要点：说明书名、介绍主要内容和感受、向朋友推荐；使用邮件格式，以 Li Ming 署名。参考范文用于展示，提交后保持待评分。'};
  }
  const hash=createHash('sha256').update(manifest.id+':basic').digest('hex');
  const assetId='qb_example_'+hash.slice(0,24);
  const bank={stimuli:[],questions:[question]};
  const metadata={format:'quizforge-question-bank',schemaVersion:'2.0',assetId,title:type.label+' · 示例题库',resources:[]};
  await mkdir(resolve(directory,'examples'),{recursive:true});
  await writeFile(resolve(directory,'examples/basic.qbank'),zip([{name:'manifest.json',data:Buffer.from(JSON.stringify(metadata))},{name:'bank.json',data:Buffer.from(JSON.stringify(bank))}]));
  manifest.minSdkApiMinor=3;type.pageApi='simple';
  type.examples=[{id:'basic',title:type.label+'基础示例',description:'可预览、可复制导入的完整题库；与默认新建模板独立。',path:'examples/basic.qbank'}];
  await writeFile(resolve(directory,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
  console.log(type.id+': '+bank.questions.length+' example question');
}
