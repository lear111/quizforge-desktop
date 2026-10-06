import test from 'node:test';
import assert from 'node:assert/strict';
import {DraftModel} from '../src/model.js';
import {parseDraftCanvasDocument} from '../src/canvas/document.js';
test('text and paper survive round trip; undo keeps the current camera',()=>{
 const model=new DraftModel();
 model.putText({id:'text-1',x:10,y:20,width:260,size:20,color:'#34313b',text:'第一行\nSecond line'});
 model.setPaper({color:'#fff7dc',pattern:'LINES'});model.pan(40,30);
 const saved=model.getDraft();assert.deepEqual(parseDraftCanvasDocument(JSON.stringify(saved)),saved);
 model.undo();assert.equal(model.getDraft().paper,undefined);assert.equal(model.getDraft().texts[0].text,'第一行\nSecond line');assert.deepEqual(model.getDraft().viewport,saved.viewport);
 model.redo();assert.deepEqual(model.getDraft(),saved);
 model.beginEdit();model.putText({...saved.texts[0],x:90});model.putText({...saved.texts[0],x:100});model.endEdit();model.undo();assert.equal(model.getDraft().texts[0].x,10);
 model.clear();assert.equal(model.getDraft().texts,undefined);model.undo();assert.equal(model.getDraft().texts[0].text,saved.texts[0].text);
});
test('annotation parsing rejects malformed extensions atomically',()=>{
 const model=new DraftModel(),before=model.getDraft();
 for(const extra of [{texts:null},{paper:{color:'url(remote)',pattern:'GRID'}},{paper:{color:'#fff',pattern:'OTHER'}},{texts:[{id:'t',x:0,y:0,width:260,size:20,color:'#333',text:'x'.repeat(10001)}]}])assert.throws(()=>model.loadDraft({...before,...extra}));
 assert.deepEqual(model.getDraft(),before);
});
