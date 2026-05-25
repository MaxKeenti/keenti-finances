# Project

## What This Is

A multi-user personal finance tracker for managing income, expenses, debts, and shared subscriptions. Built with Quarkus (hexagonal architecture) + SvelteKit + PostgreSQL, deployed on Railway. All monetary values in MXN. Auth via WorkOS passkeys.

## Core Value

Track net balance and cash flow accurately per user — know exactly where money comes from, where it goes, who owes what, and whether subscription members have paid their share. Each user's data is fully isolated.

## Project Shape

- **Complexity:** complex
- **Why:** Full-stack product with multi-user data isolation, auth integration, scheduled jobs, public-facing views, debt tracking with partial payments, auto-generated records, soft deletes, per-user theming, and onboarding — multiple interacting subsystems evolving from single-user to multi-user.

## Current State

M001 complete. Full application code across all 8 slices — auth, categories, contacts, transactions, dashboard, subscriptions, debts, and deployment readiness. Both Quarkus backend and SvelteKit frontend build into production-ready artifacts.

M002 in progress. UI overhaul (dock navigation, mobile card layouts, category color badges, system theme detection), subscription model improvements (owner participation toggle, manual billing trigger, transaction linking), passkey auth via WorkOS, and M001 deferred items (JUnit tests, Railway deployment, layerchart Svelte 5 fix, Fraunces font fix).

M003 planned. Multi-user data isolation (user_id on all tables, Hibernate @Filter enforcement), soft deletes with trash view, category color picker upgrade (360° hue wheel + hex input), per-user theme/typography customization, and onboarding wizard.

### What's shipped (M001)

- **Auth:** Password login with bcrypt, HMAC-SHA256 signed HTTP-only session cookie, SvelteKit auth guard.
- **Categories & Contacts:** Full CRUD with Flyway V2 migration.
- **Transactions:** Full CRUD (INGRESS/EGRESS) with category/contact selectors, MXN formatting.
- **Dashboard:** SVG bar chart (income vs. expenses) + trend line + net balance card driven by native SQL aggregation.
- **Subscriptions:** PERSONAL/SHARED types, member assignment, daily billing scheduler (7-day lead), payment recording, UUID token per shared subscription.
- **Public Subscription View:** Unauthenticated `/public/subscription/[token]` page showing member payment status; invalid tokens 404.
- **Debts:** Partial payment recording with auto-INGRESS transaction creation via TransactionUseCase; debt auto-transitions to PAID when fully paid.
- **Deployment Readiness:** `adapter-node`, multi-stage Dockerfiles, `%prod` Quarkus profile with env-var DB config, configurable `BACKEND_URL` proxy, lazy `SESSION_SECRET` enforcement, `/q/health` probe, `DEPLOY.md` checklist.

## Architecture / Key Patterns

- **Backend:** Quarkus 3.35.2 with hexagonal architecture (domain → application → infrastructure). Panache + Flyway for persistence. REST-Jackson for JSON serialization. Quarkus Scheduler for subscription billing jobs.
- **Frontend:** SvelteKit with Svelte 5, shadcn-svelte (bits-ui), Tailwind 4, superforms + formsnap + Zod, d3-scale for SVG dashboard charts, sonner for toasts.
- **Auth:** WorkOS AuthKit with passkeys (M002). SvelteKit owns the session. Quarkus is a trusted internal API.
- **Proxy:** SvelteKit catch-all `/api/[...path]/+server.ts` proxies to Quarkus via configurable `BACKEND_URL`. Browser never talks to Quarkus directly.
- **Multi-user (M003):** Hibernate @Filter for user_id scoping + soft-delete filtering. UserContext @RequestScoped bean. X-WorkOS-User-Id header from proxy. JIT user provisioning.
- **Currency:** MXN, single-currency throughout.

## Capability Contract

See `.gsd/REQUIREMENTS.md` for the explicit capability contract, requirement status, and coverage mapping.

## Milestone Sequence

- [x] M001: Personal Finance Tracker — Full app with transactions, dashboard, subscriptions, debts, and deployment readiness
- [ ] M002: UI Overhaul & Feature Completion — Dock navigation, mobile cards, theme detection, category badges, subscription improvements, passkey auth, and M001 deferred items
- [ ] M003: Multi-User, Soft Deletes & Personalization — Multi-user data isolation, soft deletes with trash view, category color picker upgrade, per-user theme/typography, onboarding wizard
