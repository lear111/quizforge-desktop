import {element,on,rich,textContent,plain} from './page.js';
export function answerContent(value){
  if(value?.kind==='CANVAS_DOCUMENT'){
    try{return {kind:'DOCUMENT',text:value.text||'',document:JSON.parse(value.document)};}catch{return textContent(value.text||'');}
  }
  return value&&['TEXT','RICH','DOCUMENT'].includes(value.kind)?value:textContent('');
}
function serialize(root){
  const main=[];
  function inline(node,items){
    if(node.nodeType===Node.TEXT_NODE){if(!node.data)return;const parent=node.parentElement,style=getComputedStyle(parent);
      const item={value:node.data,font:style.fontFamily,size:parseFloat(style.fontSize),color:style.color};
      item.bold=Number(style.fontWeight)>=600||style.fontWeight==='bold';item.italic=style.fontStyle==='italic';
      for(let current=parent;current&&current!==root;current=current.parentElement){const decoration=getComputedStyle(current).textDecorationLine;if(decoration.includes('underline'))item.underline=true;if(decoration.includes('line-through'))item.strikeout=true;}
      items.push(item);return;
    }
    if(node.nodeType!==Node.ELEMENT_NODE)return;
    if(node.tagName==='IMG'){if(/^data:image\//i.test(node.src))items.push({type:'image',value:node.src,width:node.width,height:node.height});return;}
    if(node.tagName==='BR'){items.push({value:'\n'});return;}
    for(const child of node.childNodes)inline(child,items);
  }
  function append(items,node){
    if(!items.length)items.push({value:''});
    const style=getComputedStyle(node.nodeType===Node.ELEMENT_NODE?node:root);
    if(main.length)main.push({value:'\n'});
    const flex=style.textAlign==='justify'?'alignment':style.textAlign;
    if(parseFloat(style.textIndent)>0)items.unshift({value:'\u2003\u2003'});
    items.forEach(item=>item.rowFlex=flex);main.push(...items);
  }
  function block(node){
    if(node.nodeType===Node.ELEMENT_NODE&&node.tagName==='TABLE'){
      main.push({type:'table',value:'',trList:[...node.rows].map(row=>({tdList:[...row.cells].map(cell=>{const value=[];inline(cell,value);return {colspan:cell.colSpan,rowspan:cell.rowSpan,value};})}))});return;
    }
    const items=[];inline(node,items);append(items,node);
  }
  // Formatting may split a line into several spans. Keep those runs on the same
  // paragraph rather than introducing a newline for every formatted fragment.
  let runs=[];
  for(const node of root.childNodes){
    if(node.nodeType===Node.ELEMENT_NODE&&['DIV','P','TABLE','UL','OL','BLOCKQUOTE','H1','H2','H3'].includes(node.tagName)){
      if(runs.length){append(runs,root);runs=[];}block(node);
    }else inline(node,runs);
  }
  if(runs.length)append(runs,root);
  // Embedded images and native table data remain owned by this answer; no resource path escapes.
  const document={data:{header:[],main,footer:[]},options:{defaultFont:'Arial',defaultSize:16}};
  return {kind:'CANVAS_DOCUMENT',text:root.innerText,document:JSON.stringify(document)};
}
/** One stable input DOM: formatting switches never recreate the answer or lose its selection. */
export async function richAnswer(root,onChange,placeholder='请输入答案'){
  const heading=element('div','qf-input-heading'),toggle=element('button','','编辑格式');toggle.type='button';heading.append(toggle);
  const tools=element('div','qf-format-toolbar');tools.hidden=true;
  const body=element('div','qf-answer-input');body.contentEditable='false';body.setAttribute('role','textbox');body.setAttribute('aria-multiline','true');body.setAttribute('aria-label',placeholder);body.dataset.placeholder=placeholder;
  root.append(heading,tools,body);
  let formatting=false,writable=false,lastValue='',lastRange=null,revision=0,composing=false,loading=false,allowed=false,disposed=false;
  function remember(){const selection=window.getSelection();if(selection?.rangeCount&&body.contains(selection.anchorNode))lastRange=selection.getRangeAt(0).cloneRange();}
  const selectionListener=()=>remember();document.addEventListener('selectionchange',selectionListener);
  window.addEventListener('pagehide',()=>{disposed=true;revision++;applyPermissions();document.removeEventListener('selectionchange',selectionListener);},{once:true});
  function emit(){if(!writable||composing)return;const value=serialize(body);lastValue=JSON.stringify(value);return onChange(value);}
  function restore(){body.focus();if(lastRange&&body.contains(lastRange.commonAncestorContainer)){const selection=window.getSelection();selection.removeAllRanges();selection.addRange(lastRange);}}
  function command(name,value){if(!writable)return;restore();document.execCommand(name,false,value);remember();emit();}
  for(const [label,name] of [['↶','undo'],['↷','redo'],['B','bold'],['I','italic'],['U','underline'],['S','strikeThrough'],['左','justifyLeft'],['中','justifyCenter'],['右','justifyRight'],['齐','justifyFull'],['缩进','indent']]){
    const button=element('button','',label);button.type='button';button.title=name;on(button,'pointerdown',event=>event.preventDefault());on(button,'click',()=>command(name));tools.append(button);
  }
  const font=element('select');font.setAttribute('aria-label','字体');['Arial','Times New Roman','Microsoft YaHei','SimSun'].forEach(name=>font.append(new Option(name,name)));on(font,'change',()=>command('fontName',font.value));tools.append(font);
  const size=element('select');size.setAttribute('aria-label','字号');[12,14,16,18,20,24,28,32].forEach(value=>size.append(new Option(value,value)));size.value='16';
  on(size,'change',()=>{restore();document.execCommand('fontSize',false,'7');for(const node of body.querySelectorAll('font[size="7"]')){node.removeAttribute('size');node.style.fontSize=size.value+'px';}remember();emit();});tools.append(size);
  const color=element('input');color.type='color';color.value='#000000';color.setAttribute('aria-label','文字颜色');on(color,'input',()=>command('foreColor',color.value));tools.append(color);
  on(toggle,'pointerdown',event=>{remember();event.preventDefault();});on(toggle,'click',()=>{formatting=!formatting;tools.hidden=!formatting;toggle.textContent=formatting?'完成编辑':'编辑格式';});
  on(body,'input',emit);on(body,'compositionstart',()=>{composing=true;});on(body,'compositionend',()=>{composing=false;emit();});
  on(body,'paste',event=>{if(!writable)return;event.preventDefault();document.execCommand('insertText',false,event.clipboardData.getData('text/plain'));emit();});
  function applyPermissions(){
    writable=allowed&&!loading&&!disposed;body.contentEditable=String(writable);body.setAttribute('aria-readonly',String(!writable));toggle.disabled=!writable;tools.hidden=!writable||!formatting;
    tools.querySelectorAll('input,select,button').forEach(node=>node.disabled=!writable);
  }
  return {async update(value,enabled){
    if(disposed)return;
    allowed=enabled;
    const encoded=JSON.stringify(value||{});
    if(encoded===lastValue){if(loading){revision++;loading=false;}applyPermissions();return;}
    if(composing){applyPermissions();return;}
    const current=++revision;loading=true;applyPermissions();
    // Resolve into a hidden sibling. A late renderer must never touch the live
    // answer DOM, even when a newer answer or attempt has already been loaded.
    const staging=element('div');staging.hidden=true;root.append(staging);
    try{
      await rich(staging,answerContent(value));
      if(disposed||current!==revision)return;
      body.replaceChildren(...staging.childNodes);lastValue=encoded;lastRange=null;
      body.classList.toggle('shared-content',staging.classList.contains('shared-content'));
      body.classList.toggle('document-content',staging.classList.contains('document-content'));
      body.classList.toggle('qf-answer-empty',!plain(answerContent(value)).trim());
      loading=false;applyPermissions();
    }finally{staging.remove();}
    // On render failure loading stays true. Retrying update can recover, but
    // empty/stale content cannot become editable and get saved as a new answer.
  },getValue(){if(loading||disposed)throw new Error('答案仍在加载或页面已关闭');return serialize(body);}};
}
