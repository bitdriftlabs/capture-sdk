import * as esbuild from 'esbuild';
import * as path from 'node:path';
import * as fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const isWatch = process.argv.includes('--watch');

const preserveWebviewConfigPlugin = {
    name: 'preserve-webview-config',
    setup(build: esbuild.PluginBuild) {
        const MAGIC_REGEX = /\/\* @magic (.*?) \*\//g;
        const HIDDEN_PREFIX = '__PROTECTED_MAGIC_COMMENT_';
        const HIDDEN_SUFFIX = '__';

        // Store mappings from hidden strings to original content
        const magicComments = new Map<string, string>();

        // 1. Transform comment into a string literal so esbuild doesn't strip it
        build.onLoad({ filter: /\.[jt]sx?$/ }, async (args) => {
            let contents = await fs.promises.readFile(args.path, 'utf8');

            // Replace all /* @magic ... */ with hidden string literals
            contents = contents.replace(MAGIC_REGEX, (_match, content) => {
                const encoded = Buffer.from(content).toString('base64');
                const hiddenString = `${HIDDEN_PREFIX}${encoded}${HIDDEN_SUFFIX}`;
                magicComments.set(hiddenString, content);
                return `"${hiddenString}"`;
            });

            return { contents, loader: args.path.endsWith('ts') ? 'ts' : 'js' };
        });

        // 2. After bundling, swap the string literal back into the magic comment
        build.onEnd(async () => {
            const { outfile } = build.initialOptions;
            if (!outfile || !fs.existsSync(outfile)) return;

            let bundle = await fs.promises.readFile(outfile, 'utf8');

            // Replace all hidden strings back into their original comment format
            const hiddenRegex = new RegExp(`["']${HIDDEN_PREFIX}([A-Za-z0-9+/=]+)${HIDDEN_SUFFIX}["']`, 'g');
            bundle = bundle.replace(hiddenRegex, (_match, encoded) => {
                const content = Buffer.from(encoded, 'base64').toString('utf8');
                return `/* @magic ${content} */`;
            });

            await fs.promises.writeFile(outfile, bundle);
        });
    },
};

const buildOptions: esbuild.BuildOptions = {
    entryPoints: [path.join(__dirname, '../src/index.ts')],
    bundle: true,
    minify: true,
    legalComments: 'inline',
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
    plugins: [preserveWebviewConfigPlugin],
};

const build = async (): Promise<void> => {
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
};

build();
