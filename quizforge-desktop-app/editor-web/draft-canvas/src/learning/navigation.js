import './navigation.css';

/** Fixed host chrome. All four directions share one visual and their existing scoped actions. */
export function mountNavigation(onAction) {
  const root=document.createElement('nav');root.className='learning-navigation';root.dataset.qfNativeNavigation='';root.setAttribute('aria-label','题目与尝试导航');
  const buttons=new Map();
  for(const [action,label,rotation] of [['previous','上一题',180],['next','下一题',0],['previousAttempt','上一次尝试',270],['nextAttempt','下一次尝试',90]]){
    const button=document.createElement('button');button.type='button';button.className='learning-navigation-button';button.dataset.nativeAction=action;button.title=label;button.setAttribute('aria-label',label);button.hidden=true;
    button.innerHTML=`<svg viewBox="0 0 24 24" aria-hidden="true" style="transform:rotate(${rotation}deg)"><path d="M5 12h14M13 6l6 6-6 6"/></svg>`;
    button.onclick=()=>onAction(action);buttons.set(action,button);root.append(button);
  }
  document.body.append(root);
  let busy=false,attemptBusy=false;
  const available=new Map();
  return {update(message){
    const actions=message.kind==='attempt-chrome'?['previousAttempt','nextAttempt']:['previous','next'];
    if(message.kind==='attempt-chrome')attemptBusy=Boolean(message.busy);else busy=Boolean(message.busy);
    for(const action of actions)available.set(action,Boolean(message[action]));
    const locked=busy||attemptBusy;
    for(const [action,button] of buttons){
      // Preserve chrome during the transaction; publish final availability in one pass.
      if(!locked)button.hidden=!available.get(action);
      button.disabled=locked;
    }
  }};
}
