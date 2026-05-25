# S02: Theme Detection & Category Colors — UAT

**Milestone:** M002
**Written:** 2026-05-17T01:52:08.323Z

# S02 UAT — Theme Detection & Category Colors

**UAT Type:** Manual exploratory + visual verification
**Proof Level:** Integration

## Preconditions

- App running locally (`bun run dev` in `frontend/`, Quarkus dev in `backend/`)
- Database migrated (V7 applied): `category` table has `color VARCHAR(10)` column
- At least one category exists

---

## Test Cases

### TC-01: Dark mode activates on system preference

**Steps:**
1. Set OS to dark mode (System Settings → Appearance → Dark)
2. Open the app in a fresh browser tab (no prior session)

**Expected:** App loads with dark theme immediately — no white flash, no manual toggle needed; `.dark` class present on `<html>`

**Edge case:** Open app in light mode, then switch OS to dark while tab is open — app should switch without reload

---

### TC-02: Light mode activates on system preference

**Steps:**
1. Set OS to light mode
2. Open app in a fresh browser tab

**Expected:** App loads in light theme; `.dark` class absent on `<html>`

---

### TC-03: Runtime theme switch (no reload)

**Steps:**
1. Open app
2. Open browser DevTools → Console: `window.matchMedia('(prefers-color-scheme: dark)').matches`
3. Toggle OS appearance while app is open

**Expected:** `.dark` class toggles on `<html>` within ~1s of OS switch; colors update across all components without page reload

---

### TC-04: Assign color to a category

**Steps:**
1. Navigate to Categories page
2. Click "Edit" on any existing category (or create new)
3. Observe SwatchPicker below the name field
4. Click a hue circle

**Expected:** Selected hue circle shows a ring/highlight; form submits with `color` value; category row in list shows a colored pill (CategoryBadge)

**Edge case — direction filtering:**
- INGRESS categories: SwatchPicker shows green-ish hues only
- EGRESS categories: red-complement hues only
- BOTH categories: blue-ish hues only

---

### TC-05: Clear category color

**Steps:**
1. Edit a category that has a color assigned
2. Click "Clear" in the SwatchPicker
3. Submit

**Expected:** Category row shows name without colored pill (badge shows neutral/no hue)

---

### TC-06: Category badge in transactions table

**Steps:**
1. Create a transaction linked to a category that has a color
2. Navigate to Transactions page

**Expected:** Transaction row shows `CategoryBadge` with the assigned hue; badge renders correct OKLCH color in both light and dark themes

**Edge case — uncategorized transaction:** Row shows `—` (em-dash) with no badge

---

### TC-07: Badge hue adapts to theme

**Steps:**
1. Assign a color to a category
2. View CategoryBadge in light mode
3. Switch OS to dark mode

**Expected:** Badge pill color adjusts L/C values for dark theme (same hue, different lightness/chroma) — no re-render required, reactive

---

### TC-08: Backend color persistence

**Steps:**
1. Assign a color to a category
2. Reload the page

**Expected:** Color is still set on the category — persisted in DB via `category.color` column

---

## Not Proven By This UAT

- OKLCH hue values outside the curated palette are not tested (SwatchPicker only presents constrained palette)
- Multi-user concurrent color edits not covered
- Railway production deployment not verified (deferred to S07)
- Contrast accessibility of OKLCH hues in both themes not audited (deferred to S06/S07)
