import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,cp,readFile,writeFile,mkdir,symlink} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import vm from 'node:vm';
import {packExtension,readExampleBank,zip} from './pack.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..'),execute=promisify(execFile);

for (const [slug, type] of [['single-choice','SINGLE_CHOICE'], ['multiple-choice','MULTIPLE_CHOICE'], ['true-false','TRUE_FALSE']]) test(`${type}: copied source and packager build independently outside the repository with identical bytes`,async()=>{
  const work=await mkdtemp(resolve(tmpdir(),'quizforge-external-sdk-'));
  const source=resolve(work,'my-extension'),tool=resolve(work,'pack.mjs'),output=resolve(work,'sample.qfext');
  await cp(resolve(root,`packages/${slug}`),source,{recursive:true});await cp(resolve(root,'tools/pack.mjs'),tool);
  const {stdout}=await execute(process.execPath,[tool,source,output],{cwd:work});
  const result=JSON.parse(stdout);assert.equal(result.id,`quizforge.types.${slug}`);assert.ok(result.files>=10);
  assert.match(result.sha256,/^[a-f0-9]{64}$/);
  assert.deepEqual(await readFile(output),await readFile(resolve(root,`dist/${result.id}-${result.version}.qfext`)));
  const context=vm.createContext({});
  const engine=resolve(root,'../quizforge-desktop-app/editor-web/draft-canvas/src/extensions/rules-runtime.js');
  vm.runInContext(await readFile(engine,'utf8'),context);
  const template=JSON.parse(await readFile(resolve(source,'default.json'),'utf8'));
  context.QF.installDefaultQuestion(type,template);
  vm.runInContext(await readFile(resolve(source,'type.js'),'utf8'),context);
  const invoke=(operation,input)=>JSON.parse(context.QuestionRules.invoke(type,operation,JSON.stringify(input)));
  const question=invoke('createDraft',{ids:{question:'q_external'}});
  assert.equal(question.type,type);
  assert.equal(invoke('grade',{question,answer:{selectedOptionIds:question.answerSpec.correctOptionIds},maxScore:2}).score,2);
  console.log(JSON.stringify({externalDirectory:work,...result}));
});

test('packager rejects output inside source and remote/traversal manifest assets',async()=>{
  const work=await mkdtemp(resolve(tmpdir(),'quizforge-pack-validation-')),source=resolve(work,'source');
  await cp(resolve(root,'packages/single-choice'),source,{recursive:true});
  await assert.rejects(packExtension(source,resolve(source,'output.qfext')),/outside the source/);
  const path=resolve(source,'manifest.json'),manifest=JSON.parse(await readFile(path,'utf8'));
  manifest.types[0].renderer='https://example.invalid/renderer.js';await writeFile(path,JSON.stringify(manifest));
  await assert.rejects(packExtension(source,resolve(work,'remote.qfext')),/Invalid offline package path/);
  manifest.types[0].renderer='../renderer.js';await writeFile(path,JSON.stringify(manifest));
  await assert.rejects(packExtension(source,resolve(work,'escape.qfext')),/Invalid offline package path/);
});

test('packager rejects symbolic-link directories before following them',async()=>{
  const work=await mkdtemp(resolve(tmpdir(),'quizforge-pack-link-')),source=resolve(work,'source'),other=resolve(work,'other');
  await cp(resolve(root,'packages/single-choice'),source,{recursive:true});await mkdir(other);
  await symlink(other,resolve(source,'linked'),process.platform==='win32'?'junction':'dir');
  await assert.rejects(packExtension(source,resolve(work,'linked.qfext')),/Symbolic links/);
});
test('packager rejects unknown permission requests',async()=>{
  const work=await mkdtemp(resolve(tmpdir(),'quizforge-pack-permission-')),source=resolve(work,'source');
  await cp(resolve(root,'packages/single-choice'),source,{recursive:true});
  const path=resolve(source,'manifest.json'),manifest=JSON.parse(await readFile(path,'utf8'));
  manifest.types[0].permissions=['files.read'];await writeFile(path,JSON.stringify(manifest));
  await assert.rejects(packExtension(source,resolve(work,'unknown.qfext')),/Unknown or invalid permissions/);
});
test('packager validates minimum SDK minor version and upgrade direction',async()=>{
  const work=await mkdtemp(resolve(tmpdir(),'quizforge-pack-sdk-')),source=resolve(work,'source');
  await cp(resolve(root,'packages/single-choice'),source,{recursive:true});
  const path=resolve(source,'manifest.json'),manifest=JSON.parse(await readFile(path,'utf8'));
  manifest.minSdkApiMinor=3;await writeFile(path,JSON.stringify(manifest));await packExtension(source,resolve(work,'valid.qfext'));
  manifest.minSdkApiMinor=4;await writeFile(path,JSON.stringify(manifest));await assert.rejects(packExtension(source,resolve(work,'future.qfext')),/SDK 2.4/);
  manifest.minSdkApiMinor='1';await writeFile(path,JSON.stringify(manifest));await assert.rejects(packExtension(source,resolve(work,'invalid.qfext')),/Invalid SDK/);
});

test('simple types require actual examples and reject empty or mismatched banks',async()=>{
  const work=await mkdtemp(resolve(tmpdir(),'quizforge-pack-examples-')),source=resolve(work,'source');
  await cp(resolve(root,'packages/single-choice'),source,{recursive:true});
  const path=resolve(source,'manifest.json'),manifest=JSON.parse(await readFile(path,'utf8'));
  const sample=manifest.types[0].examples[0],examplePath=resolve(source,sample.path);
  const bank=readExampleBank(await readFile(examplePath));
  manifest.types[0].examples=[];await writeFile(path,JSON.stringify(manifest));
  await assert.rejects(packExtension(source,resolve(work,'missing.qfext')),/require 1 to 16 examples/);
  manifest.types[0].examples=[sample];await writeFile(path,JSON.stringify(manifest));
  const encode=questions=>zip([{name:'manifest.json',data:Buffer.from(JSON.stringify({format:bank.format,schemaVersion:bank.schemaVersion,resources:[]}))},{name:'bank.json',data:Buffer.from(JSON.stringify({stimuli:[],questions}))}]);
  await writeFile(examplePath,encode([]));await assert.rejects(packExtension(source,resolve(work,'empty.qfext')),/only its declared question type/);
  await writeFile(examplePath,encode([{...bank.questions[0],type:'WRONG_TYPE'}]));await assert.rejects(packExtension(source,resolve(work,'wrong.qfext')),/only its declared question type/);
  await writeFile(examplePath,Buffer.from('not a bank'));await assert.rejects(packExtension(source,resolve(work,'invalid.qfext')),/real .qbank ZIP/);
});

test('example reader rejects traversal and corrupt ZIP entries',async()=>{
  assert.throws(()=>readExampleBank(zip([{name:'../bank.json',data:Buffer.from('{}')}])),/Invalid offline package path/);
  const bytes=await readFile(resolve(root,'packages/single-choice/examples/basic.qbank'));
  const corrupt=Buffer.from(bytes);corrupt[40]^=1;
  assert.throws(()=>readExampleBank(corrupt),/mismatch|Invalid local ZIP path/);
});
