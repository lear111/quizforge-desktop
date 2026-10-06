import test from 'node:test';
import assert from 'node:assert/strict';
import {defaultUi,configureUi} from '../src/shared/ui/preferences.js';

test('public UI defaults and partial configuration remain isolated by page',()=>{
  const editor=defaultUi('EDITOR'),practice=defaultUi('PRACTICE');
  assert.ok(Object.values(editor).every(Boolean));assert.ok(Object.values(practice).every(Boolean));
  const hidden=configureUi(editor,{save:false,sources:false},'EDITOR');
  assert.equal(hidden.save,false);assert.equal(hidden.typeLabel,true);assert.equal(editor.save,true);
  assert.equal(defaultUi('EDITOR').save,true);
  assert.equal(configureUi(hidden,{sources:true},'EDITOR').save,false);
  assert.equal(configureUi(practice,{confirmation:false},'PRACTICE').confirmation,false);
});
test('configuration rejects navigation, wrong page fields and invalid values atomically',()=>{
  const initial=defaultUi('EDITOR');
  for(const patch of [null,[],{navigation:false},{submit:false},{save:'false'},{save:false,unknown:true}])
    assert.throws(()=>configureUi(initial,patch,'EDITOR'));
  assert.equal(initial.save,true);
  assert.throws(()=>configureUi(defaultUi('PRACTICE'),{save:false},'PRACTICE'));
});
