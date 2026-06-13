// In-memory state that lets a page temporarily replace the bottom dock with a
// contextual action bar (e.g. bulk selection on the trash or transactions
// list). A page registers a bar while a selection is active and clears it when
// done; the app shell swaps the dock for this bar, keeping the user on the page
// until they finish or cancel.
import type { Component } from 'svelte';

export type DockActionButton = {
	label: string;
	icon?: Component<{ class?: string }>;
	variant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost';
	disabled?: boolean;
	onClick: () => void | Promise<void>;
};

export type DockActionBar = {
	/** Number of selected items, shown as the "{count} selected" label. */
	count: number;
	/** Primary actions, rendered left-to-right before the cancel button. */
	actions: DockActionButton[];
	/** Invoked when the user dismisses the selection (the ✕ button). */
	onCancel: () => void;
};

let current = $state<DockActionBar | null>(null);

export const dockActionStore = {
	get current(): DockActionBar | null {
		return current;
	},
	set(bar: DockActionBar) {
		current = bar;
	},
	clear() {
		current = null;
	},
};
