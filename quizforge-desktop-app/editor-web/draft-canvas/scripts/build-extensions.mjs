import { readFile, readdir, mkdir, copyFile, writeFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { packExtension } from '../../../../extensions/tools/pack.mjs';

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
    rulesSource: await read(type.rules), stylesSource: (await Promise.all((type.styles || []).map(read))).join('\n')
  });
  const bundle = { manifest, assets };
  bundles.push(bundle);
  await writeFile(resolve(dist, `${name}.bundle.json`), JSON.stringify(bundle));
}
const webResources = resolve(repo, 'quizforge-desktop-app/src/main/resources/editor/draft-canvas');
await copyFile(resolve(webResources, 'extension-preview.js'), resolve(dist, 'preview.js'));
await copyFile(resolve(webResources, 'extension-preview.css'), resolve(dist, 'preview.css'));
await writeFile(resolve(dist, 'preview-initial.js'), `const packages=${JSON.stringify(bundles)};
await Promise.all(packages.map(p=>window.questionExtensions.install(p)));
const selector=document.querySelector('#type'), questions=[];
for(const p of packages)for(const a of p.assets){questions.push(a);const o=document.createElement('option');o.value=a.typeId;o.textContent=p.manifest.types.find(t=>t.id===a.typeId).label;selector.append(o);}
function show(){const a=questions.find(a=>a.typeId===selector.value)||questions[0];if(a)window.questionPreview.showQuestion(a.defaultQuestion);}
selector.addEventListener('change',show);show();`);
await writeFile(resolve(dist, 'preview.html'), `<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'self' 'unsafe-eval' 'nonce-qf-isolated-page-v1'; frame-src 'self'; worker-src blob:; style-src 'self' 'unsafe-inline'; img-src data:; connect-src 'none'; object-src 'none'; base-uri 'none'">
<title>QuizForge 题型预览</title><link rel="stylesheet" href="preview.css">
<style>body{background:#f5f4f7;padding:24px;font:16px Arial}main{max-width:720px;margin:24px auto;padding:28px;background:white;border-radius:14px}nav{max-width:776px;margin:auto}</style>
<body><nav><label>题型预览 <select id="type"></select></label></nav><main id="question-preview"></main><script src="preview.js"></script><script type="module" src="preview-initial.js"></script></body></html>`);
console.log(`Built ${bundles.length} external packages in ${dist}`);
