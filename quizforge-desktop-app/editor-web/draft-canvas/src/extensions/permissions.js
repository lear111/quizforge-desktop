export const permissionNames=Object.freeze([
  'question.edit','bank.save','bank.add','bank.duplicate','bank.delete','answer.write',
  'practice.submit','practice.retry','navigation','sources.open','sources.manage','learning.mode',
  'whiteboard.tools','whiteboard.history','whiteboard.clear','whiteboard.appearance','whiteboard.zoom'
]);
export function readPermissions(value){
  if(value==null)return [];
  if(!Array.isArray(value)||value.some(name=>!permissionNames.includes(name)))throw new TypeError('未知或无效的拓展权限');
  return [...new Set(value)];
}
const requirements=Object.freeze({
  'editor.update':'question.edit','editor.save':'question.edit','content.edit':'question.edit',
  'bank.save':'bank.save','bank.addQuestion':'bank.add','bank.duplicateQuestion':'bank.duplicate','bank.deleteQuestion':'bank.delete',
  'answer.update':'answer.write','answer.flush':'answer.write','practice.submit':'practice.submit','practice.retry':'practice.retry',
  'navigation.goTo':'navigation','navigation.previous':'navigation','navigation.next':'navigation',
  'sources.open':'sources.open','sources.add':'sources.manage','sources.remove':'sources.manage',
  'learning.setMode':'learning.mode','learning.toggleMode':'learning.mode',
  'whiteboard.setTool':'whiteboard.tools','whiteboard.undo':'whiteboard.history','whiteboard.redo':'whiteboard.history',
  'whiteboard.clear':'whiteboard.clear','whiteboard.setAppearance':'whiteboard.appearance','whiteboard.setZoom':'whiteboard.zoom','whiteboard.zoomBy':'whiteboard.zoom'
});
const publicMethods=new Set(['host.getContext','editor.getData','bank.getState','navigation.getState','sources.list',
  'learning.getMode','whiteboard.getState','practice.getState','practice.getQuestion','practice.getResult','answer.get',
  'content.resolve','ui.configure','ui.getConfiguration','layout.configure','layout.getConfiguration','layout.getState']);
export function createPermissionPolicy(declared,approved){
  const requested=Object.freeze(readPermissions(declared)),listeners=new Set();
  let granted=Object.freeze(readPermissions(approved).filter(name=>requested.includes(name))),allowed=new Set(granted);
  return Object.freeze({declared:requested,get granted(){return granted;},
    can:method=>publicMethods.has(method)||Boolean(requirements[method]&&allowed.has(requirements[method])),
    update(values){
      const next=readPermissions(values);
      if(next.some(name=>!requested.includes(name)))throw new TypeError('不能授予未声明的拓展权限');
      granted=Object.freeze(next);allowed=new Set(next);
      for(const listener of listeners){try{listener();}catch{/* A view notification cannot undo revocation. */}}
    },
    subscribe(listener){listeners.add(listener);return()=>listeners.delete(listener);}
  });
}
