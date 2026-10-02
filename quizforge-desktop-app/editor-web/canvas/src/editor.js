import './editor.css';
import Editor, {createDomFromElementList, getElementListByHTML, getTextFromElementList, splitText} from '@hufe921/canvas-editor';
import {installCanvasFontCompatibility} from './canvas-webview-compat';
import {configureCloze} from './cloze-preview';
import {configureTranslation} from './translation-preview';
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
import indent from './icons/indent.svg?raw';
import outdent from './icons/outdent.svg?raw';
import list from './icons/list.svg?raw';
import image from './icons/image.svg?raw';
import hyperlink from './icons/hyperlink.svg?raw';
import table from './icons/table.svg?raw';
import latex from './icons/latex.svg?raw';

const PAPER_WIDTH = 816;
const MIN_ZOOM = 0.75;
const MAX_ZOOM = 1.5;
const CONTENT_WIDTH = Number(new URLSearchParams(window.location.hash.slice(1)).get('contentWidth')
  || new URLSearchParams(window.location.search).get('contentWidth')) || 672;
const SIDE_MARGIN = (PAPER_WIDTH - CONTENT_WIDTH) / 2;
const PREVIEW = (new URLSearchParams(window.location.hash.slice(1)).get('readonly')
  || new URLSearchParams(window.location.search).get('readonly')) === '1';
// Preserve the Canvas engine, draft and Java bridge API across module updates.
const state = window.__quizforgeCanvasState || (window.__quizforgeCanvasState = {editor: null, changed: 0, fault: ''});
let editor = state.editor;
const icons = {undo,redo,format,sizeAdd,sizeMinus,bold,italic,underline,strike,superscript,subscript,
  color,highlight,title,left,center,right,justify,indent,outdent,list,image,hyperlink,table,latex};

const paragraphTextTypes = new Set([undefined,'text','superscript','subscript']);
const indentMark = 'quizforgeFirstLineIndent';
function nativeUnits(elements) {
  return elements.flatMap(element => element.valueList ? nativeUnits(element.valueList)
    : paragraphTextTypes.has(element.type) ? splitText(element.value) : ['\ufffc']);
}
function setFirstLineIndent(enabled) {
  if (PREVIEW || editor.command.getOptions().mode === 'readonly') return;
  const range = editor.command.getRange();
  if (range.startIndex < 0 || range.endIndex < 0 || range.isCrossRowCol) return;
  const paragraphs = editor.command.getRangeParagraph();
  if (!paragraphs?.length || !nativeUnits(paragraphs).length) {
    if (enabled) editor.command.executeInsertElementList([{value:'\u3000\u3000',extension:{[indentMark]:true}}]);
    return;
  }
  // Public paragraph/surround commands return native runs without the first
  // paragraph separator. Count native graphemes, not UTF-16 code units.
  const prefix = nativeUnits(editor.command.getSurroundElementList({direction:'before',length:range.startIndex+1}) || []);
  const boundary = prefix.lastIndexOf('\n');
  const start = range.startIndex - (prefix.length - boundary - 1);
  const end = start + nativeUnits(paragraphs).length;
  const result = [];const edits = [];let offset = 0;let atStart = true;
  for (const element of paragraphs) {
    if (!paragraphTextTypes.has(element.type)) {
      // Keep standalone images/tables unchanged. An inline link can still
      // receive a prefix while its valueList retains its original styling.
      if (atStart && enabled && ['hyperlink','date'].includes(element.type)) {
        const style = element.valueList?.[0] || element;
        result.push({font:style.font,size:style.size,rowFlex:element.rowFlex,rowMargin:element.rowMargin,
          value:'\u3000\u3000',extension:{[indentMark]:true}});
        edits.push({index:start+offset,delta:2});
      }
      result.push(element);offset += nativeUnits([element]).length;atStart = false;continue;
    }
    const parts = element.value.split('\n');
    for (let i = 0; i < parts.length; i++) {
      if (i) {result.push({...element,value:'\n'});offset++;atStart = true;}
      const value = parts[i];if (!value) continue;
      const marked = !!element.extension?.[indentMark];
      if (atStart && marked) {
        if (!enabled) {
          const padding = value.match(/^\u3000{1,2}/)?.[0] || '';
          if (padding) edits.push({index:start+offset,delta:-padding.length});
          if (value.length > padding.length) result.push({...element,value:value.slice(padding.length)});
          // Native runs may split the two spaces when their styles differ.
          // Keep removing tagged padding until the paragraph text starts.
          offset += splitText(value).length;atStart = value.length === padding.length;continue;
        }
      } else if (atStart && enabled) {
        // Tagged native full-width spaces follow the paragraph's font size,
        // survive native save/clipboard/undo, and affect only its first line.
        result.push({font:element.font,size:element.size,rowFlex:element.rowFlex,rowMargin:element.rowMargin,
          value:'\u3000\u3000',extension:{[indentMark]:true}});
        edits.push({index:start+offset,delta:2});
      }
      result.push({...element,value});offset += splitText(value).length;atStart = false;
    }
  }
  if (!edits.length) return;
  editor.command.executeSetRange(start,end);
  // Replacing this paragraph range once creates one undo step. Do not let
  // insertion-context inheritance overwrite copied fonts, lists or colors.
  if (!result.length) editor.command.executeBackspace();
  else editor.command.executeInsertElementList(result,{ignoreContextKeys:[
    'font','size','bold','color','italic','highlight','underline','strikeout','rowFlex','rowMargin',
    'width','height','level','titleId','title','listId','listType','listStyle','listLevel',
    'areaId','area','controlId','controlComponent','tdId','trId','tableId']});
  const mapped = index => edits.reduce((position,edit) => index >= edit.index
    ? position + (edit.delta < 0 ? -Math.min(-edit.delta,index-edit.index) : edit.delta) : position,index);
  editor.command.executeSetRange(mapped(range.startIndex),mapped(range.endIndex));
  document.querySelector('.ce-inputarea').focus();
}

