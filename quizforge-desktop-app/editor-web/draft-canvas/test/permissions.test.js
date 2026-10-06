import test from 'node:test';
import assert from 'node:assert/strict';
import {createPermissionPolicy,readPermissions,permissionNames} from '../src/extensions/permissions.js';

const methods={
  'question.edit':['editor.update','editor.save','content.edit'],
  'bank.save':['bank.save'],'bank.add':['bank.addQuestion'],'bank.duplicate':['bank.duplicateQuestion'],'bank.delete':['bank.deleteQuestion'],
  'answer.write':['answer.update','answer.flush'],'practice.submit':['practice.submit'],'practice.retry':['practice.retry'],
  navigation:['navigation.goTo','navigation.previous','navigation.next'],'sources.open':['sources.open'],'sources.manage':['sources.add','sources.remove'],
  'learning.mode':['learning.setMode','learning.toggleMode'],'whiteboard.tools':['whiteboard.setTool'],
  'whiteboard.history':['whiteboard.undo','whiteboard.redo'],'whiteboard.clear':['whiteboard.clear'],
  'whiteboard.appearance':['whiteboard.setAppearance'],'whiteboard.zoom':['whiteboard.setZoom','whiteboard.zoomBy']
};
test('every protected operation needs both its declaration and host approval',()=>{
  assert.deepEqual(Object.keys(methods).sort(),[...permissionNames].sort());
  for(const [permission,names] of Object.entries(methods))for(const method of names){
    assert.equal(createPermissionPolicy([permission],[]).can(method),false,method);
    assert.equal(createPermissionPolicy([],[permission]).can(method),false,method);
    assert.equal(createPermissionPolicy([permission],[permission]).can(method),true,method);
    assert.equal(createPermissionPolicy(permissionNames,permissionNames.filter(name=>name!==permission)).can(method),false,method);
  }
});
test('own reads and display configuration remain available without operation grants',()=>{
  const policy=createPermissionPolicy();
  for(const method of ['host.getContext','editor.getData','practice.getQuestion','practice.getResult','answer.get','bank.getState','sources.list','navigation.getState','content.resolve','ui.configure','layout.configure'])assert.equal(policy.can(method),true);
  assert.deepEqual(policy.granted,[]);assert.throws(()=>policy.granted.push('bank.delete'),TypeError);
  assert.equal(createPermissionPolicy(permissionNames,permissionNames).can('future.unreviewedOperation'),false);
});
test('invalid permissions fail instead of becoming broad grants',()=>{
  for(const value of ['*',['*'],['files.read'],[null],{}])assert.throws(()=>readPermissions(value),TypeError);
  assert.deepEqual(readPermissions(['answer.write','answer.write']),['answer.write']);
});
test('revocation updates existing policy closures; restoration and subscriptions remain bounded',()=>{
  const policy=createPermissionPolicy(['answer.write'],['answer.write']);let notifications=0;
  const invoke=()=>policy.can('answer.update'),stop=policy.subscribe(()=>notifications++);
  policy.update([]);assert.equal(invoke(),false);assert.deepEqual(policy.granted,[]);assert.equal(notifications,1);
  assert.throws(()=>policy.update(['bank.delete']),/未声明/);assert.equal(notifications,1);
  policy.update(['answer.write']);assert.equal(invoke(),true);assert.equal(notifications,2);
  stop();policy.update([]);assert.equal(notifications,2);
});
