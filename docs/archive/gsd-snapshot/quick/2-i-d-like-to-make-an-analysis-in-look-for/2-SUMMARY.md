# Quick Task: Analysis — Components That Can Be Replaced with shadcn-svelte

**Date:** 2026-05-14
**Branch:** gsd/quick/2-i-d-like-to-make-an-analysis-in-look-for

## What Changed

This was a read-only analysis. No implementation files were modified.

---

## Findings

The project already uses **shadcn-svelte v1.2.7** and **bits-ui v2.16.3**. All components under `src/lib/components/ui/` are correct shadcn-svelte implementations. The gaps are in **page-level and feature-level components** where UI is hand-rolled using raw Tailwind instead of the available shadcn-svelte primitives.

---

## Replacement Opportunities (Priority Order)

### 1. CRITICAL — Badge (6+ files)

Manual inline-flex spans with `rounded-full px-2.5 py-0.5 text-xs font-medium` and hardcoded color classes.

**Shadcn-svelte replacement:** `Badge` component (already available if installed; add via `npx shadcn-svelte@latest add badge`)

**Affected files:**
- `src/routes/categories/+page.svelte` — category type badges (INGRESS, EGRESS, BOTH)
- `src/routes/debts/+page.svelte` — status badges (ACTIVE, PAID)
- `src/routes/debts/[id]/+page.svelte` — status badges
- `src/routes/public/subscription/[token]/+page.svelte` — cycle + status badges
- `src/routes/subscriptions/[id]/+page.svelte` — cycle, type, status badges
- `src/routes/subscriptions/+page.svelte` — type + cycle badges

**Before (repeated pattern):**
```svelte
<span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium bg-green-100 text-green-700">
  ACTIVE
</span>
```
**After:**
```svelte
<Badge variant="outline">ACTIVE</Badge>
```

---

### 2. HIGH — Alert / Error Messages (6 files)

Manual `<div class="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">` for form errors.

**Shadcn-svelte replacement:** `Alert` + `AlertDescription` components

**Affected files:**
- `src/routes/categories/+page.svelte`
- `src/routes/contacts/+page.svelte`
- `src/routes/debts/+page.svelte`
- `src/routes/login/+page.svelte`
- `src/routes/subscriptions/+page.svelte`
- `src/routes/transactions/+page.svelte`

**Before:**
```svelte
<div class="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
  {$message.text}
</div>
```
**After:**
```svelte
<Alert variant="destructive">
  <AlertDescription>{$message.text}</AlertDescription>
</Alert>
```

---

### 3. HIGH — Progress Bar (1 file)

Manual div-based progress bar with inline `style="width: {paidPercent}%"`.

**Shadcn-svelte replacement:** `Progress` component

**Affected file:** `src/routes/debts/[id]/+page.svelte`

**Before:**
```svelte
<div class="h-2 rounded-full bg-muted overflow-hidden">
  <div class="h-full rounded-full bg-green-500 transition-all" style="width: {paidPercent}%"></div>
</div>
```
**After:**
```svelte
<Progress value={paidPercent} class="h-2" />
```

---

### 4. MEDIUM — Cards Used as Inline Divs (3 files)

Several pages build card-like containers with `rounded-lg border bg-card p-5 space-y-3` directly instead of using the available `Card` components.

**Shadcn-svelte replacement:** `Card`, `CardHeader`, `CardContent` (already installed)

**Affected files:**
- `src/routes/debts/+page.svelte` — debt list items styled as cards
- `src/routes/subscriptions/+page.svelte` — subscription list items styled as cards
- `src/routes/debts/[id]/+page.svelte` — debt detail header card

---

### 5. MEDIUM — Back/Navigation Links (2 files)

Custom `<a>` elements with `inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors` pattern.

**Shadcn-svelte replacement:** `Button variant="link"` or `Breadcrumb` component

**Affected files:**
- `src/routes/debts/[id]/+page.svelte`
- `src/routes/subscriptions/[id]/+page.svelte`

---

### 6. LOW — Empty States (4 files)

Repeated `<div class="rounded-lg border border-dashed p-8 text-center text-muted-foreground">` pattern. No direct shadcn-svelte component, but worth extracting into a shared `EmptyState.svelte` component.

**Affected files:**
- `src/routes/categories/+page.svelte`
- `src/routes/contacts/+page.svelte`
- `src/routes/debts/+page.svelte`
- `src/routes/transactions/+page.svelte`

---

### NOT Applicable — App Shell / Navigation

`sidebar.svelte` and `bottom-nav.svelte` use custom CSS tokens (`bg-sidebar`, `border-sidebar-border`, etc.) and are application-specific layout components. shadcn-svelte does not provide layout primitives; these should remain custom.

---

## Components to Install (if not present)

Run the following to add missing shadcn-svelte components:

```bash
npx shadcn-svelte@latest add badge
npx shadcn-svelte@latest add alert
npx shadcn-svelte@latest add progress
```

`Card`, `Button`, `Table`, `Dialog`, `Select`, `Calendar`, `Form`, `Input`, `Textarea`, `Label`, `Separator`, `Popover`, `DropdownMenu`, and `Sonner` are already correctly installed and used.

---

## Files Modified

None — this was a read-only analysis task.

## Verification

- Explored all `.svelte` files in `src/routes/` and `src/lib/components/`
- Cross-referenced patterns against shadcn-svelte component catalog
- Confirmed existing `src/lib/components/ui/` implementations are correct shadcn-svelte wrappers