function wireKeyboard() {
  // JavaFX WebKit supplies keyCode/keyIdentifier but can leave KeyboardEvent.key empty.
  // Normalize the original event before Canvas handles it; keep native input/IME intact.
  if (state.keyboardHandler) {
    document.removeEventListener('keydown', state.keyboardHandler, true);
    document.removeEventListener('keyup', state.keyboardHandler, true);
  }
  state.keyboardHandler = event => {
    if (!event.target.classList || !event.target.classList.contains('ce-inputarea')) return;
    const names = {8:'Backspace',9:'Tab',13:'Enter',16:'Shift',17:'Control',18:'Alt',27:'Escape',
      33:'PageUp',34:'PageDown',35:'End',36:'Home',37:'ArrowLeft',38:'ArrowUp',39:'ArrowRight',40:'ArrowDown',
      45:'Insert',46:'Delete',91:'Meta',93:'ContextMenu'};
    let key = event.key && event.key !== 'Unidentified' ? event.key : names[event.keyCode];
    if (!key && event.keyCode >= 65 && event.keyCode <= 90)
      key = String.fromCharCode(event.keyCode);
    if (!key && event.keyIdentifier && /^U\+[0-9A-F]{4,6}$/i.test(event.keyIdentifier))
      key = String.fromCodePoint(parseInt(event.keyIdentifier.slice(2), 16));
    if (key && key !== event.key) Object.defineProperty(event, 'key', {value: key, configurable: true});
    const shortcut = key && key.toLowerCase();
    if (event.type === 'keydown' && (event.ctrlKey || event.metaKey) && !event.altKey
        && ['c','x','v'].includes(shortcut) && hasNativeClipboard()) {
      event.preventDefault(); event.stopImmediatePropagation();
      try {
        if (shortcut === 'v') pasteNativeClipboard(event.shiftKey);
        else {
          const selected = editor.command.getRangeContext();
          if (copyNativeClipboard() && shortcut === 'x' && selected && !selected.isCollapsed)
            editor.command.executeBackspace();
        }
      } catch (error) { window.quizforgeHost.clipboardError('剪贴板操作失败：' + error.message); }
    }
  };
  document.addEventListener('keydown', state.keyboardHandler, true);
  document.addEventListener('keyup', state.keyboardHandler, true);
}

function hasNativeClipboard() {
  return window.quizforgeHost && window.quizforgeHost.readClipboard && window.quizforgeHost.writeClipboard;
}

