<script lang="ts">
	import { Check, Loader2 } from '@lucide/svelte';
	import { ColorPicker } from '$lib/components/ui/color-picker';
	import { Label } from '$lib/components/ui/label';
	import { NativeSelect } from '$lib/components/native-select';
	import type { PageData } from './$types';
	import { invalidateAll } from '$app/navigation';
	import { m } from '$lib/paraglide/messages.js';

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
			<h1 class="text-2xl font-semibold tracking-tight">{m.settings_title()}</h1>
			<p class="text-sm text-muted-foreground">{m.settings_description()}</p>
		</div>
		<div class="text-sm text-muted-foreground min-w-24 text-right" aria-live="polite">
			{#if saveState === 'saving'}
				<span class="inline-flex items-center gap-1">
					<Loader2 class="w-3.5 h-3.5 animate-spin" />
					{m.common_saving()}
				</span>
			{:else if saveState === 'saved'}
				<span class="inline-flex items-center gap-1 text-foreground">
					<Check class="w-3.5 h-3.5" />
					{m.settings_saved()}
				</span>
			{:else if saveState === 'error'}
				<span class="text-destructive">{m.settings_could_not_save()}</span>
			{/if}
		</div>
	</div>

	<section class="space-y-3 rounded-lg border p-5">
		<div>
			<h2 class="font-heading text-lg font-medium">{m.settings_primary_colour()}</h2>
			<p class="text-sm text-muted-foreground">
				{m.settings_primary_colour_description()}
			</p>
		</div>
		<ColorPicker
			name={m.settings_primary_colour()}
			hue={primaryHue}
			onchange={onHueChange}
		/>
	</section>

	<section class="space-y-3 rounded-lg border p-5">
		<div>
			<h2 class="font-heading text-lg font-medium">{m.settings_typography()}</h2>
			<p class="text-sm text-muted-foreground">
				{m.settings_typography_description()}
			</p>
		</div>

		<div class="grid gap-4 sm:grid-cols-2">
			<div class="grid gap-1.5">
				<Label for="heading-font">{m.settings_heading_font()}</Label>
				<NativeSelect
					name="heading-font"
					value={headingFont}
					onValueChange={onHeadingChange}
					items={[
						{ value: 'Fraunces', label: 'Fraunces' },
						{ value: 'Playfair Display', label: 'Playfair Display' },
					]}
				/>
				<p class="font-heading text-xl mt-1">{m.settings_font_preview_short()}</p>
			</div>

			<div class="grid gap-1.5">
				<Label for="body-font">{m.settings_body_font()}</Label>
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
				<p class="text-sm mt-1">{m.settings_font_preview_long()}</p>
			</div>
		</div>
	</section>
</div>
