import './editor.css';
import Editor from '@hufe921/canvas-editor';
import undo from './icons/undo.svg?raw';
import redo from './icons/redo.svg?raw';
import format from './icons/format.svg?raw';
import sizeAdd from './icons/size-add.svg?raw';
import sizeMinus from './icons/size-minus.svg?raw';
import bold from './icons/bold.svg?raw';
import italic from './icons/italic.svg?raw';
import underline from './icons/underline.svg?raw';
import strike from './icons/strikeout.svg?raw';
import superscript from './icons/superscript.svg?raw';
import subscript from './icons/subscript.svg?raw';
import color from './icons/color.svg?raw';
import highlight from './icons/highlight.svg?raw';
import title from './icons/title.svg?raw';
import left from './icons/left.svg?raw';
import center from './icons/center.svg?raw';
import right from './icons/right.svg?raw';
import justify from './icons/justify.svg?raw';
import list from './icons/list.svg?raw';
import image from './icons/image.svg?raw';
import hyperlink from './icons/hyperlink.svg?raw';
import table from './icons/table.svg?raw';
import latex from './icons/latex.svg?raw';

const PAPER_WIDTH = 816;
const CONTENT_WIDTH = Number(new URLSearchParams(window.location.hash.slice(1)).get('contentWidth')
  || new URLSearchParams(window.location.search).get('contentWidth')) || 672;
const SIDE_MARGIN = (PAPER_WIDTH - CONTENT_WIDTH) / 2;
// Preserve the Canvas engine, draft and Java bridge API across module updates.
const state = window.__quizforgeCanvasState || (window.__quizforgeCanvasState = {editor: null, changed: 0, fault: ''});
let editor = state.editor;
const icons = {undo,redo,format,sizeAdd,sizeMinus,bold,italic,underline,strike,superscript,subscript,
  color,highlight,title,left,center,right,justify,list,image,hyperlink,table,latex};

function wireKeyboard() {
  // JavaFX WebKit supplies keyCode/keyIdentifier but can leave KeyboardEvent.key empty.
  // Normalize the original event before Canvas handles it; keep native input/IME intact.
  if (state.keyboardHandler) {
    document.removeEventListener('keydown', state.keyboardHandler, true);
    document.removeEventListener('keyup', state.keyboardHandler, true);
  }
  state.keyboardHandler = event => {
    if (!event.target.classList || !event.target.classList.contains('ce-inputarea')) return;
    if (event.key && event.key !== 'Unidentified') return;
    const names = {8:'Backspace',9:'Tab',13:'Enter',16:'Shift',17:'Control',18:'Alt',27:'Escape',
      33:'PageUp',34:'PageDown',35:'End',36:'Home',37:'ArrowLeft',38:'ArrowUp',39:'ArrowRight',40:'ArrowDown',
      45:'Insert',46:'Delete',91:'Meta',93:'ContextMenu'};
    let key = names[event.keyCode];
    if (!key && event.keyIdentifier && /^U\+[0-9A-F]{4,6}$/i.test(event.keyIdentifier))
      key = String.fromCodePoint(parseInt(event.keyIdentifier.slice(2), 16));
    if (!key && event.keyCode >= 65 && event.keyCode <= 90)
      key = String.fromCharCode(event.keyCode);
    if (key) Object.defineProperty(event, 'key', {value: key, configurable: true});
  };
  document.addEventListener('keydown', state.keyboardHandler, true);
  document.addEventListener('keyup', state.keyboardHandler, true);
}

function wireToolbar() {
  // Fresh toolbar nodes prevent duplicate listeners after an accepted JS update.
  const toolbar = document.getElementById("toolbar");
  toolbar.replaceWith(toolbar.cloneNode(true));
  document.querySelectorAll('[data-icon]').forEach(button => {
    button.innerHTML = icons[button.dataset.icon];
    // Formatting buttons must retain the Canvas input focus and selection.
    button.addEventListener('mousedown', event => event.preventDefault());
  });
  document.querySelectorAll('[data-command]').forEach(button => {
    button.addEventListener('click', () => window.canvasEditor.command(button.dataset.command, button.dataset.value || null));
  });
  document.querySelectorAll('[data-select]').forEach(select => {
    select.addEventListener('change', () => window.canvasEditor.command(select.dataset.select, select.value));
  });
  for (const name of ['color','highlight']) {
    document.getElementById(name).addEventListener('input', e => window.canvasEditor.command(name,e.target.value));
  }
  document.getElementById('insert-link').addEventListener('click', () => {
    const url = window.prompt('链接地址'); if (url) window.canvasEditor.command('link',url);
  });
  document.getElementById('insert-latex').addEventListener('click', () => {
    const tex = window.prompt('LaTeX 公式'); if (tex) window.canvasEditor.command('latex',tex);
  });
  document.getElementById('insert-image').addEventListener('click', () => {
    if (window.quizforgeHost && window.quizforgeHost.chooseImage) window.quizforgeHost.chooseImage();
  });
}