function copyNativeClipboard() {
  const range = editor.command.getRangeContext();
  const elements = range && (range.isCollapsed ? editor.command.getRangeRow() : range.selectionElementList);
  if (!elements || !elements.length) return false;
  const clone = JSON.parse(JSON.stringify(elements));
  const dom = createDomFromElementList(clone, editor.command.getOptions());
  const text = getTextFromElementList(clone);
  const html = dom.innerHTML;
  if (!window.quizforgeHost.writeClipboard(text, html)) return false;
  state.clipboard = {text, html, elements: clone};
  document.querySelector('.ce-inputarea').focus();
  return true;
}

function pasteNativeClipboard(plainText = false) {
  // Use Canvas commands so its readonly and disabled-range checks still apply.
  const clipboard = JSON.parse(window.quizforgeHost.readClipboard());
  let elements;
  if (!plainText && state.clipboard && clipboard.text === state.clipboard.text && clipboard.html === state.clipboard.html)
    elements = JSON.parse(JSON.stringify(state.clipboard.elements));
  else if (!plainText && clipboard.image) {
    window.canvasEditor.insertImage(JSON.stringify(clipboard.image));return;
  }
  else if (!plainText && clipboard.html) {
    // External clipboard HTML is content, never executable markup or a remote image import.
    const dom = new DOMParser().parseFromString(clipboard.html, 'text/html');
    if ([...dom.querySelectorAll('img')].some(node => !/^data:image\/(png|jpeg);base64,/i.test(node.getAttribute('src') || '')))
      throw new Error('图片未包含在剪贴板中，请复制图片本身或使用“插入图片”。');
    dom.querySelectorAll('script,style,iframe,object,embed,video,audio,link,meta').forEach(node => node.remove());
    dom.body.querySelectorAll('*').forEach(node => {
      [...node.attributes].forEach(attr => { if (/^on/i.test(attr.name)) node.removeAttribute(attr.name); });
    });
    elements = getElementListByHTML(dom.body.innerHTML, {innerWidth: CONTENT_WIDTH});
  } else if (clipboard.text) elements = [{value: clipboard.text.replace(/\r\n?/g, '\n')}];
  if (elements && elements.length) editor.command.executeInsertElementList(elements);
  document.querySelector('.ce-inputarea').focus();
}

function wireClipboard() {
  editor.override.copy = () => {
    if (!hasNativeClipboard()) return {preventDefault: false};
    copyNativeClipboard();return {preventDefault: true};
  };
  editor.override.paste = () => {
    if (!hasNativeClipboard()) return {preventDefault: false};
    pasteNativeClipboard();return {preventDefault: true};
  };
}

