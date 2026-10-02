// Translation markers are removed only from the preview; authoring data stays intact.
const TOKEN = /\\?\{\{([\s\S]+?)\}\}/g;
const textTypes = new Set([undefined, 'text', 'superscript', 'subscript']);

function transform(elements, sequence) {
  const result = [];
  for (let i = 0; i < elements.length;) {
    if (!textTypes.has(elements[i].type)) {
      const element = {...elements[i++]};
      if (element.valueList) element.valueList = transform(element.valueList, sequence);
      if (element.trList) element.trList = element.trList.map(row => ({...row,
        tdList: row.tdList.map(cell => ({...cell, value: transform(cell.value, sequence)}))}));
      result.push(element); continue;
    }
    const runs = []; let text = '';
    while (i < elements.length && textTypes.has(elements[i].type)) {
      const element = elements[i++];
      runs.push({start:text.length, end:text.length + element.value.length, element});
      text += element.value;
    }
    const append = (start, end, marked = false) => {
      for (const run of runs) {
        const left = Math.max(start, run.start), right = Math.min(end, run.end);
        if (right > left) result.push({...run.element, value:text.slice(left,right),
          ...(marked ? {underline:true} : {})});
      }
    };
    let position = 0;
    for (const match of text.matchAll(TOKEN)) {
      append(position, match.index);
      if (match[0].startsWith('\\')) append(match.index + 1, match.index + match[0].length);
      else {
        const original = runs.find(run => run.end > match.index).element;
        result.push({...original,value:`(${sequence.number++}) `,underline:false});
        append(match.index + 2, match.index + match[0].length - 2, true);
      }
      position = match.index + match[0].length;
    }
    append(position, text.length);
  }
  return result;
}

export function configureTranslation(editor, state) {
  if (!state.translationOriginal) state.translationOriginal = JSON.parse(JSON.stringify(editor.command.getValue().data));
  const data = JSON.parse(JSON.stringify(state.translationOriginal));
  data.main = transform(data.main, {number:1});
  editor.command.executeSetValue(data);
}
