---
status: accepted
---

# Category hue is mandatory and stored as `SMALLINT`

Every Category has an OKLCH hue. The schema enforces this with `category.hue SMALLINT NOT NULL CHECK (hue >= 0 AND hue < 360)`. The column was previously `color VARCHAR(10)` holding stringified hue digits with nulls allowed; a Flyway migration renames and retypes it, backfilling existing rows from the direction-default table (INGRESS=100, EGRESS=10, BOTH=220).

Hue is part of what defines a Category — its badge is how it shows up everywhere in the UI. A "Category with no colour" is a degenerate state that the SwatchPicker exposed through a Clear button but the new ColorPicker doesn't: a slider has no natural null. Making hue mandatory removes the "what does an uncoloured badge mean?" question from the dashboard and category list, and removes one branch from every badge render.

## Considered options

- **Keep hue nullable, keep `color VARCHAR(10)`:** rejected — leaves a wart we'd have to work around in the new picker (a non-slider null affordance) and keeps the badge rendering with two style paths for no domain-meaningful reason.
- **Retype to SMALLINT but keep nullable:** rejected — saves nothing over making it mandatory; the migration to enforce it later would cost as much as doing it now.
- **Rename to `hue` but keep VARCHAR:** rejected — half-fix; the value is a number 0–360, storing it as text invites coercion bugs in the domain layer.
