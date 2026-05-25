# M003: Multi-User, Soft Deletes & Personalization

**Vision:** Evolve Keenti Finances from single-user to multi-user with isolated data, soft deletes with trash recovery, expanded category color personalization, per-user theme/typography customization, and onboarding wizard for new users.

## Success Criteria

- Two WorkOS users logged in simultaneously see fully isolated data across all entity types
- Deleted items appear in Trash view with restore capability, excluded from all standard queries and dashboards
- Category color picker with hex-to-OKLCH conversion and live light/dark preview inline in form
- Per-user primary color and typography presets applied via CSS variables from Settings page
- Onboarding wizard presents default category selection on first login
- All existing data migrated to user 1 with Flyway migrations clean

## Slices

- [x] **S01: S01** `risk:high` `depends:[]`
  > After this: Two WorkOS users log in; each creates a transaction and a category; each sees only their own data; existing admin data intact under user 1

- [ ] **S02: Soft deletes and trash view** `risk:medium` `depends:[S01]`
  > After this: Delete a transaction — it disappears from the list and dashboard; open Trash page, see it listed; click restore, it reappears in the normal view

- [ ] **S03: Category color picker upgrade** `risk:low` `depends:[S01]`
  > After this: Open category create form; see 360-degree hue wheel and hex input; type #FF5733 and see badge preview update in both light and dark; save and see the badge rendered correctly on the categories list

- [ ] **S04: User theme and typography settings** `risk:low` `depends:[S01]`
  > After this: Open Settings page; pick a primary color and change heading font to Geist; app re-renders instantly with new color and font; log out and back in — preferences persist

- [ ] **S05: Onboarding wizard and default categories** `risk:low` `depends:[S01,S03]`
  > After this: New WorkOS user logs in for the first time; sees onboarding wizard with default category options; selects a subset and confirms; lands in the app with chosen categories ready to use

## Boundary Map

### S01 → S02

Produces:
- user_id FK and Hibernate @Filter (userScope) on all entities — S02 stacks softDelete filter alongside
- UserContext @RequestScoped bean — available for all downstream slices
- Flyway migration V10+ adding user_id and deleted_at columns to all tables
- app_user.workos_id column and JIT provisioning logic

Consumes:
- nothing (first slice)

### S01 → S03

Produces:
- Per-user category ownership (user_id on category table)
- UserContext for scoping category operations

Consumes:
- nothing (first slice)

### S01 → S04

Produces:
- app_user table with workos_id column — S04 adds preference columns (primary_hue, heading_font, body_font)
- UserContext for loading preferences on page load

Consumes:
- nothing (first slice)

### S01, S03 → S05

Produces (S01):
- Per-user category model and Flyway-seeded default categories
- JIT provisioning for new users

Produces (S03):
- Color picker available for customizing categories created during onboarding

Consumes:
- S01: user model, category ownership, default category seed data
- S03: color picker UI for category customization after onboarding
