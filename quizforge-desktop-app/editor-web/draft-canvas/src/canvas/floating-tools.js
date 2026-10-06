const icon=path=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
const paths={
 INTERACT:'<path d="m5 3 14 10-7 1-3 7z"/>',PAN:'<path d="M8 12V6a2 2 0 0 1 4 0v5-7a2 2 0 0 1 4 0v7-4a2 2 0 0 1 4 0v7c0 5-3 7-7 7-3 0-5-2-7-5l-3-4a2 2 0 0 1 3-2l2 2"/>',
 PEN:'<path d="m4 20 1-5L16 4a2 2 0 0 1 4 4L9 19zM14 6l4 4"/>',ERASER:'<path d="m4 13 9-9a2 2 0 0 1 3 0l5 5a2 2 0 0 1 0 3l-8 8H8l-4-4a2 2 0 0 1 0-3zM10 7l9 9M13 20h8"/>',
 LINE:'<path d="M4 20 20 4"/>',RECT:'<rect x="4" y="5" width="16" height="14" rx="1"/>',TEXT:'<path d="M4 5h16M12 5v15M8 20h8"/>',
 undo:'<path d="m8 5-5 5 5 5M3 10h10a7 7 0 0 1 7 7"/>',redo:'<path d="m16 5 5 5-5 5M21 10H11a7 7 0 0 0-7 7"/>',more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>'
};
export function mountFloatingTools(root){
 const labels={INTERACT:'选择',PAN:'拖动',PEN:'画笔',ERASER:'橡皮',LINE:'直线',RECT:'矩形',TEXT:'文本'};
 const toolbar=root.querySelector('.toolbar');toolbar.classList.add('floating-toolbar');
 toolbar.innerHTML=`<div class="floating-group history-tools">${['undo','redo'].map(id=>`<button type="button" id="${id}" aria-label="${id==='undo'?'撤销':'重做'}" title="${id==='undo'?'撤销 Ctrl+Z':'重做 Ctrl+Shift+Z'}" disabled>${icon(paths[id])}</button>`).join('')}</div>
 <nav class="floating-group drawing-tools" aria-label="白板工具">${Object.entries(labels).map(([mode,label])=>`<button type="button" data-mode="${mode}" aria-pressed="${mode==='INTERACT'}" title="${mode==='PAN'?'拖动画布：按住鼠标左键拖动':label}" aria-label="${label}">${icon(paths[mode])}<span>${label}</span></button>`).join('')}</nav>
 <div class="paper-tools"><button type="button" id="board-more" class="floating-group" aria-expanded="false" aria-controls="board-menu" title="更多" aria-label="更多">${icon(paths.more)}</button>
 <section id="board-menu" class="floating-group board-menu" hidden aria-label="白板设置"><strong>白板底色</strong><div class="paper-colors">${[['#f5f4f7','浅灰'],['#ffffff','白色'],['#fff7dc','纸黄色'],['#f6eee3','米色'],['#edf4ee','浅绿']].map(([color,label])=>`<button type="button" data-paper-color="${color}" style="--paper-swatch:${color}" title="${label}" aria-label="${label}" aria-pressed="false"></button>`).join('')}</div><strong>白板样式</strong><div class="paper-patterns">${[['PLAIN','空白'],['LINES','横线'],['DOTS','点式'],['GRID','格子']].map(([pattern,label])=>`<button type="button" data-paper-pattern="${pattern}" aria-pressed="false"><span class="paper-preview" data-pattern="${pattern}"></span>${label}</button>`).join('')}</div></section></div>
 <div hidden><button id="clear" disabled></button><button id="export"></button><button id="import"></button></div>`;
 const footer=root.querySelector('footer');footer.classList.add('floating-footer');
 footer.innerHTML='<span id="mode-help" class="visually-hidden"></span><div class="floating-group zoom-actions" aria-label="视口缩放"><button id="zoom-out" type="button" aria-label="缩小">−</button><button id="zoom-value" type="button" title="恢复 100%">100%</button><button id="zoom-in" type="button" aria-label="放大">+</button><button id="zoom-fit" type="button" hidden></button></div><span id="status" class="visually-hidden" role="status"></span>';
}
