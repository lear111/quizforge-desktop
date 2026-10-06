const keys = ['cardWidth','maxCardWidth','horizontalAlign','verticalAlign','padding'];
export function defaultLayout(mode = 'PRACTICE') {
  return Object.freeze({cardWidth:720,maxCardWidth:'100%',horizontalAlign:'center',verticalAlign:mode==='EDITOR'?'top':'center',padding:20});
}
export function configureLayout(current, patch) {
  if (!patch || typeof patch !== 'object' || Array.isArray(patch)) throw new TypeError('布局配置必须为对象');
  if (Object.keys(patch).some(key=>!keys.includes(key))) throw new TypeError('未知布局字段');
  const next = {...current,...patch};
  if (!Number.isFinite(next.cardWidth) || next.cardWidth<64 || next.cardWidth>8192) throw new TypeError('cardWidth 必须为 64–8192 的数值');
  if (typeof next.maxCardWidth === 'number') {
    if (!Number.isFinite(next.maxCardWidth) || next.maxCardWidth<64 || next.maxCardWidth>8192) throw new TypeError('maxCardWidth 数值必须为 64–8192');
  } else if (typeof next.maxCardWidth !== 'string' || !/^(?:\d+(?:\.\d+)?)%$/.test(next.maxCardWidth) || parseFloat(next.maxCardWidth)<=0 || parseFloat(next.maxCardWidth)>100) {
    throw new TypeError('maxCardWidth 必须为数值或大于 0、不超过 100% 的百分比');
  }
  if (!['left','center','right'].includes(next.horizontalAlign) || !['top','center','bottom'].includes(next.verticalAlign)) throw new TypeError('不支持的对齐方式');
  if (!Number.isFinite(next.padding) || next.padding<0 || next.padding>256) throw new TypeError('padding 必须为 0–256 的数值');
  return Object.freeze(next);
}
export function isolatedLayoutSource(){return `const keys=${JSON.stringify(keys)};\n${configureLayout.toString()}`;}
export function availableCardWidth(layout, width) {
  const available = Math.max(1,width-2*layout.padding);
  return Math.min(available,typeof layout.maxCardWidth==='number'?layout.maxCardWidth:available*parseFloat(layout.maxCardWidth)/100);
}
export function initialCardWidth(layout, width) {
  return Math.max(1,Math.min(layout.cardWidth,availableCardWidth(layout,width)));
}
export function alignedOffset(align, available, content, padding) {
  return Math.max(padding,align==='right'||align==='bottom'?available-padding-content:align==='center'?(available-content)/2:padding);
}

/** Plain editor/Hub/workbench pages use natural flow, with no platform-specific CSS in a type. */
export function createDomLayout(target, {mode='PRACTICE',getHeight=()=>window.innerHeight} = {}) {
  let layout=defaultLayout(mode),destroyed=false;
  const properties=['width','max-width','box-sizing','margin-left','margin-right','margin-top','margin-bottom'];
  const original=new Map(properties.map(key=>[key,[target.style.getPropertyValue(key),target.style.getPropertyPriority(key)]]));
  const set=(key,value)=>{if(target.style.getPropertyValue(key)!==value)target.style.setProperty(key,value);};
  function render() {
    if(destroyed)return;
    const width=target.parentElement?.clientWidth || window.innerWidth || layout.cardWidth;
    const cardWidth=initialCardWidth(layout,width);
    set('box-sizing','border-box');set('width',cardWidth+'px');set('max-width','100%');
    const left=alignedOffset(layout.horizontalAlign,width,cardWidth,layout.padding);
    set('margin-left',left+'px');set('margin-right','0px');
    set('margin-top',alignedOffset(layout.verticalAlign,getHeight(),target.offsetHeight,layout.padding)+'px');
    set('margin-bottom',layout.padding+'px');
  }
  const observer=typeof ResizeObserver==='undefined'?null:new ResizeObserver(render);
  if(target.parentElement)observer?.observe(target.parentElement);
  observer?.observe(target);
  window.addEventListener('resize',render);
  return Object.freeze({
    configure(next){layout=next;render();},
    getState(){return {configuration:{...layout},cardWidth:target.getBoundingClientRect().width,hasSavedGeometry:false};},
    destroy(){destroyed=true;observer?.disconnect();window.removeEventListener('resize',render);original.forEach(([value,priority],key)=>value?target.style.setProperty(key,value,priority):target.style.removeProperty(key));}
  });
}
