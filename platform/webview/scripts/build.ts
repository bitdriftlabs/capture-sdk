import * as esbuild from 'esbuild';
import * as path from 'node:path';
import * as fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const isWatch = process.argv.includes('--watch');

const buildOptions: esbuild.BuildOptions = {
    entryPoints: [path.join(__dirname, '../src/index.ts')],
    bundle: true,
    minify: true,
    format: 'iife',
    globalName: 'BitdriftWebView',
    target: ['es2020'],
    outfile: path.join(__dirname, '../dist/bitdrift-webview.js'),
    sourcemap: false,
    // Wrap in an IIFE that executes immediately
    banner: {
        js: '(function() {',
    },
    footer: {
        js: '})();',
    },
};

async function build(): Promise<void> {
    try {
        if (isWatch) {
            const ctx = await esbuild.context(buildOptions);
            await ctx.watch();
            console.log('Watching for changes...');
        } else {
            await esbuild.build(buildOptions);
            console.log('Build complete!');

            // Output bundle size
            if (buildOptions.outfile) {
                const stats = fs.statSync(buildOptions.outfile);
                console.log(`Bundle size: ${(stats.size / 1024).toFixed(2)} KB`);
            }
        }
    } catch (error) {
        console.error('Build failed:', error);
        process.exit(1);
    }
}

build();
