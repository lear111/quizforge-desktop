// Cloze changes are presentation only: the original native document stays untouched.
const TOKEN = /\\?\{\{([1-9][0-9]*)\}\}/g;
const textTypes = new Set([undefined, 'text', 'superscript', 'subscript']);

function transform(elements, config, groups) {
  const result = [];
  for (let i = 0; i < elements.length;) {
    if (!textTypes.has(elements[i].type)) {
      const element = {...elements[i++]};
      if (element.valueList) element.valueList = transform(element.valueList, config, groups);
      if (element.trList) element.trList = element.trList.map(row => ({...row,
        tdList: row.tdList.map(cell => ({...cell, value: transform(cell.value, config, groups)}))}));
      result.push(element); continue;
    }
    const runs = []; let text = '';
    while (i < elements.length && textTypes.has(elements[i].type)) {
      const element = elements[i++];
      runs.push({start:text.length, end:text.length + element.value.length, element});
      text += element.value;
    }
    const append = (start, end) => {
      for (const run of runs) {
        const left = Math.max(start, run.start), right = Math.min(end, run.end);
        if (right > left) result.push({...run.element, value:text.slice(left,right)});
      }
    };
    let position = 0;
    for (const match of text.matchAll(TOKEN)) {
      const blank = config.blanks.find(blank => blank.number === Number(match[1]));
      if (!blank && !match[0].startsWith('\\')) continue;
      append(position, match.index);
      if (match[0].startsWith('\\')) append(match.index + 1, match.index + match[0].length);
      else {
        const original = runs.find(run => run.end > match.index).element;
        const selected = blank.options.find(option => option.id === blank.selected);
        const group = `quizforge-cloze-${blank.number}-${groups.length}`;
        groups.push({id:group, blank});
        const correct = blank.selected === blank.correct;
        result.push({...original, value:`${blank.number}.${selected ? selected.text : '_______'}`,
          color:config.submitted ? (selected ? (correct ? '#26734a' : '#b14343') : '#777777') : selected ? '#715b99' : '#777777',
          highlight:undefined,
          underline:!!selected,
          groupIds:[...(original.groupIds || []), group]});
      }
      position = match.index + match[0].length;
    }
    append(position,text.length);
  }
  return result;
}

export function configureCloze(editor, state, configuration) {
  if (!state.clozeOriginal) state.clozeOriginal = JSON.parse(JSON.stringify(editor.command.getValue().data));
  state.clozeConfig = configuration;
  const groups = [];
  const data = JSON.parse(JSON.stringify(state.clozeOriginal));
  data.main = transform(data.main, configuration, groups);
  state.clozeGroups = groups;
  // This API normalizes a complete option set; partial input resets page geometry.
  const options = editor.command.getOptions();
  editor.command.executeUpdateOptions({...options,group:{...options.group,opacity:0,activeOpacity:0}});
  editor.command.executeSetValue(data);
  const paper = document.getElementById('paper');
  let layer = document.getElementById('cloze-hit-layer');
  if (!layer) { layer=document.createElement('div');layer.id='cloze-hit-layer';paper.append(layer); }
  if (state.clozeDismiss) document.removeEventListener('pointerdown',state.clozeDismiss,true);
  const close = () => {
    document.getElementById('cloze-options-popup')?.remove();
  };
  state.clozeDismiss = event => {
    if (!event.target.closest('#cloze-options-popup,.cloze-hit')) close();
  };
  document.addEventListener('pointerdown',state.clozeDismiss,true);
  const open = (blank,rect) => {
    close(); if (configuration.readonly || configuration.submitted) return;
    const menu = document.createElement('div');menu.id='cloze-options-popup';menu.setAttribute('role','group');
    const title=document.createElement('strong');title.textContent=`第 ${blank.number} 空`;menu.append(title);
    blank.options.forEach((option,index) => {
      const button=document.createElement('button');button.type='button';button.className='cloze-popup-option';
      button.textContent=`${String.fromCharCode(65+index)}. ${option.text}`;
      button.setAttribute('aria-pressed',String(option.id===blank.selected));
      button.classList.toggle('selected',option.id===blank.selected);
      button.onclick=() => {close();window.quizforgeHost?.clozeSelected(blank.number,option.id);};
      menu.append(button);
    });
    // Measure the document before adding the overlay. The menu must never resize it.
    const height=paper.clientHeight, gap=4;
    Object.assign(menu.style,{top:'0px',left:'0px',maxHeight:`${Math.max(1,height-gap*2)}px`});
    paper.append(menu);
    menu.style.left=`${Math.max(0,Math.min(rect.x,paper.clientWidth-menu.offsetWidth))}px`;
    const below=rect.y+rect.height+gap;
    const preferred=below+menu.offsetHeight<=height-gap ? below : rect.y-menu.offsetHeight-gap;
    menu.style.top=`${Math.max(0,Math.min(preferred,height-menu.offsetHeight))}px`;
    menu.querySelector('button[aria-pressed="true"]')?.focus();
  };
  state.clozeDraw = () => {
    layer.replaceChildren();
    groups.forEach(group => {
      for (const rect of editor.command.getGroupRectList(group.id) || []) {
        const hit=document.createElement('button');hit.type='button';hit.className='cloze-hit';
        hit.dataset.blank=String(group.blank.number);
        hit.setAttribute('aria-label',`第 ${group.blank.number} 空，${group.blank.options.find(o=>o.id===group.blank.selected)?.text || '未作答'}`);
        hit.disabled=!!(configuration.readonly || configuration.submitted);
        Object.assign(hit.style,{left:`${rect.x}px`,top:`${rect.y}px`,width:`${Math.max(1,rect.width)}px`,height:`${rect.height}px`});
        hit.onclick=event => {event.stopPropagation();open(group.blank,rect);};layer.append(hit);
      }
    });
  };
  close();state.clozeDraw();requestAnimationFrame(state.clozeDraw);
}
