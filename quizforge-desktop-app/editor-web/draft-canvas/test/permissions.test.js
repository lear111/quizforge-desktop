import test from 'node:test';
import assert from 'node:assert/strict';
import {createPermissionPolicy,readPermissions,permissionNames} from '../src/extensions/permissions.js';

const methods={
  'question.edit':['editor.update','content.edit'],
  'bank.save':['bank.save'],'bank.add':['page.add'],'bank.duplicate':['bank.duplicateQuestion'],'bank.delete':['bank.deleteQuestion'],'bank.move':['page.move'],
  'answer.write':['answer.update'],'practice.submit':['practice.submit'],'practice.retry':['practice.retry'],
  navigation:['navigation.goTo','navigation.previous','navigation.next','page.attempt'],'sources.open':['sources.open'],'sources.manage':['sources.add','sources.remove'],
  'learning.mode':['learning.setMode']
};
test('every protected operation needs both its declaration and host approval',()=>{
  assert.ok(Object.keys(methods).every(permission=>permissionNames.includes(permission)));
  for(const [permission,names] of Object.entries(methods))for(const method of names){
    assert.equal(createPermissionPolicy([permission],[]).can(method),false,method);
    assert.equal(createPermissionPolicy([],[permission]).can(method),false,method);
    assert.equal(createPermissionPolicy([permission],[permission]).can(method),true,method);
    assert.equal(createPermissionPolicy(permissionNames,permissionNames.filter(name=>name!==permission)).can(method),false,method);
  }
});
test('own reads and display configuration remain available without operation grants',()=>{
  const policy=createPermissionPolicy();
  for(const method of ['page.load','page.save','page.action','content.resolve','ui.configure','layout.configure'])assert.equal(policy.can(method),true);
  assert.deepEqual(policy.granted,[]);assert.throws(()=>policy.granted.push('bank.delete'),TypeError);
  assert.equal(createPermissionPolicy(permissionNames,permissionNames).can('future.unreviewedOperation'),false);
});

test('removed page APIs remain denied even when every permission is approved',()=>{
  const policy=createPermissionPolicy(permissionNames,permissionNames);
  for(const method of ['host.getContext','editor.getData','editor.save','bank.getState','bank.addQuestion','answer.get','answer.flush','practice.getState','practice.getQuestion','practice.getResult','learning.getMode','learning.toggleMode','sources.list','navigation.getState','whiteboard.getState','whiteboard.clear','whiteboard.setTool','whiteboard.setZoom'])assert.equal(policy.can(method),false,method);
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