function wireToolbar() {
  // Fresh toolbar nodes prevent duplicate listeners after an accepted JS update.
  // Canvas recognizes editor-component="menu" as part of the editor. Without
  // it, dropdown mousedown resets range styles to defaults before change fires.
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
    installCanvasFontCompatibility();
    if (PREVIEW) document.documentElement.classList.add('preview');
    // Also wrap an existing editor during HMR without replacing its document.
    const paper = document.getElementById('paper');
    if (!document.getElementById('paper-stage')) {
      const stage = document.createElement('div');
      stage.id = 'paper-stage';
      paper.before(stage);
      stage.append(paper);
    }
    if (!editor) editor = new Editor(document.getElementById('paper'), { main: [{ value: '' }] }, {
      mode: 'edit', pageMode: 'continuity', width: PAPER_WIDTH, height: 640,
      margins: [72, SIDE_MARGIN, 72, SIDE_MARGIN], marginIndicatorSize: 0,
      defaultFont: 'Arial', defaultSize: 16, scrollContainerSelector: '#workspace',
      pageNumber: { disabled: true }, header: { disabled: true }, footer: { disabled: true },
      watermark: { disabled: true }, ruler: { disabled: true }, magnifier: { disabled: true },
      ...(PREVIEW ? previewOptions() : {})
    });
    state.editor = editor;
    if (PREVIEW) editor.command.executeUpdateOptions(previewOptions());
    editor.listener.contentChange = () => {
      state.changed++; reportHeight();
      if(state.clozeDraw)requestAnimationFrame(state.clozeDraw);
      const fingerprint = JSON.stringify(editor.command.getValue().data);
      if (fingerprint === state.answerFingerprint) return;
      state.answerFingerprint = fingerprint;
      if (!PREVIEW && window.quizforgeHost && window.quizforgeHost.contentChanged)
        window.quizforgeHost.contentChanged();
    };
    if (state.heightObserver) state.heightObserver.disconnect();
    if (PREVIEW) {
      // Images and fonts may finish layout after the initial document load.
      state.heightObserver = new ResizeObserver(reportHeight);
      state.heightObserver.observe(paper);
      if (state.previewResize) window.removeEventListener('resize',state.previewResize);
      state.previewResize=fitPreview;
      window.addEventListener('resize',state.previewResize);
    }
    editor.listener.pageScaleChange = scale => {
      const bounded = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale));
      if (!PREVIEW && bounded !== scale) editor.command.executePageScale(bounded);
      else syncPaperLayout(true);
    };
    editor.listener.pageSizeChange = () => syncPaperLayout();
    if (!PREVIEW) editor.command.executePageScale(Math.max(MIN_ZOOM,
      Math.min(MAX_ZOOM, editor.command.getOptions().scale)));
    syncPaperLayout(true);
    if (PREVIEW) fitPreview();
    wireKeyboard();
    wireClipboard();
    wireToolbar();
    wireZoom();
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
      if (style.size) {
        const sizes = document.querySelector('[data-select="size"]');
        // Pasted text may use a size outside the presets. Show its actual size
        // so choosing 16 still changes the select's value and applies formatting.
        sizes.querySelector('[data-current-size]')?.remove();
        if (![...sizes.options].some(o => Number(o.value) === style.size)) {
          const current = new Option(String(style.size), String(style.size));
          current.dataset.currentSize = 'true'; sizes.add(current);
        }
        sizes.value = String(style.size);
      }
    };
    document.documentElement.dataset.editorReady = 'true';
  } catch (e) {
    state.fault = String(e && (e.stack || e.message) || e);
    document.documentElement.dataset.editorError = state.fault;
  }
  if (window.quizforgeHost && window.quizforgeHost.editorReady) window.quizforgeHost.editorReady();
}

function previewOptions() {
  // Keep full-width table borders inside the canvas rather than on its clipping edge.
  return {mode:'readonly',width:CONTENT_WIDTH+2,height:1,margins:[0,1,0,1],marginIndicatorSize:0,pageMode:'continuity',scale:1,
    background:{color:'transparent',image:''},
    header:{disabled:true},footer:{disabled:true},pageNumber:{disabled:true},watermark:{disabled:true},ruler:{disabled:true},magnifier:{disabled:true},shortcutDisableKeys:['pageScale']};
}
function wireZoom() {
  const workspace = document.getElementById('workspace');
  if (state.zoomHandler) workspace.removeEventListener('wheel', state.zoomHandler, true);
  state.zoomHandler = event => {
    if (PREVIEW) {
      if (event.ctrlKey || event.shiftKey || event.deltaX) event.preventDefault();
      return;
    }
    if (!event.ctrlKey || !event.deltaY) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    const scale = editor.command.getOptions().scale;
    editor.command.executePageScale(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
      Math.round((scale + (event.deltaY < 0 ? 0.05 : -0.05)) * 100) / 100)));
  };
  workspace.addEventListener('wheel', state.zoomHandler, {capture:true, passive:false});
}
function focusDocument() {
  if (PREVIEW) fitPreview();
  if (!PREVIEW) {
    const scale = editor.command.getOptions().scale;
    editor.command.executePageScale(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale)));
    editor.command.executeSetRange(0,0);
    document.querySelector('.ce-inputarea').focus();
  }
  syncPaperLayout(true);
  reportHeight();
}
function fitPreview() {
  if (!PREVIEW || !editor) return;
  const width=Math.max(1,document.documentElement.clientWidth);
  const scale=Math.min(1,width/(CONTENT_WIDTH+2));
  if(Math.abs(editor.command.getOptions().scale-scale)>0.000001)editor.command.executePageScale(scale);
  syncPaperLayout();
  if(state.clozeDraw)requestAnimationFrame(state.clozeDraw);
  reportHeight();
}
function syncPaperLayout(center = false) {
  const paper = document.getElementById('paper');
  // Canvas owns scale and page geometry; its wrapper already includes zoom.
  // Do not apply a second CSS transform or change the document's font sizes.
  const width = parseFloat(paper.firstElementChild.style.width);
  paper.style.width = `${width}px`;
  document.getElementById('paper-stage').style.width = `${width + 48}px`;
  if (PREVIEW) {
    for (const viewport of [document.documentElement, document.body, document.getElementById('workspace')])
      viewport.scrollLeft = viewport.scrollTop = 0;
  }
  if (center && !PREVIEW) {
    const workspace = document.getElementById('workspace');
    workspace.scrollLeft = Math.max(0, (workspace.scrollWidth - workspace.clientWidth) / 2);
  }
}
function reportHeight() {
  if (!PREVIEW) return;
  requestAnimationFrame(() => {
    if (window.quizforgeHost && window.quizforgeHost.contentHeight)
      window.quizforgeHost.contentHeight(document.getElementById('paper').clientHeight);
  });
}
function insertNativeImage(image) {
  if (PREVIEW || editor.command.getOptions().mode === 'readonly') return;
  // Paste and file insertion use the current Canvas selection. A cancelled file
  // chooser must never leave a saved selection that a later paste can replace.
  delete state.imageRange;
  if (!Number.isFinite(image.width) || !Number.isFinite(image.height) || image.width <= 0 || image.height <= 0
      || typeof image.value !== 'string' || !image.value.startsWith('data:image/'))
    throw new Error('图片数据无法读取，原内容未修改。');
  const range = editor.command.getRange();
  if (range.startIndex < 0 || range.endIndex < 0) throw new Error('请先选择图片插入位置。');
  const width = Math.min(image.width, CONTENT_WIDTH);
  editor.command.executeInsertElementList([{...image,type:'image',width,
    height:Math.max(1,Math.round(image.height*width/image.width)),imgDisplay:image.imgDisplay || 'block'}]);
  document.querySelector('.ce-inputarea').focus();
}

