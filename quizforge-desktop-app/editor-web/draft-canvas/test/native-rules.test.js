import test from 'node:test';
import assert from 'node:assert/strict';
import {nativeRules} from '../src/extensions/isolated-rules.js';

test('native client waits for the bounded Windows cold startup before rejecting a valid reply',async t=>{
  t.mock.timers.enable({apis:['setTimeout']});
  globalThis.window={};const calls=[];const rules=nativeRules({request(...args){calls.push(args);}});
  try{
    let completed=false;const pending=rules.invoke('SINGLE_CHOICE','createDraft','{}').then(value=>{completed=true;return value;});
    t.mock.timers.tick(31000);await Promise.resolve();assert.equal(completed,false);
    t.mock.timers.tick(14000);
    window.qfNativeRules.replyFromJson(JSON.stringify({id:calls[0][0],value:'{"type":"SINGLE_CHOICE"}'}));
    assert.equal(JSON.parse(await pending).type,'SINGLE_CHOICE');
  }finally{rules.destroy();delete globalThis.window;}
});

test('native workbench rules carry typed errors, allow retry, and revoke pending calls on close',async()=>{
  globalThis.window={};const calls=[];
  const rules=nativeRules({request(...args){calls.push(args);}});
  try{
    const failed=rules.invoke('SINGLE_CHOICE','grade','{"answer":{}}');
    assert.deepEqual(calls[0].slice(1),['SINGLE_CHOICE','grade','{"answer":{}}']);
    window.qfNativeRules.replyFromJson(JSON.stringify({id:calls[0][0],error:{code:'EXTENSION_TIMEOUT',message:'stopped'}}));
    await assert.rejects(failed,error=>error.code==='EXTENSION_TIMEOUT');
    const retry=rules.invoke('SINGLE_CHOICE','grade','{}');
    window.qfNativeRules.replyFromJson(JSON.stringify({id:calls[1][0],value:'{"score":2}'}));
    assert.deepEqual(JSON.parse(await retry),{score:2});
    const pending=rules.invoke('SINGLE_CHOICE','snapshot','{}');rules.destroy();
    await assert.rejects(pending,/已关闭/);await assert.rejects(rules.invoke('SINGLE_CHOICE','grade','{}'),/已关闭/);
    assert.equal(window.qfNativeRules,undefined);
  }finally{rules.destroy();delete globalThis.window;}
});
