// Bundles the game into a single offline script inside the Android assets folder.
import * as esbuild from 'esbuild';
import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(here, '../android/app/src/main/assets/game');
mkdirSync(outDir, { recursive: true });

const copyStatic = () => {
  for (const f of ['index.html', 'style.css']) copyFileSync(resolve(here, 'public', f), resolve(outDir, f));
};

const options = {
  entryPoints: [resolve(here, 'src/main.js')],
  bundle: true,
  format: 'iife',
  minify: true,
  sourcemap: false,
  target: ['es2018', 'chrome64'],
  outfile: resolve(outDir, 'game.js'),
  legalComments: 'none',
  plugins: [{ name: 'static', setup(b) { b.onEnd(() => { copyStatic(); console.log('[build] ok'); }); } }],
};

if (process.argv.includes('--watch')) {
  const ctx = await esbuild.context(options);
  await ctx.watch();
} else {
  await esbuild.build(options);
}
