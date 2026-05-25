---
status: accepted
---

# Category colour is stored as an OKLCH hue, rendered with fixed L/C per theme

A Category persists only its hue (a number on the OKLCH colour wheel), not a full hex/RGB value. The frontend renders badges by combining the stored hue with theme-specific fixed lightness and chroma values, so the same Category looks correct in both light and dark mode.

Storing only the hue keeps the palette theme-adaptive — full hex would lock contrast into one theme and break the other. The picker is a full 360° hue wheel plus a hex input that's converted to OKLCH hue on save.

## Considered options

- **Full hex storage:** rejected — fixed colours break contrast in at least one theme.
- **Original direction-constrained curated swatch palette (green-ish for INGRESS, red-complement for EGRESS, blue-ish for BOTH):** initially shipped, later relaxed to a full hue wheel based on user preference for personalization over semantic colour. The constraint is no longer enforced.
