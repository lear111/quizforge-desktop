import { build } from 'esbuild';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const output = '../../src/main/resources/editor/canvas';
await build({
  entryPoints: ['src/editor.js'], bundle: true, format: 'iife', target: 'es2018',
  outfile: `${output}/canvas-editor-bundle.js`,
  define: { 'import.meta.hot': 'false' },
  plugins: [{ name: 'raw-svg', setup(builder) {
    builder.onResolve({ filter: /\.svg\?raw$/ }, args => ({
      path: resolve(args.resolveDir, args.path.slice(0, -4)), namespace: 'raw-svg'
    }));
    builder.onLoad({ filter: /.*/, namespace: 'raw-svg' }, async args => ({
      contents: await readFile(args.path, 'utf8'), loader: 'text'
    }));
  }}]
});
const html = (await readFile('index.html', 'utf8')).replace(
  '<script type="module" src="/src/editor.js"></script>',
  '<link rel="stylesheet" href="canvas-editor-bundle.css"><script src="canvas-editor-bundle.js"></script>'
);
await writeFile(`${output}/canvas-editor.html`, html);
