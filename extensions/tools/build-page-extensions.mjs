import {readFile,writeFile,copyFile,mkdir,access} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {packExtension} from './pack.mjs';
const repo=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const available=['single-choice','multiple-choice','true-false','cloze','reading','matching','translation','essay'];
const chosen=process.argv.slice(2);if(chosen.some(s=>!available.includes(s)))throw new Error('Unknown question extension');
for(const slug of chosen.length?chosen:available){
  const directory=resolve(repo,'extensions/packages',slug);
  const hasSource=async name=>{try{await access(resolve(directory,name+'-source.js'));return true;}catch(error){if(error.code==='ENOENT')return false;throw error;}};
  if(available.indexOf(slug)<3&&await hasSource('editor')){await mkdir(resolve(directory,'lib'),{recursive:true});await copyFile(resolve(repo,'extensions/shared/page-client.js'),resolve(directory,'lib/page-client.js'));}
  for(const name of ['editor','practice',...(available.indexOf(slug)>=3?['type']:[])]){
    // Handwritten pages are already runtime scripts; preserve them and package directly.
    if(!await hasSource(name)){await access(resolve(directory,name+'.js'));continue;}
    const {build}=await import('../../quizforge-desktop-app/editor-web/draft-canvas/node_modules/esbuild/lib/main.js');
    const compiled=await build({entryPoints:[resolve(directory,name+'-source.js')],bundle:true,format:'iife',globalName:name==='type'?undefined:'QFPage',platform:'browser',target:'es2020',write:false});
    await writeFile(resolve(directory,name+'.js'),compiled.outputFiles[0].text+(name==='type'?'':'\nawait QFPage.start();\n'));
  }
  if(available.indexOf(slug)>=3){
    const base=await readFile(resolve(repo,'extensions/packages/true-false/style.css'),'utf8'),extra=await readFile(resolve(repo,'extensions/shared/reading-types.css'),'utf8');
    await writeFile(resolve(directory,'style.css'),base.replaceAll('qf-judgment-editor','qf-type-editor').replaceAll('qf-judgment-practice','qf-type-practice')+'\n'+extra);
  }
  const manifest=JSON.parse(await readFile(resolve(directory,'manifest.json'),'utf8'));
  await packExtension(directory,resolve(repo,'extensions/dist',manifest.id+'-'+manifest.version+'.qfext'));
  console.log('Built '+manifest.id+' '+manifest.version);
}
