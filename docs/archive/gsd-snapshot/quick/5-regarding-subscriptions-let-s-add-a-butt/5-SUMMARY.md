# Quick Task: Regarding subscriptions, let's add a button to handle /subscriptions/[id] so its clearer where that resource is found

**Date:** 2026-05-15
**Branch:** gsd/quick/5-regarding-subscriptions-let-s-add-a-butt

## What Changed
- Added a "View" button to each subscription card's action row in the list page, linking directly to `/subscriptions/{id}`

## Files Modified
- `frontend/src/routes/subscriptions/+page.svelte`

## Verification
- Button renders before Edit in the card footer; uses `href` prop so it's a proper anchor link, consistent with how other link-buttons are used in the codebase
