<script lang="ts">
	import { Check, Loader2 } from '@lucide/svelte';
	import { ColorPicker } from '$lib/components/ui/color-picker';
	import { NativeSelect } from '$lib/components/native-select';
	import type { PageData } from './$types';
	import { invalidateAll } from '$app/navigation';

	const { data }: { data: PageData } = $props();

	const BODY_FAMILY: Record<string, string> = {
		Geist: "'Geist Variable'",
		Inter: "'Inter Variable'",
		'System UI': 'system-ui',
	};
	const HEADING_FAMILY: Record<string, string> = {
		Fraunces: "'Fraunces Variable'",
		'Playfair Display': "'Playfair Display Variable'",
	};

	let primaryHue = $state(data.preferences.primaryHue);
	let headingFont = $state(data.preferences.headingFont);
	let bodyFont = $state(data.preferences.bodyFont);

	type SaveState = 'idle' | 'saving' | 'saved' | 'error';
	let saveState = $state<SaveState>('idle');
	let savedTimer: ReturnType<typeof setTimeout> | null = null;
	let debounceTimer: ReturnType<typeof setTimeout> | null = null;

	function applyLocally(hue: number, body: string, heading: string) {
		// Live preview: mirror the values onto the same CSS variables the
		// SSR-rendered <style> in +layout.svelte sets, so the app re-renders
		// against the in-flight selection without waiting for the server.
		const root = document.documentElement;
		root.style.setProperty('--primary-hue', String(hue));
		root.style.setProperty('--user-body-font', BODY_FAMILY[body] ?? BODY_FAMILY.Geist);
		root.style.setProperty('--user-heading-font', HEADING_FAMILY[heading] ?? HEADING_FAMILY.Fraunces);
	}

	async function persist() {
		saveState = 'saving';
		try {
			const res = await fetch('/api/user/preferences', {
				method: 'PUT',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ primaryHue, headingFont, bodyFont }),
			});
			if (!res.ok) {
				saveState = 'error';
				return;
			}
			saveState = 'saved';
			// Re-run the root layout load so other tabs / future navigations see
			// the new preferences without a hard refresh.
			await invalidateAll();
			if (savedTimer) clearTimeout(savedTimer);
			savedTimer = setTimeout(() => {
				if (saveState === 'saved') saveState = 'idle';
			}, 2000);
		} catch {
			saveState = 'error';
		}
	}

	function onHueChange(next: number) {
		primaryHue = next;
		applyLocally(next, bodyFont, headingFont);
		// Q18(c): 500ms debounce on the slider, save once the user stops moving.
		if (debounceTimer) clearTimeout(debounceTimer);
		debounceTimer = setTimeout(persist, 500);
	}

	function onBodyChange(next: string) {
		bodyFont = next;
		applyLocally(primaryHue, next, headingFont);
		// Q18(c): immediate save on dropdown — discrete choice, no debounce.
		void persist();
	}

	function onHeadingChange(next: string) {
		headingFont = next;
		applyLocally(primaryHue, bodyFont, next);
		void persist();
	}
</script>

<div class="space-y-6 max-w-2xl">
	<div class="flex items-center justify-between">
		<div>
			<h1 class="text-2xl font-semibold tracking-tight">Settings</h1>
			<p class="text-sm text-muted-foreground">Personalize the app's appearance.</p>
		</div>
		<div class="text-sm text-muted-foreground min-w-24 text-right" aria-live="polite">
			{#if saveState === 'saving'}
				<span class="inline-flex items-center gap-1">
					<Loader2 class="w-3.5 h-3.5 animate-spin" />
					Saving…
				</span>
			{:else if saveState === 'saved'}
				<span class="inline-flex items-center gap-1 text-foreground">
					<Check class="w-3.5 h-3.5" />
					Saved
				</span>
			{:else if saveState === 'error'}
				<span class="text-destructive">Could not save.</span>
			{/if}
		</div>
	</div>

	<section class="space-y-3 rounded-lg border p-5">
		<div>
			<h2 class="font-heading text-lg font-medium">Primary colour</h2>
			<p class="text-sm text-muted-foreground">
				The accent hue used for buttons, links, and highlights. Lightness and saturation are
				fixed per theme — only the hue changes.
			</p>
		</div>
		<ColorPicker
			name="Primary"
			hue={primaryHue}
			onchange={onHueChange}
		/>
	</section>

	<section class="space-y-3 rounded-lg border p-5">
		<div>
			<h2 class="font-heading text-lg font-medium">Typography</h2>
			<p class="text-sm text-muted-foreground">
				Pick a heading font and a body font. Your current choice is preloaded; switching to a
				different one briefly fetches it the first time.
			</p>
		</div>

		<div class="grid gap-4 sm:grid-cols-2">
			<div class="grid gap-1.5">
				<label for="heading-font" class="text-sm font-medium leading-none">Heading font</label>
				<NativeSelect
					name="heading-font"
					value={headingFont}
					onValueChange={onHeadingChange}
					items={[
						{ value: 'Fraunces', label: 'Fraunces' },
						{ value: 'Playfair Display', label: 'Playfair Display' },
					]}
				/>
				<p class="font-heading text-xl mt-1">The quick brown fox</p>
			</div>

			<div class="grid gap-1.5">
				<label for="body-font" class="text-sm font-medium leading-none">Body font</label>
				<NativeSelect
					name="body-font"
					value={bodyFont}
					onValueChange={onBodyChange}
					items={[
						{ value: 'Geist', label: 'Geist' },
						{ value: 'Inter', label: 'Inter' },
						{ value: 'System UI', label: 'System UI' },
					]}
				/>
				<p class="text-sm mt-1">The quick brown fox jumps over the lazy dog.</p>
			</div>
		</div>
	</section>
</div>
