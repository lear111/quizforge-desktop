import test from 'node:test';
import assert from 'node:assert/strict';
import {QuestionRendererRegistry} from '../src/shared/renderer/registry.js';
import {validatePageHtml} from '../src/extensions/html-ui.js';
import {compileQuestionExtension,flushExtensionEditor,replaceDevelopmentExtension,installQuestionExtension} from '../src/extensions/sdk.js';
import {bundle,installChoices,compiled} from './fixtures/html-choice.js';
assert.deepEqual(QuestionRendererRegistry.types,[]);
installChoices();
test('only installed HTML types have renderers and editors',()=>{
  assert.deepEqual(QuestionRendererRegistry.types,['SINGLE_CHOICE','MULTIPLE_CHOICE']);
  for(const type of QuestionRendererRegistry.types){assert.equal(QuestionRendererRegistry.require(type).selectionMode,'EXTENSION');assert.equal(typeof QuestionRendererRegistry.editor(type).mount,'function');}
  assert.throws(()=>QuestionRendererRegistry.require('CLOZE'),/缺少对应题型拓展/);
});
test('HTML accepts static structure and rejects executable or external markup',()=>{
  assert.equal(validatePageHtml('<section><input data-answer></section>'),'<section><input data-answer></section>');
  for(const html of ['<script>bad()</script>','<iframe></iframe>','<img src="https://bad">','<button onclick="bad()">x</button>'])assert.throws(()=>validatePageHtml(html));
  const source=bundle();source.manifest.sdkApiMajor=1;assert.throws(()=>compileQuestionExtension(source),/SDK 2 required/);
});
for(const slug of ['single-choice','multiple-choice'])test(`${slug} full JSON template, IDs and grading round trip`,()=>{
  const rules=compiled(slug).rules,type=bundle(slug).manifest.types[0].id;
  const invoke=(operation,input)=>JSON.parse(rules.invoke(type,operation,JSON.stringify(input)));
  const q=invoke('createDraft',{ids:{question:'q_new',opts:['opt_a','opt_b','opt_c','opt_d']}});
  assert.equal(q.id,'q_new');assert.equal(q.payload.kind,'CHOICE');assert.deepEqual(invoke('validate',{question:q}).errors,[]);
  const maxScore=q.scoreSpec.defaultMaxScore;
  const right=invoke('grade',{question:q,answer:{selectedOptionIds:q.answerSpec.correctOptionIds},maxScore});
  assert.equal(right.score,maxScore);assert.equal(right.status,'CORRECT');
  assert.equal(invoke('grade',{question:q,answer:{selectedOptionIds:['opt_b']},maxScore}).score,0);
  assert.ok(invoke('validateAnswer',{question:q,answer:{selectedOptionIds:['opt_unknown']}}).errors.length);
  const duplicate=invoke('duplicate',{question:q,ids:{question:'q_copy',opts:['opt_e','opt_f','opt_g','opt_h']}});
  assert.ok(duplicate.answerSpec.correctOptionIds.every(id=>duplicate.payload.options.some(o=>o.id===id)));
  assert.ok(duplicate.payload.options.every(o=>!q.payload.options.some(old=>old.id===o.id)));
});
test('editor flush completes writes before reading saved data',async()=>{
  let value='old';assert.equal(await flushExtensionEditor({async flush(){value='new';}},()=>value),'new');
  await assert.rejects(flushExtensionEditor({async flush(){throw Error('failed');}},()=>value),/failed/);
});
test('development replaces the installed page definitions while normal installation remains immutable',()=>{
  const original=bundle(),type=original.manifest.types[0].id;
  const before=QuestionRendererRegistry.require(type,original.manifest.version);
  const changed=structuredClone(original);changed.assets[0].rendererHtml+='<p>Live preview</p>';
  assert.equal(installQuestionExtension(changed).installed,false);
  assert.equal(QuestionRendererRegistry.require(type,original.manifest.version),before);
  assert.equal(replaceDevelopmentExtension(changed),true);
  assert.notEqual(QuestionRendererRegistry.require(type,original.manifest.version),before);
  replaceDevelopmentExtension(original);
});
test('invalid view scripts and identity changes preserve the last usable development definitions',()=>{
  const original=bundle(),before=QuestionRendererRegistry.require('SINGLE_CHOICE',original.manifest.version);
  const invalid=structuredClone(original);invalid.assets[0].rendererSource='const = broken;';
  assert.throws(()=>replaceDevelopmentExtension(invalid),SyntaxError);
  assert.equal(QuestionRendererRegistry.require('SINGLE_CHOICE',original.manifest.version),before);
  const changed=structuredClone(original);changed.manifest.version='9.0.0';
  assert.throws(()=>replaceDevelopmentExtension(changed),/installed extension version/);
  assert.equal(QuestionRendererRegistry.require('SINGLE_CHOICE',original.manifest.version),before);
});
