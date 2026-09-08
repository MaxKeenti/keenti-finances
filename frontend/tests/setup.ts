/**
 * Test preload.
 *
 * `sveltekit-superforms` pulls in its client entry, which imports SvelteKit's
 * `$app/environment` virtual module. Vite supplies that at build time; under
 * `bun test` there is no Vite, so stub the few flags it reads. Server code
 * under test never depends on these values.
 */

// `bun:test` is the runner's own module and is not part of the app's
// TypeScript program, so `svelte-check` cannot resolve it.
// @ts-expect-error -- resolved by the Bun test runtime only.
import { mock } from 'bun:test';

mock.module('$app/environment', () => ({
	browser: false,
	building: false,
	dev: false,
	version: 'test',
}));

mock.module('$app/stores', () => {
	const readable = (value: unknown) => ({
		subscribe: (run: (value: unknown) => void) => (run(value), () => {}),
	});
	return {
		page: readable({ url: new URL('http://app.test/'), params: {}, form: null }),
		navigating: readable(null),
		updated: { ...readable(false), check: async () => false },
		getStores: () => ({ page: readable({}), navigating: readable(null), updated: readable(false) }),
	};
});

mock.module('$app/navigation', () => ({
	goto: async () => {},
	invalidateAll: async () => {},
	beforeNavigate: () => {},
	afterNavigate: () => {},
	onNavigate: () => {},
	applyAction: async () => {},
}));

mock.module('$app/forms', () => ({
	enhance: () => ({ destroy() {} }),
	applyAction: async () => {},
	deserialize: (value: string) => JSON.parse(value),
}));

// The repo ships type-only shims for `effect` so `svelte-check` can resolve
// superforms' optional adapter. Those are declaration files with no runtime
// value; the Zod adapter under test never calls into them.
const effectStub = {
	Schema: {},
	SchemaAST: {},
	Either: {},
	JSONSchema: {},
};
mock.module('effect', () => effectStub);
mock.module('effect/SchemaAST', () => ({}));
mock.module('effect/ParseResult', () => ({ ArrayFormatter: {} }));
mock.module(new URL('../src/lib/types/effect-shim.d.ts', import.meta.url).pathname, () => effectStub);
mock.module(
	new URL('../src/lib/types/effect-schema-ast-shim.d.ts', import.meta.url).pathname,
	() => ({}),
);