window.canvasEditor = Object.assign(window.canvasEditor || {}, {
  ready: () => !!editor,
  error: () => state.fault,
  changed: () => state.changed,
  configuration: () => JSON.stringify({ paperWidth: PAPER_WIDTH, margin: SIDE_MARGIN, contentWidth: CONTENT_WIDTH, pageMode: 'continuity' }),
  load: json => {
    state.clozeOriginal=null;
    state.translationOriginal=null;
    if (PREVIEW) editor.command.executeUpdateOptions(previewOptions());
    editor.command.executeSetValue(JSON.parse(json));focusDocument();
    state.answerFingerprint = JSON.stringify(editor.command.getValue().data);
  },
  loadDocument: json => {
    state.clozeOriginal=null;
    state.translationOriginal=null;
    const document = JSON.parse(json);
    editor.command.executeUpdateOptions({...document.options,mode:PREVIEW?'readonly':'edit',magnifier:{disabled:true},...(PREVIEW?previewOptions():{})});
    editor.command.executeSetValue(document.data);focusDocument();
    state.answerFingerprint = JSON.stringify(editor.command.getValue().data);
  },
  document: () => JSON.stringify(editor.command.getValue()),
  text: () => editor.command.getText().main,
  empty: () => !editor.command.getText().main.trim()
    && !JSON.stringify(editor.command.getValue().data.main).match(/"type":"(image|latex|table)"/),
  value: () => JSON.stringify(editor.command.getValue().data),
  mode: value => { editor.command.executeMode(value); },
  cloze: json => {if(PREVIEW)configureCloze(editor,state,JSON.parse(json));},
  translation: () => {if(PREVIEW)configureTranslation(editor,state);},
  insertImage: json => { insertNativeImage(JSON.parse(json)); },
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
    if (name === 'indent') return setFirstLineIndent(true);
    if (name === 'outdent') return setFirstLineIndent(false);
    if (name === 'list') return editor.command.executeList(value || null, value === 'ol' ? 'decimal' : 'disc');
    if (name === 'table') return editor.command.executeInsertTable(2, 2);
    if (name === 'link') return editor.command.executeHyperlink({ url: value, valueList: [{ value: value }] });
    if (name === 'latex') return editor.command.executeInsertElementList([{ type: 'latex', value: value }]);
    return false;
  },
  destroy: () => {
    if (state.heightObserver) state.heightObserver.disconnect();
    if (editor) editor.destroy(); editor = state.editor = null;
    document.documentElement.dataset.editorReady = 'false';
  }
});
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize, {once: true});
else initialize();

if (import.meta.hot) {
  import.meta.hot.accept();
}
