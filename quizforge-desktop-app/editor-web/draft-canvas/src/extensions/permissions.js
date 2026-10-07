export const permissionNames=Object.freeze([
  'question.edit','bank.save','bank.add','bank.duplicate','bank.delete','bank.move','answer.write',
  'practice.submit','practice.retry','navigation','sources.open','sources.manage','learning.mode',
  'whiteboard.tools','whiteboard.history','whiteboard.clear','whiteboard.appearance','whiteboard.zoom'
]);
export function readPermissions(value){
  if(value==null)return [];
  if(!Array.isArray(value)||value.some(name=>!permissionNames.includes(name)))throw new TypeError('未知或无效的拓展权限');
  return [...new Set(value)];
}
const requirements=Object.freeze({
  'editor.update':'question.edit','content.edit':'question.edit',
  'bank.save':'bank.save','bank.duplicateQuestion':'bank.duplicate','bank.deleteQuestion':'bank.delete',
  'page.add':'bank.add','page.move':'bank.move',
  'answer.update':'answer.write','practice.submit':'practice.submit','practice.retry':'practice.retry',
  'navigation.goTo':'navigation','navigation.previous':'navigation','navigation.next':'navigation','page.attempt':'navigation',
  'sources.open':'sources.open','sources.add':'sources.manage','sources.remove':'sources.manage',
  'learning.setMode':'learning.mode'
});
const publicMethods=new Set(['page.load','page.save','page.action','content.resolve','ui.configure','layout.configure']);
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
