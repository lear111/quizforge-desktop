#!/usr/bin/env node
// Independent SDK packager: Node standard library only, deterministic stored ZIP.
import {readFile,writeFile,readdir,lstat,realpath,mkdir} from 'node:fs/promises';
import {resolve,dirname,basename,relative,isAbsolute,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';

const limits={entry:8*1024*1024,total:64*1024*1024,package:32*1024*1024,count:1024};
const permissions=new Set(['question.edit','bank.save','bank.add','bank.duplicate','bank.delete','answer.write',
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
  if(major!==2)throw new Error(`扩展需要 SDK ${major}，当前 SDK 2.1；${major>2?'请更新应用/打包工具':'请更新扩展'}`);
  if(minor>1)throw new Error(`扩展需要 SDK 2.${minor}，当前 SDK 2.1；请更新应用/打包工具`);
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
function zip(entries){
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
