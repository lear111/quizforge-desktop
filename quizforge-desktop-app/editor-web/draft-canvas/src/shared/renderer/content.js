import { element } from './contract.js';

/** Owned structured content, rendered in the same World DOM as answers and annotation. */
export function readContent(value, name) {
  const fail = () => { throw new TypeError(`Unsupported content: ${name} is missing structured data`); };
  if (value?.kind === 'RICH' && Array.isArray(value.document?.blocks)) {
    const plain = node => typeof node.text === 'string' ? node.text : (node.children || node.blocks || node.items || []).map(plain).join('');
    return { ...value, text: typeof value.text === 'string' ? value.text : value.document.blocks.map(plain).join('\n') };
  }
  if (!value || typeof value.text !== 'string') fail();
  if (value.kind === 'TEXT') return {kind:'TEXT',text:value.text};
  if (value.kind === 'DOCUMENT' && Array.isArray(value.document?.data?.main)) return value;
  if (value.kind === 'RICH' && Array.isArray(value.document?.blocks)) return value;
  fail();
}
function image(src, alt = '') {
  const node = element('img'); node.alt = alt;
  // Documents already own their images; do not resolve arbitrary paths or remote URLs.
  if (/^data:image\/(png|jpeg|jpg|gif|webp|bmp|svg\+xml);base64,/i.test(src || '')) node.src = src;
  return node;
}
function style(node, item, defaults = {}) {
  if (item.font || defaults.defaultFont) node.style.fontFamily = item.font || defaults.defaultFont;
  if (Number.isFinite(item.size || defaults.defaultSize)) node.style.fontSize = `${item.size || defaults.defaultSize}px`;
  if (item.bold) node.style.fontWeight = 'bold';
  if (item.italic) node.style.fontStyle = 'italic';
  if (item.color) node.style.color = item.color;
  if (item.highlight) node.style.backgroundColor = item.highlight;
  const decoration = [item.underline?'underline':'', item.strikeout?'line-through':''].filter(Boolean);
  if (decoration.length) node.style.textDecoration = decoration.join(' ');
}
function nativeElements(root, items, defaults, inline = false) {
  let paragraph = null;
  function line(item = {}) {
    if(inline && paragraph)root.append(element('br'));
    paragraph = element(inline?'span':'div','document-paragraph'); root.append(paragraph);
    const flex = item.rowFlex;
    paragraph.style.textAlign = {alignment:'justify',justify:'justify',center:'center',right:'right',left:'left'}[flex] || 'left';
    if (Number.isFinite(item.rowMargin)) paragraph.style.lineHeight = String(1.2 + item.rowMargin * .2);
    return paragraph;
  }
  function block(node) { root.append(node); paragraph = null; }
  for (const item of items) {
    if (item.type === 'table') {
      const table = element('table'); table.style.width = '100%';
      if (item.borderType === 'none') table.classList.add('document-borderless');
      const cols = element('colgroup'); const total = (item.colgroup || []).reduce((n,c)=>n+c.width,0);
      for (const c of item.colgroup || []) {const col=element('col'); if(total)col.style.width=`${c.width/total*100}%`; cols.append(col);} table.append(cols);
      for (const row of item.trList || []) {
        const tr=element('tr');
        for (const cell of row.tdList || []) {
          const td=element('td');td.colSpan=cell.colspan||1;td.rowSpan=cell.rowspan||1;td.style.verticalAlign=cell.verticalAlign||'top';
          if(cell.backgroundColor)td.style.backgroundColor=cell.backgroundColor;
          nativeElements(td,cell.value||[],defaults);tr.append(td);
        } table.append(tr);
      } block(table); continue;
    }
    if (item.type === 'title') {
      const level={first:1,second:2,third:3,fourth:4,fifth:5,sixth:6}[item.level] || 3;
      const heading=element(`h${level}`);nativeElements(heading,item.valueList||[],defaults);block(heading);continue;
    }
    if (item.type === 'list') {
      const list=element(item.listType==='ol'?'ol':'ul');
      let parts=[];
      for (const part of item.valueList || []) {
        if((part.value==='\n'||part.listWrap)&&parts.length) {const li=element('li');nativeElements(li,parts,defaults);list.append(li);parts=[];}
        parts.push(part);
      }
      if(parts.length){const li=element('li');nativeElements(li,parts,defaults);list.append(li);}block(list);continue;
    }
    if (item.type === 'separator') {block(element('hr'));continue;}
    if (item.type === 'pageBreak') continue; // Continuous practice reading has no paper pagination.
    if (item.type === 'image') {
      const img=image(item.value,item.alt);if(item.width)img.style.width=`${item.width}px`;
      (paragraph||line(item)).append(img);continue;
    }
    if (item.type === 'hyperlink' || item.type === 'control' || item.type === 'date') {
      const span=element('span');style(span,item,defaults);nativeElements(span,item.valueList||item.control?.value||[],defaults,true);(paragraph||line(item)).append(span);continue;
    }
    const parts=String(item.value || '').replace(/\u200b/g,'').split('\n');
    parts.forEach((part,n)=>{
      if(n)line(item);
      if(!part)return;
      const container=paragraph||line(item);if(item.rowFlex)container.style.textAlign={alignment:'justify',justify:'justify',center:'center',right:'right',left:'left'}[item.rowFlex]||'left';
      const span=element(item.type==='superscript'?'sup':item.type==='subscript'?'sub':'span','',part);style(span,item,defaults);container.append(span);
    });
  }
}
function richBlocks(root, blocks, images) {
  for (const block of blocks) {
    let node;
    if(block.type==='IMAGE') {node=element('figure');const img=image(images[block.resourceId],block.alt);if(block.widthPercent)img.style.width=`${block.widthPercent}%`;node.append(img);if(block.caption)node.append(element('figcaption','',block.caption));}
    else if(block.type==='MATH') node=element('p','document-math',block.tex);
    else if(block.type==='BLOCK_QUOTE') {node=element('blockquote');richBlocks(node,block.blocks,images);}
    else if(block.type==='BULLET_LIST'||block.type==='ORDERED_LIST') {
      node=element(block.type==='BULLET_LIST'?'ul':'ol');if(block.start)node.start=block.start;
      for(const item of block.items){const li=element('li');richBlocks(li,item.blocks,images);node.append(li);}
    } else {
      node=element(block.type==='HEADING'?`h${block.level}`:'p');
      for(const item of block.children||[])richInline(node,item,images);
    }
    if(block.alignment)node.style.textAlign={JUSTIFY:'justify',CENTER:'center',RIGHT:'right',LEFT:'left'}[block.alignment]||'left';root.append(node);
  }
}
function richInline(root,item,images) {
  if(item.type==='IMAGE'){root.append(image(images[item.resourceId],item.alt));return;}
  if(item.type==='LINE_BREAK'){root.append(element('br'));return;}
  if(item.type==='LINK'){for(const child of item.children)richInline(root,child,images);return;}
  const span=element('span','',item.type==='MATH'?item.tex:item.text);
  style(span,{bold:item.marks?.includes('BOLD'),italic:item.marks?.includes('ITALIC'),underline:item.marks?.includes('UNDERLINE'),strikeout:item.marks?.includes('STRIKE')});root.append(span);
}
export function renderContent(root, content) {
  root.classList.add('shared-content');
  if(content.kind==='TEXT'){root.textContent=content.text;return root;}
  root.classList.add('document-content');
  if(content.kind==='DOCUMENT')nativeElements(root,content.document.data.main,content.document.options||{});
  else richBlocks(root,content.document.blocks,content.images||{});
  return root;
}

/** Trusted rendering helpers copied into an isolated document, without host objects. */
export function isolatedContentSource() {
  return [element,image,style,nativeElements,richBlocks,richInline,renderContent].map(fn=>fn.toString()).join('\n');
}

/** Resolve tokens across styled text runs without stripping formatting around the token. */
export function replaceMarkers(root, pattern, create) {
  const walker=document.createTreeWalker(root,4);const nodes=[];let text='',node;
  while((node=walker.nextNode())){nodes.push({node,start:text.length,end:text.length+node.data.length});text+=node.data;}
  const matches=Array.from(text.matchAll(pattern));
  for(let n=matches.length-1;n>=0;n--){
    const match=matches[n],start=match.index,end=start+match[0].length;
    const first=nodes.find(i=>i.end>start),last=nodes.find(i=>i.end>=end);
    if(!first||!last)continue;
    const range=document.createRange();range.setStart(first.node,start-first.start);range.setEnd(last.node,end-last.start);
    const fragment=range.extractContents();const replacement=create(match,n,fragment);range.insertNode(replacement);range.detach();
  }
}
