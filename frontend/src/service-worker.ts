/// <reference types="@sveltejs/kit" />
/// <reference no-default-lib="true" />
/// <reference lib="esnext" />
/// <reference lib="webworker" />

// SvelteKit auto-registers this worker in production builds. It exists to make
// Keenti installable as a PWA and to serve the versioned app shell assets fast.
//
// Strategy: precache the immutable build output + static files and serve them
// cache-first. Everything else — page navigations (SSR, per-user) and the API —
// goes straight to the network so authenticated content is never served stale
// or to the wrong user from cache.

import { build, files, version } from '$service-worker';

const sw = self as unknown as ServiceWorkerGlobalScope;

const CACHE = `keenti-cache-${version}`;

// `build` = the app's JS/CSS (content-hashed, immutable).
// `files` = everything in static/ (icons, manifest, favicon).
const ASSETS = [...build, ...files];
const ASSET_SET = new Set(ASSETS);

sw.addEventListener('install', (event) => {
	event.waitUntil(
		caches.open(CACHE).then((cache) => cache.addAll(ASSETS)),
	);
	// Activate this worker as soon as it finishes installing.
	sw.skipWaiting();
});

sw.addEventListener('activate', (event) => {
	event.waitUntil(
		(async () => {
			// Drop caches from previous deploys.
			for (const key of await caches.keys()) {
				if (key !== CACHE) await caches.delete(key);
			}
			await sw.clients.claim();
		})(),
	);
});

sw.addEventListener('fetch', (event) => {
	const { request } = event;
	if (request.method !== 'GET') return;

	const url = new URL(request.url);
	if (url.origin !== sw.location.origin) return;

	// Only the known, versioned assets are cached; serve them cache-first.
	if (ASSET_SET.has(url.pathname)) {
		event.respondWith(
			(async () => {
				const cached = await caches.match(request);
				if (cached) return cached;
				const response = await fetch(request);
				const cache = await caches.open(CACHE);
				cache.put(request, response.clone());
				return response;
			})(),
		);
	}
	// All other requests (navigations, API, etc.) fall through to the network.
});
