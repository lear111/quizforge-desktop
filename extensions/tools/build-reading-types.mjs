// Bundles only local extension sources, then uses the public independent package tool.
import {build} from '../../quizforge-desktop-app/editor-web/draft-canvas/node_modules/esbuild/lib/main.js';
import {readFile,writeFile} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {packExtension} from './pack.mjs';
const repo=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const available=['cloze','reading','matching','translation','essay'];
const selected=process.argv.slice(2);
if(selected.some(slug=>!available.includes(slug)))throw new Error('Unknown reading type');
for(const slug of selected.length?selected:available){
  const directory=resolve(repo,'extensions/packages',slug);
  for(const name of ['editor','practice','type']){
    const compiled=await build({entryPoints:[resolve(directory,name+'-source.js')],bundle:true,format:'iife',globalName:name==='type'?undefined:'QFPage',platform:'browser',target:'es2020',write:false});
    // Page bootstrap owns the async function; use an ESM export entry bundled as a callable IIFE.
    await writeFile(resolve(directory,name+'.js'),compiled.outputFiles[0].text+(name==='type'?'':'\nawait QFPage.start();\n'));
  }
  const base=await readFile(resolve(repo,'extensions/packages/true-false/style.css'),'utf8');
  const extra=await readFile(resolve(repo,'extensions/shared/reading-types.css'),'utf8');
  await writeFile(resolve(directory,'style.css'),base.replaceAll('qf-judgment-editor','qf-type-editor').replaceAll('qf-judgment-practice','qf-type-practice')+'\n'+extra);
  const manifest=JSON.parse(await readFile(resolve(directory,'manifest.json'),'utf8'));
  await packExtension(directory,resolve(repo,'extensions/dist',manifest.id+'-'+manifest.version+'.qfext'));
  console.log('Built independent extension: '+manifest.id);
}
