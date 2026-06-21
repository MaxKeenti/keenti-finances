// One-shot generator for the PWA icon set.
//
// Keenti has no image-rasterization toolchain (no sharp / ImageMagick), so this
// script draws the brand mark from scratch and encodes PNGs using only Node's
// built-in zlib. Brand colors are computed directly from the oklch theme tokens
// in src/routes/layout.css so the icon tracks the real palette.
//
// Run: `node scripts/generate-icons.mjs` (re-run if the brand mark changes).

import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const STATIC_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'static');

// --- oklch -> sRGB ---------------------------------------------------------
// Standard Oklab/Oklch conversion (Björn Ottosson) + linear-sRGB companding.
function oklchToRgb(L, C, h) {
	const hr = (h * Math.PI) / 180;
	const a = C * Math.cos(hr);
	const b = C * Math.sin(hr);

	const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
	const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
	const s_ = L - 0.0894841775 * a - 1.291485548 * b;

	const l = l_ ** 3;
	const m = m_ ** 3;
	const s = s_ ** 3;

	const lin = [
		4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
		-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
		-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
	];

	return lin.map((v) => {
		const c = v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055;
		return Math.max(0, Math.min(255, Math.round(c * 255)));
	});
}

// Theme tokens (light mode): --primary and --primary-foreground at hue 91.
const BG = oklchToRgb(0.852, 0.199, 91); // amber field
const FG = oklchToRgb(0.421, 0.095, 91); // deep amber mark

// --- geometry --------------------------------------------------------------
// Brand mark "K", defined in a 0..100 space so it matches icon.svg exactly.
const STROKE = 11; // stroke-width of the K
const HALF = STROKE / 2;

// Squared distance from point p to segment ab.
function distToSeg(px, py, ax, ay, bx, by) {
	const dx = bx - ax;
	const dy = by - ay;
	const len2 = dx * dx + dy * dy || 1;
	let t = ((px - ax) * dx + (py - ay) * dy) / len2;
	t = Math.max(0, Math.min(1, t));
	const cx = ax + t * dx;
	const cy = ay + t * dy;
	return Math.hypot(px - cx, py - cy);
}

// Coverage (0..1) of the K stroke at a point in 0..100 space. `inset` shrinks
// the mark toward center for maskable icons (safe zone).
function kCoverage(x, y, inset) {
	// Re-map so the 0..100 mark sits inside the inset box.
	const s = (100 - 2 * inset) / 100;
	const mx = (x - inset) / s;
	const my = (y - inset) / s;
	if (mx < -HALF || mx > 100 + HALF || my < -HALF || my > 100 + HALF) return 0;

	const d = Math.min(
		distToSeg(mx, my, 35, 24, 35, 76), // stem
		distToSeg(mx, my, 70, 24, 41, 50), // upper diagonal
		distToSeg(mx, my, 41, 50, 72, 76), // lower diagonal
	);
	// 1px (in mark space) of antialiasing falloff at the stroke edge.
	return Math.max(0, Math.min(1, HALF + 0.5 - d));
}

// Rounded-rect coverage for the background field. Proper box SDF so the
// interior is solidly opaque (negative distance) even at radius 0.
function fieldCoverage(x, y, radius) {
	const qx = Math.abs(x - 50) - (50 - radius);
	const qy = Math.abs(y - 50) - (50 - radius);
	const outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
	const inside = Math.min(Math.max(qx, qy), 0);
	const d = outside + inside - radius;
	return Math.max(0, Math.min(1, 0.5 - d));
}

// --- raster + PNG ----------------------------------------------------------
function render(size, { maskable }) {
	// Maskable icons are full-bleed (no rounded corners; the platform masks
	// them) and keep the glyph inside an ~80% safe zone.
	const radius = maskable ? 0 : 22;
	const inset = maskable ? 20 : 14;
	const ss = 4; // supersample factor for smooth edges

	const buf = Buffer.alloc(size * size * 4);
	for (let py = 0; py < size; py++) {
		for (let px = 0; px < size; px++) {
			let field = 0;
			let mark = 0;
			for (let sy = 0; sy < ss; sy++) {
				for (let sx = 0; sx < ss; sx++) {
					const x = ((px + (sx + 0.5) / ss) / size) * 100;
					const y = ((py + (sy + 0.5) / ss) / size) * 100;
					field += fieldCoverage(x, y, radius);
					mark += kCoverage(x, y, inset);
				}
			}
			const n = ss * ss;
			field /= n;
			mark /= n;

			// Composite the deep-amber mark over the amber field.
			const r = BG[0] * (1 - mark) + FG[0] * mark;
			const g = BG[1] * (1 - mark) + FG[1] * mark;
			const b = BG[2] * (1 - mark) + FG[2] * mark;

			const i = (py * size + px) * 4;
			buf[i] = Math.round(r);
			buf[i + 1] = Math.round(g);
			buf[i + 2] = Math.round(b);
			buf[i + 3] = Math.round(field * 255);
		}
	}
	return buf;
}

function crc32(buf) {
	let c = ~0;
	for (let i = 0; i < buf.length; i++) {
		c ^= buf[i];
		for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
	}
	return (~c) >>> 0;
}

function chunk(type, data) {
	const len = Buffer.alloc(4);
	len.writeUInt32BE(data.length, 0);
	const typeBuf = Buffer.from(type, 'ascii');
	const crcBuf = Buffer.alloc(4);
	crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
	return Buffer.concat([len, typeBuf, data, crcBuf]);
}

function encodePng(size, rgba) {
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(size, 0);
	ihdr.writeUInt32BE(size, 4);
	ihdr[8] = 8; // bit depth
	ihdr[9] = 6; // RGBA
	// rows prefixed with filter byte 0
	const stride = size * 4;
	const raw = Buffer.alloc((stride + 1) * size);
	for (let y = 0; y < size; y++) {
		raw[y * (stride + 1)] = 0;
		rgba.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride);
	}
	const idat = deflateSync(raw, { level: 9 });
	return Buffer.concat([
		Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
		chunk('IHDR', ihdr),
		chunk('IDAT', idat),
		chunk('IEND', Buffer.alloc(0)),
	]);
}

// --- emit ------------------------------------------------------------------
mkdirSync(STATIC_DIR, { recursive: true });

const targets = [
	{ name: 'icon-192.png', size: 192, maskable: false },
	{ name: 'icon-512.png', size: 512, maskable: false },
	{ name: 'icon-maskable-192.png', size: 192, maskable: true },
	{ name: 'icon-maskable-512.png', size: 512, maskable: true },
	{ name: 'apple-touch-icon.png', size: 180, maskable: false },
];

for (const t of targets) {
	const png = encodePng(t.size, render(t.size, { maskable: t.maskable }));
	writeFileSync(join(STATIC_DIR, t.name), png);
	console.log(`wrote ${t.name} (${png.length} bytes)`);
}

const toHex = (c) => '#' + c.map((v) => v.toString(16).padStart(2, '0')).join('');
console.log(`field=${toHex(BG)} mark=${toHex(FG)}`);
