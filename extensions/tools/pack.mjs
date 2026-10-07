#!/usr/bin/env node
// Independent SDK packager: Node standard library only, deterministic stored ZIP.
import {readFile,writeFile,readdir,lstat,realpath,mkdir} from 'node:fs/promises';
import {resolve,dirname,basename,relative,isAbsolute,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {inflateRawSync} from 'node:zlib';

const limits={entry:8*1024*1024,total:64*1024*1024,package:32*1024*1024,count:1024};
const permissions=new Set(['question.edit','bank.save','bank.add','bank.duplicate','bank.delete','bank.move','answer.write',
  'practice.submit','practice.retry','navigation','sources.open','sources.manage','learning.mode',
  'whiteboard.tools','whiteboard.history','whiteboard.clear','whiteboard.appearance','whiteboard.zoom']);
const crcTable=Array.from({length:256},(_,i)=>{let c=i;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;return c>>>0;});
function crc(data){let c=0xffffffff;for(const b of data)c=crcTable[(c^b)&255]^(c>>>8);return (c^0xffffffff)>>>0;}
function safePath(value){
  if(typeof value!=='string'||!value||value.includes('\\')||value.includes(':')||isAbsolute(value)
      ||value.split('/').some(part=>!part||part==='.'||part==='..')||/[\x00-\x1f\x7f]/.test(value))
    throw new Error(`Invalid offline package path: ${value}`);
  return value;
}
function inside(source,target){const rel=relative(source,target);return rel===''||(!isAbsolute(rel)&&rel!=='..'&&!rel.startsWith('..'+sep));}
async function files(directory,prefix='',budget={bytes:0,count:0}){
  const result=[];
  for(const entry of (await readdir(directory,{withFileTypes:true})).sort((a,b)=>a.name<b.name?-1:a.name>b.name?1:0)){
    const name=safePath(prefix+entry.name),path=resolve(directory,entry.name),stat=await lstat(path);
    if(stat.isSymbolicLink())throw new Error(`Symbolic links cannot be packaged: ${name}`);
    if(stat.isDirectory())result.push(...await files(path,name+'/',budget));
    else if(stat.isFile()){
      if(stat.size>limits.entry)throw new Error(`File exceeds 8 MiB: ${name}`);
      budget.bytes+=stat.size;budget.count++;
      if(budget.bytes>limits.total||budget.count>limits.count)throw new Error('Package exceeds file-count or uncompressed-size limit');
      result.push({name,data:await readFile(path)});
    }else throw new Error(`Only regular files can be packaged: ${name}`);
  }
  return result;
}
function validate(manifest,entries){
  const format=manifest?.packageFormatVersion,major=manifest?.sdkApiMajor,minor=manifest?.minSdkApiMinor??0;
  if(!Number.isInteger(format)||!Number.isInteger(major)||!Number.isInteger(minor)||minor<0)throw new Error('Invalid SDK compatibility metadata');
  if(format!==2)throw new Error(`扩展需要格式 ${format}，当前支持格式 2；${format>2?'请更新应用/打包工具':'请更新扩展'}`);
  if(major!==2)throw new Error(`扩展需要 SDK ${major}，当前 SDK 2.3；${major>2?'请更新应用/打包工具':'请更新扩展'}`);
  if(minor>3)throw new Error(`扩展需要 SDK 2.${minor}，当前 SDK 2.3；请更新应用/打包工具`);
  if(manifest?.packageFormatVersion!==2||manifest.sdkApiMajor!==2||!Array.isArray(manifest.types)||!manifest.types.length)
    throw new Error('Manifest must declare packageFormatVersion 2, sdkApiMajor 2 and at least one type');
  if(!/^[a-z][a-z0-9-]*(?:\.[a-z][a-z0-9-]*)+$/.test(manifest.id||''))throw new Error('Extension ID must use a publisher namespace');
  if(typeof manifest.name!=='string'||!manifest.name.trim()||!/^\d+\.\d+\.\d+$/.test(manifest.version||''))
    throw new Error('Manifest needs a name and numeric major.minor.patch version');
  const names=new Map(entries.map(e=>[e.name,e.data])),ids=new Set();
  if(manifest.definition!==undefined&&manifest.definition!==null){
    const path=safePath(manifest.definition);if(!names.has(path))throw new Error(`Missing shared definition file: ${path}`);
  }
  if(new Set(entries.map(e=>e.name.toLowerCase())).size!==entries.length)throw new Error('Package paths must be unique across case-sensitive and Windows filesystems');
  for(const type of manifest.types){
    if(typeof type.id!=='string'||!type.id.trim()||ids.has(type.id)||! /^[A-Za-z][A-Za-z0-9_.-]{0,127}$/.test(type.id))
      throw new Error('Type IDs must be unique and namespaced');
    ids.add(type.id);
    if(type.pageApi!=null&&type.pageApi!=='simple')throw new Error('Unknown page API');
    if(type.pageApi==='simple'&&minor<3)throw new Error('Simple page API requires SDK 2.3');
    if(type.pageOptions){
      if(typeof type.pageOptions!=='object'||Array.isArray(type.pageOptions))throw new Error('Page options must be an object');
      if(Object.keys(type.pageOptions).some(k=>!['useDraft','card','initialLayout'].includes(k)))throw new Error('Unknown page option');
      for(const k of ['useDraft','card'])if(type.pageOptions[k]!=null&&typeof type.pageOptions[k]!=='boolean')throw new Error('Page option must be boolean');
      if(type.pageOptions.initialLayout!=null&&(typeof type.pageOptions.initialLayout!=='object'||Array.isArray(type.pageOptions.initialLayout)))throw new Error('Initial layout must be an object');
    }
    const examples=type.examples||[];
    if(!Array.isArray(examples)||examples.length>16||type.pageApi==='simple'&&!examples.length)throw new Error('Simple page types require 1 to 16 examples');
    const exampleIds=new Set();
    for(const example of examples){
      if(!/^[A-Za-z0-9_-]{1,80}$/.test(example.id||'')||exampleIds.has(example.id)||typeof example.title!=='string'||!example.title.trim())throw new Error('Invalid example metadata');
      exampleIds.add(example.id);const path=safePath(example.path);
      if(!path.endsWith('.qbank')||!names.has(path))throw new Error('Missing .qbank example');
      const bank=readExampleBank(names.get(path));
      if(!bank.questions.length||bank.questions.some(q=>q.type!==type.id))throw new Error('Example must contain only its declared question type');
    }
    if(type.permissions!=null&&(!Array.isArray(type.permissions)||type.permissions.some(name=>!permissions.has(name))))
      throw new Error(`Unknown or invalid permissions: ${type.id}`);
    if(!Number.isInteger(type.dataVersion)||type.dataVersion<1||typeof type.label!=='string'||!type.label.trim()
        ||!['OBJECTIVE','SUBJECTIVE'].includes(type.family)||!Array.isArray(type.capabilities))
      throw new Error(`Invalid type metadata: ${type.id}`);
    for(const field of ['questionSchema','answerSchema','rules','editor','renderer','defaultQuestion','editorScript','rendererScript']){
      const path=safePath(type[field]);if(!names.has(path))throw new Error(`Missing declared ${field} file: ${path}`);
      if(field.endsWith('Schema')||field==='defaultQuestion')JSON.parse(names.get(path).toString('utf8'));
      if((field==='editor'||field==='renderer')&&(/<\s*(script|iframe|frame|object|embed|base|link|meta)\b/i.test(names.get(path).toString('utf8'))||/\s(?:on\w+|src|srcset|href|action|formaction)\s*=/i.test(names.get(path).toString('utf8'))))throw new Error('HTML must use separate local manifest scripts');
    }
    const styles=typeof type.styles==='string'?[type.styles]:type.styles||[];
    if(!Array.isArray(styles))throw new Error('styles must be an offline path or path list');
    for(const style of styles){const path=safePath(style);if(!names.has(path))throw new Error(`Missing stylesheet: ${path}`);}
  }
}
export function zip(entries){
  const local=[],central=[];let offset=0,count=0;
  for(const {name,data}of entries){
    const path=Buffer.from(name),sum=crc(data),header=Buffer.alloc(30);
    header.writeUInt32LE(0x04034b50);header.writeUInt16LE(20,4);header.writeUInt16LE(0x0800,6);
    header.writeUInt32LE(sum,14);header.writeUInt32LE(data.length,18);header.writeUInt32LE(data.length,22);header.writeUInt16LE(path.length,26);
    const dir=Buffer.alloc(46);dir.writeUInt32LE(0x02014b50);dir.writeUInt16LE(20,4);dir.writeUInt16LE(20,6);dir.writeUInt16LE(0x0800,8);
    dir.writeUInt32LE(sum,16);dir.writeUInt32LE(data.length,20);dir.writeUInt32LE(data.length,24);dir.writeUInt16LE(path.length,28);dir.writeUInt32LE(offset,42);
    local.push(header,path,data);central.push(dir,path);offset+=header.length+path.length+data.length;count++;
  }
  const directory=Buffer.concat(central),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);
  end.writeUInt16LE(count,8);end.writeUInt16LE(count,10);end.writeUInt32LE(directory.length,12);end.writeUInt32LE(offset,16);
  return Buffer.concat([...local,directory,end]);
}
/** Bounded offline reader for preview/export. Native installation also validates the logical bank. */
export function readExampleBank(bytes){
  if(bytes.length>limits.entry)throw new Error('Example exceeds 8 MiB');
  let end=-1;for(let i=bytes.length-22;i>=Math.max(0,bytes.length-65557);i--)if(bytes.readUInt32LE(i)===0x06054b50){end=i;break;}
  if(end<0)throw new Error('Example must be a real .qbank ZIP');
  const count=bytes.readUInt16LE(end+10),offset=bytes.readUInt32LE(end+16),size=bytes.readUInt32LE(end+12);
  if(bytes.readUInt16LE(end+4)||bytes.readUInt16LE(end+6)||count!==bytes.readUInt16LE(end+8)||!count||count>limits.count||offset+size!==end)throw new Error('Invalid example ZIP directory');
  const entries=new Map();let position=offset,total=0;
  for(let i=0;i<count;i++){
    if(position+46>end||bytes.readUInt32LE(position)!==0x02014b50)throw new Error('Invalid example ZIP entry');
    const flags=bytes.readUInt16LE(position+8),method=bytes.readUInt16LE(position+10),packed=bytes.readUInt32LE(position+20),length=bytes.readUInt32LE(position+24),nameLength=bytes.readUInt16LE(position+28),extra=bytes.readUInt16LE(position+30),comment=bytes.readUInt16LE(position+32),local=bytes.readUInt32LE(position+42),checksum=bytes.readUInt32LE(position+16);
    const name=safePath(bytes.subarray(position+46,position+46+nameLength).toString('utf8'));
    if(flags&1||![0,8].includes(method)||length>limits.entry||total+length>limits.total||local+30>offset||entries.has(name.toLowerCase()))throw new Error('Unsafe or oversized example ZIP');
    if(bytes.readUInt32LE(local)!==0x04034b50||bytes.readUInt16LE(local+8)!==method)throw new Error('Invalid local ZIP header');
    const localNameLength=bytes.readUInt16LE(local+26),start=local+30+localNameLength+bytes.readUInt16LE(local+28);
    if(bytes.subarray(local+30,local+30+localNameLength).toString('utf8')!==name||start+packed>offset)throw new Error('Invalid local ZIP path');
    const data=method===0?bytes.subarray(start,start+packed):inflateRawSync(bytes.subarray(start,start+packed),{maxOutputLength:limits.entry});
    if(data.length!==length||crc(data)!==checksum)throw new Error('Example ZIP content mismatch');
    entries.set(name.toLowerCase(),data);total+=length;position+=46+nameLength+extra+comment;
  }
  if(position!==end||!entries.has('manifest.json')||!entries.has('bank.json'))throw new Error('Incomplete example bank');
  const manifest=JSON.parse(entries.get('manifest.json').toString('utf8')),body=JSON.parse(entries.get('bank.json').toString('utf8'));
  if(manifest.format!=='quizforge-question-bank'||manifest.schemaVersion!=='2.0'||!Array.isArray(manifest.resources)||!Array.isArray(body.questions)||!Array.isArray(body.stimuli))throw new Error('Invalid example bank format');
  for(const resource of manifest.resources){const path=safePath(resource.path),data=entries.get(path.toLowerCase());if(!path.startsWith('resources/')||!data||createHash('sha256').update(data).digest('hex')!==resource.sha256)throw new Error('Missing or corrupt example resource');}
  return {...manifest,...body};
}
export async function packExtension(sourceDirectory,outputFile){
  const sourcePath=resolve(sourceDirectory),sourceStat=await lstat(sourcePath);
  if(!sourceStat.isDirectory()||sourceStat.isSymbolicLink())throw new Error('Source must be a regular directory');
  const source=await realpath(sourcePath),out=resolve(outputFile);
  if(!out.toLowerCase().endsWith('.qfext'))throw new Error('Output must end with .qfext');
  if(inside(source,out)||inside(sourcePath,out))throw new Error('Output must be outside the source directory');
  await mkdir(dirname(out),{recursive:true});
  const actualParent=await realpath(dirname(out));
  if(inside(source,resolve(actualParent,basename(out))))throw new Error('Output parent resolves inside the source directory');
  try {if((await lstat(out)).isSymbolicLink())throw new Error('Output cannot be a symbolic link');}
  catch(error){if(error.code!=='ENOENT')throw error;}
  const entries=await files(source);
  if(entries.length>limits.count||entries.reduce((n,e)=>n+e.data.length,0)>limits.total)throw new Error('Package exceeds file-count or uncompressed-size limit');
  const manifestEntry=entries.find(e=>e.name==='manifest.json');if(!manifestEntry)throw new Error('Source needs manifest.json at its root');
  const manifest=JSON.parse(manifestEntry.data.toString('utf8'));validate(manifest,entries);
  const bytes=zip(entries);if(bytes.length>limits.package)throw new Error('Package exceeds 32 MiB');
  await writeFile(out,bytes);
  return {id:manifest.id,version:manifest.version,output:out,files:entries.length,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try {
    if(process.argv.length!==4)throw new Error('Usage: node pack.mjs <source-dir> <output.qfext>');
    console.log(JSON.stringify(await packExtension(process.argv[2],process.argv[3]),null,2));
  }catch(error){console.error(`Extension packaging failed: ${error.message}`);process.exitCode=1;}
}