function initialize() {
  try {
    if (!editor) editor = new Editor(document.getElementById('paper'), { main: [{ value: '' }] }, {
      mode: 'edit', pageMode: 'continuity', width: PAPER_WIDTH, height: 640,
      margins: [72, SIDE_MARGIN, 72, SIDE_MARGIN], marginIndicatorSize: 0,
      defaultFont: 'Arial', defaultSize: 16, scrollContainerSelector: '#workspace',
      pageNumber: { disabled: true }, header: { disabled: true }, footer: { disabled: true },
      watermark: { disabled: true }, ruler: { disabled: true }
    });
    state.editor = editor;
    editor.listener.contentChange = () => { state.changed++; };
    wireKeyboard();
    wireToolbar();
    editor.listener.rangeStyleChange = style => {
      for (const name of ['bold','italic','underline']) {
        document.querySelector(`[data-command="${name}"]`).classList.toggle('selected',!!style[name]);
      }
      document.querySelector('[data-command="strike"]').classList.toggle('selected',!!style.strikeout);
      document.querySelectorAll('[data-command="align"]').forEach(button =>
        button.classList.toggle('selected',button.dataset.value === style.rowFlex));
      document.querySelectorAll('[data-command="list"]').forEach(button =>
        button.classList.toggle('selected',button.dataset.value === style.listType));
      if (style.level !== undefined) document.querySelector('[data-select="heading"]').value = style.level || '';
      if (style.font && [...document.querySelector('[data-select="font"]').options].some(o => o.value === style.font))
        document.querySelector('[data-select="font"]').value = style.font;
      if (style.size && [...document.querySelector('[data-select="size"]').options].some(o => Number(o.value) === style.size))
        document.querySelector('[data-select="size"]').value = String(style.size);
    };
    document.documentElement.dataset.editorReady = 'true';
  } catch (e) {
    state.fault = String(e && (e.stack || e.message) || e);
    document.documentElement.dataset.editorError = state.fault;
  }
  if (window.quizforgeHost && window.quizforgeHost.editorReady) window.quizforgeHost.editorReady();
}

window.canvasEditor = Object.assign(window.canvasEditor || {}, {
  ready: () => !!editor,
  error: () => state.fault,
  changed: () => state.changed,
  configuration: () => JSON.stringify({ paperWidth: PAPER_WIDTH, margin: SIDE_MARGIN, contentWidth: CONTENT_WIDTH, pageMode: 'continuity' }),
  load: json => { editor.command.executeSetValue(JSON.parse(json)); },
  value: () => JSON.stringify(editor.command.getValue().data),
  mode: value => { editor.command.executeMode(value); },
  insertImage: json => { editor.command.executeImage(JSON.parse(json)); },
  insertElement: json => { editor.command.executeInsertElementList(JSON.parse(json)); },
  command: (name, value) => {
    const commands = {
      undo: 'executeUndo', redo: 'executeRedo', bold: 'executeBold', italic: 'executeItalic',
      underline: 'executeUnderline', strike: 'executeStrikeout', clear: 'executeFormat',
      sizeAdd: 'executeSizeAdd', sizeMinus: 'executeSizeMinus',
      superscript: 'executeSuperscript', subscript: 'executeSubscript'
    };
    if (commands[name]) return editor.command[commands[name]]();
    if (name === 'font') return editor.command.executeFont(value);
    if (name === 'size') return editor.command.executeSize(Number(value));
    if (name === 'color') return editor.command.executeColor(value);
    if (name === 'highlight') return editor.command.executeHighlight(value);
    if (name === 'heading') return editor.command.executeTitle(value || null);
    if (name === 'align') return editor.command.executeRowFlex(value);
    if (name === 'list') return editor.command.executeList(value || null, value === 'ol' ? 'decimal' : 'disc');
    if (name === 'table') return editor.command.executeInsertTable(2, 2);
    if (name === 'link') return editor.command.executeHyperlink({ url: value, valueList: [{ value: value }] });
    if (name === 'latex') return editor.command.executeInsertElementList([{ type: 'latex', value: value }]);
    return false;
  },
  destroy: () => { if (editor) editor.destroy(); editor = state.editor = null; document.documentElement.dataset.editorReady = 'false'; }
});
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize, {once: true});
else initialize();

if (import.meta.hot) {
  import.meta.hot.accept();
}
