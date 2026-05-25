# Quick Task: Dock on mobile don't get stuck to the bottom when scrolling, it seems they continue to be swept up with the rest of the page whenever the first bunch of loaded content is scrolled

**Date:** 2026-05-18
**Branch:** gsd/quick/11-dock-on-mobile-don-t-get-stuck-to-the-bo

## What Changed
- Replaced `position: fixed` dock with a flex-column app shell layout so the dock naturally anchors to the viewport bottom without relying on fixed positioning
- App shell now uses `h-dvh flex flex-col` with a `flex-1 overflow-y-auto` main content area — only the content scrolls, the dock never moves
- Removed `backdrop-blur-md` and semi-transparent `bg-sidebar/80` from dock nav (no longer needed since it doesn't overlay content); updated to solid `bg-sidebar`
- Removed `pb-20` bottom padding from main (was compensating for the old fixed overlay)

## Files Modified
- `frontend/src/lib/components/app-shell/app-shell.svelte`
- `frontend/src/lib/components/app-shell/dock.svelte`

## Verification
- Confirmed `position: fixed` was replaced with flex flow — the dock is now the last child in a `flex-col h-dvh` container
- The root cause was that `position: fixed` on mobile browsers (especially iOS Safari) can break when content reflows occur during dynamic loading, causing the dock to scroll with the page
- The flex layout approach is universally reliable: the scroll container is explicitly `overflow-y-auto` on `<main>`, so the dock never participates in scrolling
