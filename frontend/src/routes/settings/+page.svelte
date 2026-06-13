<script lang="ts">
	import { Check, Loader2 } from '@lucide/svelte';
	import { ColorPicker } from '$lib/components/ui/color-picker';
	import * as Card from '$lib/components/ui/card';
	import { Label } from '$lib/components/ui/label';
	import * as NativeSelect from '$lib/components/ui/native-select';
	import { Badge } from '$lib/components/ui/badge';
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
				<Badge variant="secondary" class="gap-1">
					<Loader2 class="w-3.5 h-3.5 animate-spin" />
					{m.common_saving()}
				</Badge>
			{:else if saveState === 'saved'}
				<Badge variant="success" class="gap-1">
					<Check class="w-3.5 h-3.5" />
					{m.settings_saved()}
				</Badge>
			{:else if saveState === 'error'}
				<Badge variant="destructive">{m.settings_could_not_save()}</Badge>
			{/if}
		</div>
	</div>

	<Card.Root>
		<Card.Header>
			<Card.Title>{m.settings_primary_colour()}</Card.Title>
			<Card.Description>
				{m.settings_primary_colour_description()}
			</Card.Description>
		</Card.Header>
		<Card.Content>
			<ColorPicker
				name={m.settings_primary_colour()}
				hue={primaryHue}
				onchange={onHueChange}
			/>
		</Card.Content>
	</Card.Root>

	<Card.Root>
		<Card.Header>
			<Card.Title>{m.settings_typography()}</Card.Title>
			<Card.Description>
				{m.settings_typography_description()}
			</Card.Description>
		</Card.Header>

		<Card.Content>
			<div class="grid gap-4 sm:grid-cols-2">
				<div class="grid gap-1.5">
					<Label for="heading-font">{m.settings_heading_font()}</Label>
					<NativeSelect.Root
						id="heading-font"
						name="heading-font"
						bind:value={headingFont}
						class="w-full"
						onchange={(e) => onHeadingChange((e.currentTarget as HTMLSelectElement).value)}
					>
						<NativeSelect.Option value="Fraunces">Fraunces</NativeSelect.Option>
						<NativeSelect.Option value="Playfair Display">Playfair Display</NativeSelect.Option>
					</NativeSelect.Root>
					<p class="font-heading text-xl mt-1">{m.settings_font_preview_short()}</p>
				</div>

				<div class="grid gap-1.5">
					<Label for="body-font">{m.settings_body_font()}</Label>
					<NativeSelect.Root
						id="body-font"
						name="body-font"
						bind:value={bodyFont}
						class="w-full"
						onchange={(e) => onBodyChange((e.currentTarget as HTMLSelectElement).value)}
					>
						<NativeSelect.Option value="Geist">Geist</NativeSelect.Option>
						<NativeSelect.Option value="Inter">Inter</NativeSelect.Option>
						<NativeSelect.Option value="System UI">System UI</NativeSelect.Option>
					</NativeSelect.Root>
					<p class="text-sm mt-1">{m.settings_font_preview_long()}</p>
				</div>
			</div>
		</Card.Content>
	</Card.Root>
</div>
