import { build } from 'esbuild';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(root, '../../src/main/resources/editor/draft-canvas');
const nodePaths = [resolve(root, '../canvas/node_modules')];
await mkdir(output, { recursive: true });
// Inline trusted worker bundle only; packages cannot supply worker code or a worker URL.
const schemaWorker=await build({absWorkingDir:root,entryPoints:['src/extensions/schema-worker.js'],bundle:true,
  write:false,format:'iife',target:'es2020'});
const define={__QF_SCHEMA_WORKER_SOURCE__:JSON.stringify(schemaWorker.outputFiles[0].text)};
await build({ define, absWorkingDir: root, entryPoints: ['src/app.js'], bundle: true,
  format: 'iife', target: 'es2018', outfile: resolve(output, 'draft-canvas.js') });
const html = (await readFile(resolve(root, 'index.html'), 'utf8'))
  .replace('<script type="module" src="./src/app.js"></script>',
    '<link rel="stylesheet" href="draft-canvas.css"><script defer src="draft-canvas.js"></script>');
await writeFile(resolve(output, 'draft-canvas.html'), html);
console.log('Local DOM/SVG bundle: src/main/resources/editor/draft-canvas');
await build({ define, absWorkingDir: root, nodePaths, entryPoints: ['src/shared-practice-app.js'], bundle: true,
  format: 'iife', target: 'es2018', outfile: resolve(output, 'shared-practice.js') });
const practiceHtml = (await readFile(resolve(root, 'shared-practice.html'), 'utf8'))
  .replace('<script type="module" src="./src/shared-practice-app.js"></script>',
    '<link rel="stylesheet" href="shared-practice.css"><script defer src="shared-practice.js"></script>');
await writeFile(resolve(output, 'shared-practice.html'), practiceHtml);
console.log('Local Shared Practice: installed HTML SDK 2 extensions');
await build({define,absWorkingDir:root,nodePaths,entryPoints:['src/webview2-practice-app.js'],bundle:true,
  format:'iife',target:'es2020',outfile:resolve(output,'webview2-practice.js')});
await writeFile(resolve(output,'webview2-practice.html'),practiceHtml
  .replaceAll('shared-practice.css','webview2-practice.css').replaceAll('shared-practice.js','webview2-practice.js')
  .replace("worker-src blob: data:; child-src blob: data:;", "worker-src blob:; child-src 'self';"));
await build({ define, absWorkingDir: root, nodePaths, entryPoints: ['src/history-replay-app.js'], bundle: true,
  format: 'iife', target: 'es2018', outfile: resolve(output, 'history-replay.js'),
  // History uses the exact Practice stylesheet, not a separate copied style definition.
  plugins: [{ name: 'shared-practice-styles', setup(builder) {
    builder.onLoad({ filter: /\.css$/ }, () => ({ contents: '', loader: 'js' }));
  } }] });
const historyHtml = (await readFile(resolve(root, 'shared-practice.html'), 'utf8'))
  .replace('QuizForge · Shared Practice SINGLE_CHOICE', 'QuizForge · History Draft Replay')
  .replace('<script type="module" src="./src/shared-practice-app.js"></script>',
    '<link rel="stylesheet" href="shared-practice.css"><script defer src="history-replay.js"></script>');
await writeFile(resolve(output, 'history-replay.html'), historyHtml);
await build({define,absWorkingDir:root,nodePaths,entryPoints:['src/webview2-history-app.js'],bundle:true,
  format:'iife',target:'es2020',outfile:resolve(output,'webview2-history.js')});
await writeFile(resolve(output,'webview2-history.html'),historyHtml
  .replaceAll('shared-practice.css','webview2-practice.css').replaceAll('history-replay.js','webview2-practice.js')
  .replace("worker-src blob: data:; child-src blob: data:;", "worker-src blob:; child-src 'self';"));
console.log('Local read-only History replay: existing Canvas / installed HTML extensions / shared CSS');
await build({ define, absWorkingDir: root, nodePaths, entryPoints: ['src/extensions/editor-app.js'], bundle: true,
  format: 'iife', target: 'es2018', loader: {'.html':'text'}, outfile: resolve(output, 'extension-editor.js') });
await writeFile(resolve(output, 'extension-editor.html'), '<!doctype html><html><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'self\' \'unsafe-eval\' \'nonce-qf-isolated-page-v1\'; frame-src \'self\'; worker-src blob:; style-src \'self\' \'unsafe-inline\'; img-src data:; connect-src \'none\'; object-src \'none\'; base-uri \'none\'; form-action \'none\'"><link rel="stylesheet" href="extension-editor.css"><body><main id="extension-editor"></main><script src="extension-editor.js"></script></body></html>');
await build({define,absWorkingDir:root,nodePaths,entryPoints:['src/webview2-editor-app.js'],bundle:true,
  format:'iife',target:'es2020',loader:{'.html':'text'},outfile:resolve(output,'webview2-editor.js')});
await writeFile(resolve(output,'webview2-editor.html'),(await readFile(resolve(output,'extension-editor.html'),'utf8'))
  .replaceAll('extension-editor.css','webview2-practice.css').replaceAll('extension-editor.js','webview2-practice.js'));
await build({define,absWorkingDir:root,nodePaths,entryPoints:['src/extensions/preview.js'],bundle:true,format:'iife',target:'es2018',outfile:resolve(output,'extension-preview.js')});
await writeFile(resolve(output,'extension-preview.html'), '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'self\' \'unsafe-eval\' \'nonce-qf-isolated-page-v1\'; frame-src \'self\'; worker-src blob:; style-src \'self\' \'unsafe-inline\'; img-src data:; connect-src \'none\'; object-src \'none\'; base-uri \'none\'; form-action \'none\'"><link rel="stylesheet" href="extension-preview.css"><style>body{background:#f5f4f7;margin:0;padding:24px;font:16px Arial,sans-serif}#question-preview{max-width:720px;margin:auto;padding:28px;background:white;border-radius:14px}</style><main id="question-preview"></main><script src="extension-preview.js"></script></html>');
await build({define,absWorkingDir:root,nodePaths,entryPoints:['src/extensions/workbench-app.js'],bundle:true,format:'iife',target:'es2018',outfile:resolve(output,'extension-workbench.js')});
await writeFile(resolve(output,'extension-workbench.html'), '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'self\' \'unsafe-eval\' \'nonce-qf-isolated-page-v1\'; frame-src \'self\'; worker-src blob:; style-src \'self\' \'unsafe-inline\'; img-src data:; connect-src \'none\'; object-src \'none\'; base-uri \'none\'; form-action \'none\'"><title>QuizForge 扩展开发预览</title><link rel="stylesheet" href="extension-workbench.css"><main id="extension-workbench"></main><script src="extension-workbench.js"></script></html>');
await writeFile(resolve(output, 'extension-rules-runtime.js'),
  await readFile(resolve(root, 'src/extensions/rules-runtime.js'), 'utf8'));
// Extension packages are built separately and installed explicitly by the user.
