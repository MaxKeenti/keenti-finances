// Hex ↔ OKLCH hue conversion. The conversion is intentionally lossy:
// we only extract / produce the hue component (0..359). Lightness and
// chroma are theme-fixed at render time (see category-badge.svelte).
//
// Pipeline: hex → sRGB → linear RGB → OKLab → atan2 for hue.
// Reference: https://bottosson.github.io/posts/oklab/

const PI = Math.PI;

function srgbToLinear(c: number): number {
	return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function linearToSrgb(c: number): number {
	const out = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
	return Math.max(0, Math.min(1, out));
}

function linearRgbToOklab(r: number, g: number, b: number): [number, number, number] {
	const l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
	const m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
	const s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;

	const l_ = Math.cbrt(l);
	const m_ = Math.cbrt(m);
	const s_ = Math.cbrt(s);

	return [
		0.2104542553 * l_ + 0.793617785 * m_ - 0.0040720468 * s_,
		1.9779984951 * l_ - 2.428592205 * m_ + 0.4505937099 * s_,
		0.0259040371 * l_ + 0.7827717662 * m_ - 0.808675766 * s_,
	];
}

function oklabToLinearRgb(L: number, a: number, b: number): [number, number, number] {
	const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
	const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
	const s_ = L - 0.0894841775 * a - 1.291485548 * b;

	const l = l_ * l_ * l_;
	const m = m_ * m_ * m_;
	const s = s_ * s_ * s_;

	return [
		4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
		-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
		-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
	];
}

function parseHex(hex: string): [number, number, number] | null {
	let s = hex.trim().replace(/^#/, '');
	if (s.length === 3) s = s.split('').map((c) => c + c).join('');
	if (s.length !== 6 || !/^[0-9a-fA-F]{6}$/.test(s)) return null;
	const r = parseInt(s.slice(0, 2), 16) / 255;
	const g = parseInt(s.slice(2, 4), 16) / 255;
	const b = parseInt(s.slice(4, 6), 16) / 255;
	return [r, g, b];
}

function toHexChannel(c: number): string {
	return Math.round(c * 255)
		.toString(16)
		.padStart(2, '0');
}

/**
 * Parse `hex` and return its OKLCH hue (0..359). Returns null if the hex is
 * malformed or the color is achromatic (pure gray/black/white have undefined
 * hue — caller should keep the previous value).
 */
export function hexToOklchHue(hex: string): number | null {
	const rgb = parseHex(hex);
	if (!rgb) return null;
	const [r, g, b] = rgb.map(srgbToLinear) as [number, number, number];
	const [, a, bb] = linearRgbToOklab(r, g, b);
	const chroma = Math.sqrt(a * a + bb * bb);
	if (chroma < 1e-4) return null;
	let hue = (Math.atan2(bb, a) * 180) / PI;
	if (hue < 0) hue += 360;
	return Math.round(hue) % 360;
}

/**
 * Produce a representative hex for `hue` rendered at OKLCH(L=0.7, C=0.18).
 * Used to surface a "what hue does this slider position mean" preview swatch
 * separate from the theme-fixed badge rendering.
 */
export function oklchHueToRepresentativeHex(hue: number): string {
	const L = 0.7;
	const C = 0.18;
	const h = ((hue % 360) * PI) / 180;
	const a = C * Math.cos(h);
	const b = C * Math.sin(h);
	const [lr, lg, lb] = oklabToLinearRgb(L, a, b);
	return `#${toHexChannel(linearToSrgb(lr))}${toHexChannel(linearToSrgb(lg))}${toHexChannel(linearToSrgb(lb))}`;
}
