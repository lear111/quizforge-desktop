import './summary.css';

/** Trusted score panel shares the existing browser and navigation, without an extension frame. */
export function mountSummary(onRestart) {
  const root=document.createElement('section');root.className='learning-summary';root.hidden=true;
  root.innerHTML='<article class="learning-summary-card"><header><span class="tag">本次练习</span><span data-total></span></header><div class="learning-summary-results"><div class="learning-summary-ring"><div><small>得分</small><strong data-score></strong><small data-maximum></small></div></div><dl></dl></div><button type="button" class="practice-submit" data-restart>↻ 重新练习</button></article>';
  root.querySelector('[data-restart]').onclick=onRestart;document.body.append(root);
  root.querySelector('[data-restart]').hidden=!onRestart;
  return {show(value){
    // Keep the score panel on the same paper as the preceding question.
    const viewport=document.querySelector('#viewport');
    if(viewport){const paper=getComputedStyle(viewport);for(const property of ['backgroundColor','backgroundImage','backgroundSize','backgroundPosition'])root.style[property]=paper[property];}
    root.querySelector('[data-total]').textContent=`共 ${value.total} 题`;
    root.querySelector('[data-score]').textContent=value.score;
    root.querySelector('[data-maximum]').textContent=`/ ${value.maximum}`;
    const legend=root.querySelector('dl');legend.replaceChildren();
    for(const [label,count,color] of [['正确',value.correct,'#3cc7a0'],['错误',value.incorrect,'#de6c55'],['未作答',value.unfinished,'#d6d2cc'],['未评分',value.unscored,'#b6accc']]){
      if(label==='未评分'&&!count)continue;
      const row=document.createElement('div'),name=document.createElement('dt'),number=document.createElement('dd');
      row.style.color=color;name.textContent=label;number.textContent=count;row.append(name,number);legend.append(row);
    }
    const correct=value.total?value.correct/value.total*100:0,incorrect=value.total?value.incorrect/value.total*100:0;
    root.querySelector('.learning-summary-ring').style.background=`conic-gradient(#3cc7a0 0 ${correct}%,#de6c55 ${correct}% ${correct+incorrect}%,#d6d2cc ${correct+incorrect}% 100%)`;
    root.hidden=false;
  },hide(){root.hidden=true;},setBusy(value){root.querySelector('[data-restart]').disabled=value;}};
}
