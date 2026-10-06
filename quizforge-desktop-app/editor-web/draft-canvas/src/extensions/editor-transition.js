/** Stage a type editor at its real width without changing the visible editor's height. */
export function stageEditor(root, previous, mount, commit) {
  const body=document.createElement('div');body.className='qf-editor-body';body.style.display='flow-root';
  body.style.visibility='hidden';body.inert=true;
  root.style.position='relative';
  if(previous){
    const padding=getComputedStyle(root);
    Object.assign(body.style,{position:'absolute',left:padding.paddingLeft,right:padding.paddingRight,top:padding.paddingTop});
    previous.body.inert=true;previous.instance.suspendPresentation?.();
  }
  root.append(body);
  let instance,cancelled=false,committed=false;
  try{instance=mount(body,()=>committed&&!cancelled);}
  catch(error){body.remove();throw error;}
  function reveal(){
    if(cancelled)return;
    // The old renderer may still owe a reply to the navigation that started this switch.
    const closing=previous?.instance.destroy?.();
    if(previous?.body.querySelector('.qf-frame-retiring')){
      previous.body.dataset.qfRetiredRoot='';previous.body.style.cssText='position:absolute;visibility:hidden;pointer-events:none';
      previous.body.querySelectorAll('[id]').forEach(node=>node.removeAttribute('id'));
      Promise.resolve(closing).finally(()=>previous.body.remove());
    }else previous?.body.remove();
    committed=true;
    Object.assign(body.style,{position:'',left:'',right:'',top:'',visibility:''});body.inert=false;
    commit({body,instance});
  }
  const ready=Promise.resolve(instance.ready).then(()=>instance.flush?.())
    .then(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
    .then(reveal,error=>{if(cancelled)return;reveal();throw error;});
  ready.catch(()=>{});
  return {body,instance,ready,cancel(){if(committed||cancelled)return;cancelled=true;instance.destroy();body.remove();}};
}
