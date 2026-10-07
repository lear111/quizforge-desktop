import { readFile, readdir, mkdir, copyFile, writeFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { packExtension,readExampleBank } from '../../../../extensions/tools/pack.mjs';

// Optional developer distribution build. Never writes into application resources.
const repo = resolve(dirname(fileURLToPath(import.meta.url)), '../../../..');
const sources = resolve(process.argv[2] || resolve(repo, 'extensions/packages'));
const dist = resolve(process.argv[3] || resolve(repo, 'extensions/dist'));
await mkdir(dist, { recursive: true });
const bundles = [];
for (const entry of (await readdir(sources, { withFileTypes: true })).sort((a, b) => a.name.localeCompare(b.name))) {
  if (!entry.isDirectory()) continue;
  const directory = resolve(sources, entry.name);
  const read = file => readFile(resolve(directory, file), 'utf8');
  const manifest = JSON.parse(await read('manifest.json'));
  const name = `${manifest.id}-${manifest.version}`;
  await packExtension(directory, resolve(dist, `${name}.qfext`));
  const assets = [];
  for (const type of manifest.types) assets.push({
    typeId: type.id, defaultQuestion: JSON.parse(await read(type.defaultQuestion)),
    questionSchemaSource: await read(type.questionSchema), answerSchemaSource: await read(type.answerSchema),
    editorHtml: await read(type.editor), rendererHtml: await read(type.renderer),
    editorSource: type.editorScript ? await read(type.editorScript) : '',
    rendererSource: type.rendererScript ? await read(type.rendererScript) : '',
    rulesSource: await read(type.rules), stylesSource: (await Promise.all((type.styles || []).map(read))).join('\n'),
    examples:await Promise.all((type.examples||[]).map(async sample=>({...sample,bank:readExampleBank(await readFile(resolve(directory,sample.path)))})))
  });
  for(const asset of assets)for(const example of asset.examples||[]){
    example.downloadPath=`examples/${name}/${asset.typeId}/${example.id}.qbank`;
    const output=resolve(dist,example.downloadPath);await mkdir(dirname(output),{recursive:true});await copyFile(resolve(directory,example.path),output);
  }
  const bundle = { manifest, assets };
  bundles.push(bundle);
  await writeFile(resolve(dist, `${name}.bundle.json`), JSON.stringify(bundle));
}
const webResources = resolve(repo, 'quizforge-desktop-app/src/main/resources/editor/draft-canvas');
await copyFile(resolve(webResources, 'extension-preview.js'), resolve(dist, 'preview.js'));
await copyFile(resolve(webResources, 'extension-preview.css'), resolve(dist, 'preview.css'));
await writeFile(resolve(dist, 'preview-initial.js'), `const packages=${JSON.stringify(bundles)};
await Promise.all(packages.map(p=>window.questionExtensions.install(p)));
const selector=document.querySelector('#type'), sample=document.querySelector('#sample'), question=document.querySelector('#question'), types=[];
for(const p of packages)for(const a of p.assets){types.push(a);const o=new Option(p.manifest.types.find(t=>t.id===a.typeId).label,a.typeId);selector.append(o);}
function selected(){return types.find(a=>a.typeId===selector.value)||types[0];}
function questions(){const a=selected(),bank=a?.examples?.find(s=>s.id===sample.value)?.bank;return bank?.questions||[a.defaultQuestion];}
function show(){const q=questions()[Number(question.value)||0];if(q)window.questionPreview.showQuestion(q);}
function selectSample(){const list=questions();question.replaceChildren(...list.map((q,i)=>new Option('第 '+(i+1)+' 题',String(i))));const example=selected()?.examples?.find(s=>s.id===sample.value),download=document.querySelector('#download');download.hidden=!example;if(example){download.href=example.downloadPath;download.download=example.id+'.qbank';}show();}
function selectType(){const a=selected();sample.replaceChildren(...(a?.examples||[]).map(s=>new Option(s.title,s.id)));selectSample();}
selector.addEventListener('change',selectType);sample.addEventListener('change',selectSample);question.addEventListener('change',show);selectType();`);
await writeFile(resolve(dist, 'preview.html'), `<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-eval' 'nonce-qf-isolated-page-v1'; frame-src 'self'; worker-src blob:; style-src 'self' 'unsafe-inline'; img-src data:; connect-src 'none'; object-src 'none'; base-uri 'none'">
<title>QuizForge 题型预览</title><link rel="stylesheet" href="preview.css">
<style>body{background:#f5f4f7;padding:24px;font:16px Arial}main{max-width:720px;margin:24px auto;padding:28px;background:white;border-radius:14px}nav{max-width:776px;margin:auto}</style>
<body><nav><label>题型预览 <select id="type"></select></label> <label>示例 <select id="sample"></select></label> <label>题目 <select id="question"></select></label> <a id="download" hidden>下载样例题库</a></nav><main id="question-preview"></main><script src="preview.js"></script><script type="module" src="preview-initial.js"></script></body></html>`);
console.log(`Built ${bundles.length} external packages in ${dist}`);
