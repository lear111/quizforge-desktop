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
console.log('Local Shared Practice SINGLE_CHOICE bundle: same World host, separate renderer');
