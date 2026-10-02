// Build gate: colour comes only from the semantic tokens in src/routes/layout.css.
//
// Flags raw Tailwind palette classes (`text-amber-600`, `bg-white`,
// `shadow-black/10`, …) and arbitrary hex/rgb/hsl colours (`bg-[#fff]`) in
// Svelte and TypeScript sources. Hue-driven `oklch(… var(--*-hue))` arbitrary
// values are allowed: they render a User-chosen hue, not a palette colour.
//
// When this flags something, add or reuse a token in layout.css — do not relax
// the pattern. Run: `bun run lint:colors`.

import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = join(ROOT, 'src');
const IGNORED = [join(SRC, 'lib', 'paraglide')];

const PALETTE =
	'red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|slate|gray|zinc|neutral|stone|white|black';
const UTILITY =
	'bg|text|border(?:-[trblxyse])?|ring(?:-offset)?|outline|fill|stroke|from|via|to|shadow|decoration|divide|accent|caret|placeholder';

const RULES = [
	{
		pattern: new RegExp(`(?<![\\w-])(?:${UTILITY})-(?:${PALETTE})(?:-\\d{2,3})?(?:\\/\\d+)?(?![\\w-])`, 'g'),
		message: 'raw palette class',
	},
	{
		pattern: new RegExp(`(?<![\\w-])(?:${UTILITY})-\\[(?:#|rgba?\\(|hsla?\\()[^\\]]*\\]`, 'g'),
		message: 'arbitrary colour value',
	},
];

function* sourceFiles(dir) {
	for (const entry of readdirSync(dir, { withFileTypes: true })) {
		const path = join(dir, entry.name);
		if (IGNORED.includes(path)) continue;
		if (entry.isDirectory()) yield* sourceFiles(path);
		else if (/\.(svelte|ts|js)$/.test(entry.name)) yield path;
	}
}

const violations = [];
for (const file of sourceFiles(SRC)) {
	readFileSync(file, 'utf8')
		.split('\n')
		.forEach((line, index) => {
			for (const { pattern, message } of RULES) {
				for (const match of line.matchAll(pattern)) {
					violations.push(`${relative(ROOT, file)}:${index + 1}  ${message}: ${match[0]}`);
				}
			}
		});
}

if (violations.length > 0) {
	console.error(violations.join('\n'));
	console.error(
		`\n${violations.length} colour violation(s). Use a semantic token from src/routes/layout.css (see AI_RULES.md).`,
	);
	process.exit(1);
}
