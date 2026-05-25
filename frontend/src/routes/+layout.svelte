<script lang="ts">
	import './layout.css';
	import AppShell from '$lib/components/app-shell/app-shell.svelte';
	import type { LayoutData } from './$types';
	import { initTheme } from '$lib/theme.svelte';

	// Vite-bundled URLs for choosable fonts. Used to render <link rel="preload">
	// for the active body+heading on initial paint and eliminate FOUT on the
	// user's current choices. Fonts not preloaded here still fetch lazily via
	// @fontsource's @import when first used (per Q16(c)).
	import geistUrl from '@fontsource-variable/geist/files/geist-latin-wght-normal.woff2?url';
	import interUrl from '@fontsource-variable/inter/files/inter-latin-wght-normal.woff2?url';
	import frauncesUrl from '@fontsource-variable/fraunces/files/fraunces-latin-wght-normal.woff2?url';
	import playfairUrl from '@fontsource-variable/playfair-display/files/playfair-display-latin-wght-normal.woff2?url';

	const { children, data }: { children: any; data: LayoutData } = $props();

	initTheme();

	// Map user-facing font name → CSS font-family string.
	// Per Q15(a): 3 body fonts × 2 heading fonts.
	const BODY_FAMILY: Record<string, string> = {
		Geist: "'Geist Variable'",
		Inter: "'Inter Variable'",
		'System UI': 'system-ui',
	};
	const HEADING_FAMILY: Record<string, string> = {
		Fraunces: "'Fraunces Variable'",
		'Playfair Display': "'Playfair Display Variable'",
	};

	const BODY_WOFF2: Record<string, string | null> = {
		Geist: geistUrl,
		Inter: interUrl,
		'System UI': null,
	};
	const HEADING_WOFF2: Record<string, string | null> = {
		Fraunces: frauncesUrl,
		'Playfair Display': playfairUrl,
	};

	const prefs = $derived(data.preferences);
	const bodyFamily = $derived(BODY_FAMILY[prefs.bodyFont] ?? BODY_FAMILY.Geist);
	const headingFamily = $derived(HEADING_FAMILY[prefs.headingFont] ?? HEADING_FAMILY.Fraunces);
	const bodyPreload = $derived(BODY_WOFF2[prefs.bodyFont] ?? null);
	const headingPreload = $derived(HEADING_WOFF2[prefs.headingFont] ?? null);
</script>

<svelte:head>
	{#if bodyPreload}
		<link rel="preload" href={bodyPreload} as="font" type="font/woff2" crossorigin="anonymous" />
	{/if}
	{#if headingPreload}
		<link rel="preload" href={headingPreload} as="font" type="font/woff2" crossorigin="anonymous" />
	{/if}
	<!-- SSR-rendered so the user's hue/fonts apply on first paint, no flash. -->
	<style>
		:root {
			--primary-hue: {prefs.primaryHue};
			--user-body-font: {bodyFamily};
			--user-heading-font: {headingFamily};
		}
	</style>
</svelte:head>

{#if data.session}
	<AppShell>
		{@render children()}
	</AppShell>
{:else}
	{@render children()}
{/if}
