// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
declare global {
	namespace App {
		// interface Error {}
		interface Locals {
			session: {
				user: {
					id: string;
					email: string;
					firstName: string | null;
					lastName: string | null;
				};
			} | null;
		}
		interface PageData {
			session: {
				user: {
					id: string;
					email: string;
					firstName: string | null;
					lastName: string | null;
				};
			} | null;
		}
		// interface PageState {}
		// interface Platform {}
	}
}

export {};
