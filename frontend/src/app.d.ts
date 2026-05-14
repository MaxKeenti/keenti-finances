// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
declare global {
	namespace App {
		// interface Error {}
		interface Locals {
			session: { username: string } | null;
		}
		interface PageData {
			session: { username: string } | null;
		}
		// interface PageState {}
		// interface Platform {}
	}
}

export {};
