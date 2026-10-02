// In-memory record of the areas opened this session, so the dock can mark them
// with a running dot the way macOS marks open apps. It is only written from the
// browser (an effect in the dock), never during server rendering.
let opened = $state<string[]>([]);

export const dockSessionStore = {
	/** Dock hrefs of the areas opened this session. */
	get opened(): string[] {
		return opened;
	},
	open(href: string) {
		if (!opened.includes(href)) opened = [...opened, href];
	},
};
