import { build } from 'esbuild';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(root, '../../src/main/resources/editor/draft-canvas');
await mkdir(output, { recursive: true });
await build({ absWorkingDir: root, entryPoints: ['src/app.js'], bundle: true,
  format: 'iife', target: 'es2018', outfile: resolve(output, 'draft-canvas.js') });
const html = (await readFile(resolve(root, 'index.html'), 'utf8'))
  .replace('<script type="module" src="./src/app.js"></script>',
    '<link rel="stylesheet" href="draft-canvas.css"><script defer src="draft-canvas.js"></script>');
await writeFile(resolve(output, 'draft-canvas.html'), html);
console.log('Local DOM/SVG bundle: src/main/resources/editor/draft-canvas');
await build({ absWorkingDir: root, entryPoints: ['src/shared-practice-app.js'], bundle: true,
  format: 'iife', target: 'es2018', outfile: resolve(output, 'shared-practice.js') });
const practiceHtml = (await readFile(resolve(root, 'shared-practice.html'), 'utf8'))
  .replace('<script type="module" src="./src/shared-practice-app.js"></script>',
    '<link rel="stylesheet" href="shared-practice.css"><script defer src="shared-practice.js"></script>');
await writeFile(resolve(output, 'shared-practice.html'), practiceHtml);
console.log('Local Shared Practice: seven built-in renderers, shared runtime and static registry');
await build({ absWorkingDir: root, entryPoints: ['src/history-replay-app.js'], bundle: true,
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
console.log('Local read-only History replay: existing Canvas / shared seven-type registry / shared CSS');
