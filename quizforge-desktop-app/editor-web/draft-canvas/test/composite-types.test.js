import test from 'node:test';
import assert from 'node:assert/strict';
import {compiled,bundle} from './fixtures/html-choice.js';
import {payload,spec,groups,markers,synchronizeTranslations} from '../../../../extensions/shared/legacy-data.js';
import {installQuestionExtension} from '../src/extensions/sdk.js';
import {projectQuestionPreview} from '../src/extensions/preview-projection.js';
const types=['cloze','reading','matching','translation','essay'];
const engines=new Map(types.map(slug=>[slug,compiled(slug)]));
const call=(slug,operation,input)=>JSON.parse(engines.get(slug).rules.invoke(slug.toUpperCase(),operation,JSON.stringify(input)));
const create=slug=>call(slug,'createDraft',{ids:{question:'q_'+slug}});
for(const slug of types)test(`${slug}: independent template, schema, rules and identity allocation`,()=>{
  const q=create(slug),copy=call(slug,'duplicate',{question:q,ids:{question:'q_copy'}});
  assert.deepEqual(call(slug,'validate',{question:q}).errors,[]);
  assert.deepEqual(call(slug,'validate',{question:copy}).errors,[]);
  assert.equal(copy.id,'q_copy');assert.notEqual(q.id,copy.id);
  const ids=value=>[...groups(value).map(i=>i.id),...(payload(value).options||groups(value).flatMap(g=>g.options||[])).map(o=>o.id)];
  assert.ok(ids(copy).every(id=>!ids(q).includes(id)));
  assert.equal(engines.get(slug).editors.length,1);assert.equal(engines.get(slug).renderers.length,1);
});
for(const slug of ['cloze','reading','matching'])test(`${slug}: partial/full grading use frozen composite maximum`,()=>{
  const q=create(slug),targets=call(slug,'snapshot',{question:q}),count=groups(q).filter(g=>!g.locked).length;
  assert.equal(targets.maxScore,q.scoreSpec.defaultMaxScore*count);assert.equal(targets.targets.length,count);
  const answer=Object.fromEntries(spec(q).answers.filter(a=>targets.targets.some(t=>t.id===(a.blankId||a.itemId))).map(a=>[a.blankId||a.itemId,a.correctOptionId]));
  assert.equal(call(slug,'grade',{question:q,answer,maxScore:targets.maxScore}).status,'CORRECT');
  const first=Object.fromEntries(Object.entries(answer).slice(0,1));
  assert.equal(call(slug,'grade',{question:q,answer:first,maxScore:targets.maxScore}).score,q.scoreSpec.defaultMaxScore);
});
test('matching discloses three hints only, accepts repeated free letters and rejects fixed letters',()=>{
  const q=create('matching'),s=call('matching','snapshot',{question:q}),data=s.publicPayload.data;
  assert.equal(data.blanks.filter(b=>b.givenOptionId).length,3);assert.equal(s.targets.length,5);
  const unlocked=groups(q).filter(b=>!b.locked),hints=groups(q).filter(b=>b.locked),free=spec(q).answers.find(a=>a.blankId===unlocked[0].id).correctOptionId;
  assert.deepEqual(call('matching','validateAnswer',{question:q,answer:{[unlocked[0].id]:free,[unlocked[1].id]:free}}).errors,[]);
  assert.ok(call('matching','validateAnswer',{question:q,answer:{[unlocked[0].id]:hints[0].givenOptionId}}).errors.length);
  assert.ok(call('matching','validateAnswer',{question:q,answer:{[hints[0].id]:free}}).errors.length);
});
test('legacy storage envelopes are accepted, including essays without optional fields',()=>{
  for(const slug of types){const q=create(slug);q.payload={kind:slug.toUpperCase(),...payload(q)};q.answerSpec={kind:slug.toUpperCase(),...spec(q)};
    if(slug==='essay'){delete q.payload.placeholder;delete q.answerSpec.referenceAnswer;}
    assert.deepEqual(call(slug,'validate',{question:q}).errors,[]);
  }
});
test('markers reject malformed input; translation reorder preserves reference identities',()=>{
  assert.equal(markers('\\{{1}} {{2}}',true).found.length,1);
  assert.deepEqual(markers('\\{{1}} {{2}}',true).errors,[]);
  for(const text of ['{{x}}','{{1','{{1{{2}}'])assert.ok(markers(text,true).errors.length);
  const q=create('translation'),old=groups(q),prompt={kind:'TEXT',text:`{{${old[1].text}}} {{${old[0].text}}} {{A new sentence.}}`};
  const synced=synchronizeTranslations(q,prompt,()=> 'item_fresh');
  assert.deepEqual(synced.items.map(i=>i.id),[old[1].id,old[0].id,'item_fresh']);
  assert.deepEqual(synced.answers[0],spec(q).answers[1]);assert.equal(synced.answers[2].referenceAnswer.text,'');
});
test('subjective rich answers persist and submit without fabricated automatic marks',()=>{
  for(const slug of ['essay','translation']){const q=create(slug),value={kind:'CANVAS_DOCUMENT',text:'An answer.',document:JSON.stringify({data:{main:[{value:'An answer.',bold:true}]}})},answer=slug==='essay'?value:{[groups(q)[0].id]:value};
    assert.deepEqual(call(slug,'validateAnswer',{question:q,answer}).errors,[]);
    const result=call(slug,'grade',{question:q,answer,maxScore:call(slug,'snapshot',{question:q}).maxScore});assert.equal(result.status,'UNSCORED');assert.equal(result.score,null);
  }
});
test('web preview uses extension totals and public hints while keeping answers private',()=>{
  for(const slug of types){installQuestionExtension(bundle(slug));const q=create(slug),projection=projectQuestionPreview(q);
    assert.equal(projection.maxScore,call(slug,'snapshot',{question:q}).maxScore);
    assert.equal(projection.presentation.reference,null);assert.equal(projection.presentation.question.answerSpec,undefined);
    if(slug==='matching')assert.equal(projection.presentation.question.payload.data.blanks.filter(b=>b.givenOptionId).length,3);
  }
});
