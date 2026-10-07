import test from 'node:test';
import assert from 'node:assert/strict';
import {hostSdk,requireCompatibleManifest} from '../src/extensions/compatibility.js';
import {bundle} from './fixtures/html-choice.js';
import {compileQuestionExtension,installQuestionExtension,replaceDevelopmentExtension,updateQuestionExtensionPermissions} from '../src/extensions/sdk.js';
import {QuestionRendererRegistry} from '../src/shared/renderer/registry.js';

test('SDK minor requirements are backward compatible and reject future or malformed versions',()=>{
  assert.deepEqual(hostSdk,{apiMajor:2,apiMinor:3,packageFormatVersion:2});
  for(const minor of [undefined,0,1,2,3])requireCompatibleManifest({packageFormatVersion:2,sdkApiMajor:2,minSdkApiMinor:minor});
  assert.throws(()=>requireCompatibleManifest({packageFormatVersion:2,sdkApiMajor:3}),/请更新应用/);
  assert.throws(()=>requireCompatibleManifest({packageFormatVersion:2,sdkApiMajor:1}),/请更新扩展/);
  assert.throws(()=>requireCompatibleManifest({packageFormatVersion:3,sdkApiMajor:2}),/请更新应用/);
  for(const minor of [4,-1,0.5,'1'])assert.throws(()=>requireCompatibleManifest({packageFormatVersion:2,sdkApiMajor:2,minSdkApiMinor:minor}));
});

test('legacy page contracts are rejected before compilation and leave current definitions intact',()=>{
  const current=bundle(),before=QuestionRendererRegistry.extensions();
  for(const pageApi of [undefined,'legacy']){
    const source=structuredClone(current);source.manifest.types[0].pageApi=pageApi;
    assert.throws(()=>compileQuestionExtension(source),/旧页面接口已移除/);
    assert.deepEqual(QuestionRendererRegistry.extensions(),before);
  }
});
test('permissions are host-only updates bound to the installed hash and preserve renderer identity',()=>{
  const source=bundle(),type=source.manifest.types[0].id;source.sha256='a'.repeat(64);
  source.grantedPermissions={[type]:['answer.write']};installQuestionExtension(source);
  const before=QuestionRendererRegistry.require(type),packet={id:source.manifest.id,version:source.manifest.version,sha256:source.sha256,grantedPermissions:{}};
  assert.equal(updateQuestionExtensionPermissions(packet),true);assert.equal(QuestionRendererRegistry.require(type),before);
  assert.throws(()=>updateQuestionExtensionPermissions({...packet,sha256:'b'.repeat(64)}),/package hash/);
  assert.throws(()=>updateQuestionExtensionPermissions({...packet,grantedPermissions:{UNDECLARED:['answer.write']}}),/Undeclared/);
  const modified=structuredClone(source);modified.sha256='b'.repeat(64);assert.throws(()=>replaceDevelopmentExtension(modified),/package hash/);
  const supported=structuredClone(source);supported.manifest.minSdkApiMinor=4;
  assert.throws(()=>replaceDevelopmentExtension(supported),/SDK 2.4/);assert.equal(QuestionRendererRegistry.require(type),before);
});
