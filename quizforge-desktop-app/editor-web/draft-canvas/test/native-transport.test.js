import test from 'node:test';
import assert from 'node:assert/strict';
import {CHUNK_CHARACTERS,MAX_MESSAGE_CHARACTERS,createMessageAssembler,sendMessage} from '../src/native-transport.js';

function packets(message){const result=[];sendMessage({postMessage:p=>result.push(p)},message,'test-1');return result;}
test('large rich text crosses physical limit losslessly including split surrogate pairs and escaping',()=>{
  const document='x'.repeat(9*1024*1024)+'中文\"\\\n😀';
  const value={kind:'document',document};
  const wire=packets(value);assert.ok(wire.length>8);
  const reader=createMessageAssembler();let result;
  for(const packet of wire){assert.ok(JSON.stringify(packet).length<8*1024*1024);const next=reader.accept(JSON.parse(JSON.stringify(packet)));if(next)result=next;}
  assert.deepEqual(result,value);
});
test('bounded framing rejects oversized, out-of-order, overlapping, and stale transfers and recovers',()=>{
  let now=0;const reader=createMessageAssembler(()=>now);
  const wire=packets({kind:'test',text:'a'.repeat(CHUNK_CHARACTERS+2)});
  assert.throws(()=>reader.accept({...wire[0],length:MAX_MESSAGE_CHARACTERS+1}));
  assert.throws(()=>reader.accept(wire[1]));
  reader.accept(wire[0]);assert.throws(()=>reader.accept(wire[0]));
  reader.accept(wire[0]);now=30001;assert.throws(()=>reader.accept(wire[1]));
  assert.equal(reader.accept({kind:'ping'}).kind,'ping');
  let result;for(const packet of wire)result=reader.accept(packet);assert.equal(result.text.length,CHUNK_CHARACTERS+2);
});
test('invalid or recursive payloads clear assembly and cannot impersonate transport packets',()=>{
  const reader=createMessageAssembler();
  assert.throws(()=>reader.accept({kind:'qf-transport-chunk',id:'bad',index:0,total:1,length:2,data:'xx'}));
  assert.equal(reader.accept({kind:'ping'}).kind,'ping');
  const nested=JSON.stringify({kind:'qf-transport-chunk'});
  assert.throws(()=>reader.accept({kind:'qf-transport-chunk',id:'bad',index:0,total:1,length:nested.length,data:nested}));
});

test('surrogate pair split at a physical chunk boundary remains lossless',()=>{
  const prefix=JSON.stringify({kind:'test',text:''}).indexOf('"text":"')+8;
  const message={kind:'test',text:'x'.repeat(CHUNK_CHARACTERS-prefix-1)+'😀'+'x'.repeat(CHUNK_CHARACTERS)};
  const wire=packets(message);assert.ok(/[\ud800-\udbff]$/.test(wire[0].data));
  const reader=createMessageAssembler();let result;for(const packet of wire)result=reader.accept(JSON.parse(JSON.stringify(packet)));
  assert.deepEqual(result,message);
});
