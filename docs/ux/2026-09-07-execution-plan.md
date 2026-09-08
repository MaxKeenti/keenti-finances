# UX/UI execution plan — 7 September 2026

Status: revised after the user's conditional approval; decisions below remain subject to their explicit sign-off. No application changes are implemented by this plan.

Companion: [development UX/UI audit](2026-09-07-audit.md).

## Intended outcome

A user with irregular income can understand their recorded financial position, see what is reserved and due, record spending from a Box without duplicate work, and distinguish money owed to them from money already received. Existing accounting behavior remains explicit and traceable.

## Review decisions and recommended defaults

| Decision | Recommended starting point | Consequence |
|---|---|---|
| Dashboard scope | Current position and actionable existing data first; optional 30-day planning view second | Delivers clarity before introducing a new forecast calculation |
| Available to Spend | Keep the canonical calculation; describe it as money unallocated to Boxes and disclose future-cost exclusions | Avoids silently changing accounting semantics; forecast gets a distinct label |
| Mobile navigation | Stable Dashboard entry, Transactions, Boxes, and More; preserve explicit existing pin preferences across the navigation preference migration | Makes planning discoverable while respecting customization |
| Subscription links | Immediately clarify income/contribution linking; keep provider-expense association out of 3B | Separate decision D3 must document the undocumented INGRESS-only service constraint, then review API/data model and assess whether any Flyway database migration is needed |
| Debts wording | User-facing “Money owed to you” / “Te deben,” with Debt retained in the domain | Separates receivables from credit owed |
| Credit Financial Account terminology | Negative signed balance: “Credit debt” / “Deuda de crédito”; positive signed balance: “Credit in your favor” / “Crédito a favor”; zero: “No credit debt” / “Sin deuda de crédito” | Show the magnitude with the matching label; preserve the signed value in calculations |
| Available credit | “Available credit” / “Crédito disponible,” only when a credit limit exists | Separate limit-derived capacity; never include it in Net Balance, money held, or Available to Spend |
| Confirmed statement obligations | “Outstanding statement payment” / “Pago pendiente del estado de cuenta,” with its due-date status | Separate from current signed credit balance; do not add it again to Net Balance |
| Automatic actions | Continue user-confirmed allocation and manual billing generation | Preserves ADR-0021 and ADR-0019; automation is a separate product decision |

### Decision ownership and gates

The user, as product owner, signs off on product language, navigation, and accounting/API decisions. The implementing agent prepares the decision notes, worked examples, and proposed contracts for review. Conditional approval of this plan does not settle the choices below. Record approval and date in each note before cutting its dependent implementation issues. Deadlines are dependency milestones, not invented calendar commitments; unrelated fixes may proceed independently once authorized.

| Gate | Required decision and artifact | Sign-off due |
|---|---|---|
| D1 | Approve the balance/credit terms above and a tracking-mode data-source note: reuse authoritative existing metadata if available, otherwise specify an additive API contract, compatibility, and unknown-mode behavior | Before 1A-balance implementation issues |
| D2 | Approve a short obligation-status design note, containing a reviewable status truth table, shared by credit warnings and subscription summaries | Before 1D, 3A, or 3B implementation issues |
| D3 | Investigate and document the INGRESS-only link constraint, then decide whether provider-expense association is wanted; if yes, approve a documented contract covering direction, relationship/cardinality, correction lifecycle, API validation, and schema/backfill needs | Before separate 3D implementation issues; does not block income-link copy or 3B under its current contract |
| D4 | Approve navigation and receivables wording | Before 2A and 3C implementation issues |
| D5 | Approve the planning-horizon formula and its Box Plan integration contract | Before Phase 5 implementation issues, after 2B's shape is settled |

D2 must define independently: (a) subscription billing generation due/pending versus caught up under ADR-0019, (b) generated contribution records paid/unpaid and any justified overdue interpretation, and (c) confirmed credit statement payment due today/upcoming/overdue/settled. A past subscription billing date alone must not assert an unpaid provider charge. Specify the user's time-zone calendar day, boundary examples, missing-data states, and precedence when multiple conditions coexist. Deriving presentation states must not generate billing or advance dates. The note must express these states as a reviewable status truth table — inputs (dates, generation state, payment state, missing data) mapped to the single presented state and action — because 1D's acceptance is checked against that table. The note is a small design deliverable; implementing the state model is separately sized in 1D.

D3 must first establish the facts before proposing any change. ADR-0010 records `transaction.subscription_id` as a nullable FK with retroactive multi-select annotation; it does not restrict Direction. The INGRESS-only rule is an undocumented service constraint in `TransactionService.linkSubscription` (`backend/.../application/service/TransactionService.java:246`), mirrored by the frontend candidate filter. D3 must determine whether that constraint was deliberate and why it was never documented, then document the current constraint and its rationale alongside whatever change is agreed. Do not frame this work as reversing a restriction recorded in ADR-0010; ADR-0010 is silent on direction. If the outcome changes recorded architectural intent, capture it in the appropriate ADR update as part of D3's artifact.

## Slice 0 — Reproducible fixtures and verification setup

Prepare isolated synthetic test data before Phase 1's acceptance gates. The implementing agent owns the seed/harness work; the user reviews the resulting fixture manifest alongside the relevant decision notes. Do not depend on the incidental records encountered during the audit or overwrite existing development data.

- **0A, Phase 1 prerequisite:** Deterministic fixtures for tracking inactive/active, legitimate zero balance, positive net balance with excessive Box reservations, negative Net Balance, an empty Box while Available to Spend is negative, and a genuine confirmed-statement mismatch. Include populated and genuinely empty shared subscriptions, debt progress at 0/10/100%, and date boundaries under a controlled clock. Provide request-level failure injection in the test harness for failed dashboard totals and partially failed subscription sections; never disrupt a live service to create a failure. Implemented; see the [fixture manifest](fixtures/2026-09-07-manifest.md).
- **0B, later journey prerequisite:** Extend the same seed set with an underfunded Spending Budget; active/overdue/completed Saving Goals; populated shared subscriptions with both owner-participation settings and partial contributions; and the credit-in-favor/unpaid-statement combination. Build this before 2B/3A/3B and Phase 4 acceptance, not after implementation reaches an untestable exit gate.
- **0B fixture `FX-DASH-NEG-01`, Phase 4 prerequisite:** A named isolated synthetic scenario owned by the fixture manifest, exercising a positive Net Balance with Box reserves that exceed it. Its values are invented for deterministic verification and are deliberately distinct from any figure observed during the audit; no acceptance check may be bound to live or incidental data.

  | Element | Expected value (MXN) |
  |---|---|
  | Asset Financial Account balances (money held) | 4,120.50 |
  | Credit Financial Account signed balance (credit in your favor) | 55.50 |
  | Net Balance | 4,176.00 |
  | In Boxes | 5,300.00 |
  | Available to Spend | −1,124.00 |
  | Unpaid confirmed statement payment | 310.25 |
  | Credit limit | 9,000.00 |
  | Available credit (limit-derived, excluded from the totals above) | 9,055.50 |

  Arithmetic the fixture asserts: `4,120.50 + 55.50 = 4,176.00`; `4,176.00 − 5,300.00 = −1,124.00`; `9,000.00 + 55.50 = 9,055.50`. The 310.25 confirmed statement payment is a separately identified obligation and is never subtracted from Net Balance. Available credit is limit-derived capacity and never enters money held, Net Balance, In Boxes, or Available to Spend. The manifest owns these values; if they are revised, Phase 4's acceptance text follows the manifest rather than being restated independently.
- Document stable fixture IDs, expected balances/statuses, setup/reset commands, clock/time-zone inputs, and which acceptance criteria each fixture supports. Keep synthetic accounts isolated and reset only data created by this harness.
- Make normal-data scenarios available to browser tests and Railway development review. Failure scenarios run against controlled mocked/intercepted requests in the test harness; they need not occur on the live dev service.

**Acceptance:** Repeating setup yields the same expected state without duplicating records. Each Phase 1 acceptance case has an executable fixture or failure scenario before its exit gate is evaluated. Each later slice names its 0B scenarios. Preserve existing development records. Slice 0 prepares verification data; it does not change accounting semantics.

## Phase 1 — Correct misleading states

Deliver as small, reviewable changes, grouped by behavior rather than one broad redesign.

### 1A-loader. Explicit failure and empty states (independently shippable)

- Supply per-section loading/error status from dashboard and subscription loaders; stop rendering failed totals as zero or failed lists as empty (UX-14).
- Keep successful sections usable, with an explicit partial-data notice and Retry for unavailable sections.

**Scope:** SvelteKit loaders, their page data types, UI states, and localized messages. No new backend tracking-mode contract is required.

**Acceptance:** With 0A failure injection, a failed summary displays unavailable/Retry and never a fabricated zero. A legitimate zero remains distinguishable from failure. Partially failed subscription loading preserves successful sections while distinguishing unavailable members/payments from a genuinely empty list. This slice can ship without waiting for D1 or the broader balance presentation.

**Status — implemented, pending the user's review.** Scope covered: the dashboard loader (summary and account-warning sections, with per-credit-account partial reporting), the subscription detail loader (members, payment records, linked income, and link candidates), and the shared `+layout.server.ts` balance summary that the app-shell header renders on every page — the same fabricated-zero bug, previously logged as “using zero fallback.” Sections are typed as `Section<T>` (`$lib/types/section.ts`), loaded by `loadSection` (`$lib/server/section-load.ts`) so one rejection cannot discard a sibling's result, and validated at the boundary by `$lib/server/payloads.ts` so a partial or malformed HTTP 200 becomes `unavailable` instead of 0.00. Consumers of the shared summary (app-shell, Boxes list/detail, transaction create/edit) show the unavailable state and disable only the actions that need the missing figure — Box deposits and optional Box funding/distribution — while recording a Transaction, withdrawing from a Box, generating billing, and existing income linking are unchanged. Verification: `frontend/tests/section-loaders.test.ts` drives the real route loaders against the 0A fixture backend for success, genuine zero, non-OK, rejection, malformed/partial payload, partial subscription failure, and recovery, asserting request isolation and that no page read issues a write; full `bun test` (76 pass), `bun run check` (0 errors, no new warnings), and `bun run build` pass.

Review follow-up: when only the link-candidate dependency fails (`GET /api/transactions` unavailable) while every other section loads, the subscription detail page now shows the shared unavailable/Retry notice instead of a dead-end paragraph. It is suppressed when linked income failed too, since that section's own notice already retries the same dependency. Open dialogs cannot submit a stale selection: a reload that removes candidates clears the multi-select set and the single-payment radio value, and the submit buttons stay disabled while nothing valid is selected.

Root browser checks against the 0A fixture server: with `GET /api/dashboard/summary=500` the dashboard summary shows unavailable rather than 0.00, and Retry against the still-failing route reports the “still unavailable” message; with the fixture server restarted without `FIXTURE_FAIL`, Retry restores the fixture's real figures (net 2500.75, reserved 500, available 2000.75) without a page reload. With `GET /api/subscriptions/{id}/payments=500` the subscription detail page keeps members and linked income intact while payment records report unavailable; the header balance summary, whose route the scenario does not declare, shows its own unavailable state (a harness artifact of the undeclared route, per the 0A manifest).

Not covered here and still open: 1A-balance's explanatory copy, D1/D2-dependent states, and browser verification across the full locale/theme/viewport matrix — the checks above were English/dark at desktop width.

### 1A-balance. Balance explanations and tracking-mode contract

- Correct Net Balance copy according to whether account tracking is active (UX-01).
- Add the Available to Spend exclusion note and a plain explanation of the formula.
- Make negative-state copy describe supported facts. Distinguish excess reservations from negative Net Balance and from an actual record mismatch (UX-02).
- Resolve D1 before implementation: confirm whether authoritative tracking-mode metadata can be reused. If it cannot, implement and verify the separately approved additive backend contract before consuming it in explanatory copy. Do not infer mode from totals or silently use the inactive-mode description when mode is unknown.
- Update the Boxes feature document's outdated unconditional transaction-only balance formula to reference ADR-0022.

**Likely scope:** dashboard, app-shell balance strip, Boxes pages, messages in both locales, feature docs. A backend metadata change is a distinct conditional API work item under D1, not part of 1A-loader.

**Acceptance:** Correct descriptions before/after account activation; no claim that expected income or future bills are included. The 0A negative Net Balance, excessive Box allocation, and genuine mismatch fixtures have distinct guidance. Empty Boxes do not offer impossible withdrawals. Unknown tracking mode does not display a guessed formula. If an API addition is required, cover compatibility and unknown/unavailable metadata explicitly.

### 1B. Progress, initial validation, and copy under existing contracts

- Fix the Debt progress at the debt page call site: remove green from the root track and retain the primitive's indicator behavior (UX-05). Do not change the shared primitive to compensate for this caller's styling.
- Rename the current subscription linking action and description to match INGRESS behavior (UX-03).
- Remove required-field errors on initial debt form load and on newly added untouched Box rows. Keep meaningful validation after interaction/submission (UX-08/09).
- Change description examples with transaction direction. Explain Debt Payment's generated income Transaction near Save.

**Likely scope:** debt detail, subscription linking copy, transaction form/allocation editor, translations. Date-state modeling is excluded and belongs to D2/1D.

**Acceptance:** Progress correctly renders 0%, 10%, and 100%. No blank form opens in an error state. Linking is clearly about existing received income and does not imply creating an expense. Debt payment copy explicitly says one income Transaction will be recorded.

### 1C. Responsive alert repair

- Move alert actions into normal responsive layout instead of relying on fixed right padding (UX-12).
- Test long account names and Spanish action text at 320, 390, 768, and 1280px.

**Acceptance:** Alert content and actions never overlap; every action remains readable, focusable, and reachable with text enlarged.

### 1D. Implement the reviewed obligation-status model

- Implement D2's shared date/status derivation for Dashboard warnings, credit detail, and subscription list/detail (UX-04).
- Replace ambiguous “next” descriptions with the approved specific state and action, without generating billing or changing stored dates.
- Expose/reuse the same state contract in 3A and 3B rather than defining another model there.

**Acceptance:** 0A fixtures for yesterday/today/tomorrow, user time-zone boundaries, pending generation, paid/unpaid generated records, and confirmed-statement obligations match D2's truth table. A past billing-generation date does not independently claim an unpaid provider charge. Reading any page creates no billing records and advances no billing dates.

**Phase 1 exit:** 0A setup is reproducible and 1A-loader, 1A-balance, 1B, 1C, and 1D pass their scoped acceptance checks. All P1 findings are either resolved and verified, or explicitly deferred by the product owner with a reason and dependent slice recorded. Unresolved D1/D2 gates block their slices, not shipment of independent loader, progress, or responsive fixes. Use controlled failure tests rather than causing a live Railway outage. Development review uses 0A's normal-data scenarios; later journeys use 0B when their slices are ready.

## Phase 2 — Expose existing planning and simplify recording

### 2A. Navigation and Box overview

- Apply the reviewed desktop labels and mobile navigation model (UX-11).
- Change “View history” to a destination that describes the complete Box page; make names/cards clear entry points (UX-06).
- Show active-plan type and a concise status; for unplanned Boxes show one Add plan action.
- Organize detail around the plan, available reserved money, and history. Move archival to secondary actions.

**Acceptance:** A user can find planning from Boxes without guessing a history link. A no-plan state has one clear primary setup action. Existing custom navigation preferences survive the navigation preference migration — the upgrade of stored user navigation settings to the new model, which is not a Flyway database migration and must not be confused with one. Keyboard focus and screen-reader names remain meaningful.

### 2B. Plan setup and funding suggestions

**Prerequisite:** 0B plan/budget fixtures and 2A. Document the resulting Box Plan integration shape (cadence, anchors, period boundaries, revision semantics, and available summary fields); Phase 5 consumes this contract.

- Present Saving Goal and Spending Budget as clear purpose choices.
- Ask essential inputs first; expose cadence anchors and optional commitment under customization (UX-07).
- Suppress zero-period previews until the inputs support a calculation; explain the final suggestion and current Box balance.
- Use consistent plain language for Funding Triggers and distinguish plan guidance from actual deposits.

**Acceptance:** Create/revise/end flows preserve historical plan terms and one-active-plan rules. A goal and budget can be understood without knowing “cadence” or “trigger.” Suggestions still require confirmation. No automatic deposits are introduced.

### 2C. Record money movement and optional Box funding

**Prerequisite:** 0A's negative Available to Spend and empty-Box fixtures, which supply the negative-balance and funding acceptance cases below; and 2A.

- Make income/expense selection direct and understandable.
- Explain Account = where money moved, Category = what it was for, Box = which reserved money funded it.
- Keep optional Box funding discoverable and show amount, funded portion, remainder, and resulting Available to Spend together (UX-08).
- Add a contextual record-expense-from-this-Box entry that prefills a draft; the user still chooses the real account and confirms.
- Add focused transaction search/filters; keep sort/page-size preferences secondary.

**Acceptance:** A fully Box-funded expense reduces Net Balance and the Box equally, leaving Available to Spend unchanged. Partial funding only reduces Available to Spend by the remainder. Saving does not also create a separate withdrawal. Corrections and deletion/refund behavior remain atomic; using 0A's negative Available to Spend and empty-Box fixtures, real expenses remain recordable with a negative unallocated balance and the funding summary states the consequence correctly. Cancelling creates nothing.

**Phase 2 exit:** Walk through receive income → choose allocation → record purchase → understand remaining Box money on phone and desktop, using 0A's balance and negative Available to Spend fixtures together with 0B's plan/budget fixtures — not incidental development data.

## Phase 3 — Connect Accounts, Subscriptions, and Debts

### 3A. Credit account hierarchy

- Lead with the signed-balance labels approved in D1: Credit debt / Deuda de crédito, Credit in your favor / Crédito a favor, or the zero state. Keep Available credit / Crédito disponible separate from those balances. Present confirmed statement obligations using D2/1D's status contract and the next relevant action (UX-04, UX-13).
- Explain confirmed statement snapshots versus current ledger activity.
- Offer a contextual Record card payment action using the existing Transfer workflow.
- Move tracking-start date, archival, and rarely edited settings lower in the hierarchy.

**Acceptance:** Recording card payment is a neutral Transfer, does not change Net Balance or create another expense, and visibly updates statement payment allocation. Refund income is not mislabeled as a card payment. The “credit in favor plus unpaid confirmed statement” fixture explains both figures and offers review, rather than declaring either number wrong.

### 3B. Subscription period summary

- Add period, full provider cost, expected contributions, collected amount, outstanding contributions, and own-share labels (UX-10).
- Provide Add members at the empty state and contextual explanations for Generate billing, Record payment, and Link existing income.
- Label existing monthly/annual totals as gross price equivalents. Do not imply annualized totals are current cash due.
- Use the D2/1D status model. Work within the existing income-link contract; provider-expense association is explicitly excluded and requires D3/3D. Show unavailable provider-charge linkage honestly rather than inferring a recorded charge from billing generation.

**Acceptance:** Using 0B, cover personal, shared owner-participating, owner-not-participating, no members, partial contributions, paid, overdue, and no-generated-billing fixtures. Linking existing income does not create another Transaction. Repeated billing generation does not duplicate records. Gross subscription cost and received contributions are separately labeled; the summary does not claim provider-charge linkage that the existing contract cannot supply.

### 3C. Receivables and supporting screens

- Apply the reviewed “Te deben” framing and display outstanding totals with clear links to detail (UX-09).
- Explain payment effects and link the created Transaction after success.
- Make Contacts and Trash actions practical on mobile; improve heading/action wrapping (UX-12).

**Acceptance:** Receiving one Debt Payment creates one income Transaction and updates the remaining Debt. Mobile recovery actions are visible/reachable, and permanent deletion remains clearly distinguished from recovery. No destructive actions are exercised against existing user records for UI testing.

### 3D. Optional provider-expense association — separate scope

Only after D3 approval, implement the contract it documents across persistence (if needed), API validation, correction/deletion lifecycle, frontend association flows, and summary integration. The service-level INGRESS-only rule and its documented rationale are the starting point; relaxing it is a deliberate change to that constraint, not a correction of ADR-0010. Specify how provider EGRESS differs from contribution INGRESS and whether the existing nullable FK is sufficient. An additional relationship or role model requires a new append-only Flyway migration and a backfill/compatibility plan; allowing EGRESS on the existing nullable FK alone does not necessarily require a database migration. Resolve that in D3 rather than assuming a schema change or treating this as copy work.

**Acceptance:** Existing income links remain valid. Provider expense and received contributions are independently traceable; linking creates no synthetic Transaction. Editing, unlinking, deleting, and restoring each side obey the approved contract without duplicate income/expense. If a Flyway database migration is required, verify historical-link preservation and upgrade behavior against the fixture manifest's scenarios. 3B may ship without this optional capability.

## Phase 4 — Build a useful dashboard from existing data

This phase depends on the corrected meanings and connected workflows above.

Recommended layout, in reading order:

1. **Current position:** money held, Credit debt / Deuda de crédito and Credit in your favor / Crédito a favor using D1's signed-balance rules, Net Balance, In Boxes, and Available to Spend with a breakdown. Available credit / Crédito disponible is separate limit-derived capacity, excluded from these totals; outstanding statement payments remain separately identified obligations.
2. **Needs attention:** overdue confirmed statements, pending billing generation, over-allocation, and data needing review; show the appropriate action for each.
3. **Your plans:** existing Saving Goal and Spending Budget summaries, amounts reserved, and suggested top-ups with links.
4. **Money expected:** outstanding Debts and member contributions, explicitly not received and excluded from available money.
5. **History:** current annual charts and transaction trends.

**Implementation:** Compose a dedicated summary response/read model using existing sources. Return explicit section availability, timestamps where meaningful, and traceable item IDs. Avoid loading every detail page's full history just to render the dashboard. Do not silently adjust the account ledger or Box balances in a read operation.

**Acceptance:** Every amount has a labeled source and drill-down. Expected receipts are not included in available money. Partial data failure is visible. The empty/new-user dashboard points to setup rather than showing unexplained zeros and charts. Against the isolated `FX-DASH-NEG-01` fixture, the dashboard itself displays money held 4,120.50 MXN, credit in your favor 55.50 MXN, Net Balance 4,176.00 MXN, In Boxes 5,300.00 MXN, and Available to Spend −1,124.00 MXN, with the arithmetic `4,120.50 + 55.50 = 4,176.00` and `4,176.00 − 5,300.00 = −1,124.00` available on that page. The 310.25 MXN outstanding confirmed statement payment is separately labeled and is not subtracted again from Net Balance. The 9,055.50 MXN available-credit figure, if shown, is explicitly separate limit-derived capacity, not money held. This check runs only against that fixture; live development balances are not an acceptance input.

**Moderated review task:** Using the same `FX-DASH-NEG-01` fixture, ask the user to explain why Available to Spend is negative and distinguish it from an unpaid statement, using the dashboard only. Record their interpretation and friction; this is usability evidence, not a deterministic release check or a claim already established by the audit.

## Phase 5 — Optional planning horizon, after agreeing the calculation

The earlier conversation proposed a 30-day view. Treat this as an additional product capability requiring a short design decision, not a copy change.

**Dependencies:** D5 approval, 2B's settled Box Plan integration shape and cadence/anchor semantics, 0B plan fixtures, D2/1D obligation states, and Phase 4's summary/data-availability contract. Initial formula exploration may happen earlier, but implementation must consume these agreed contracts. If the approved horizon requires provider-expense associations, 3D also becomes a hard dependency; otherwise disclose that exclusion.

- Define which date makes a cost relevant and whether it is forecast, already recorded, or a cash-settlement obligation.
- Define how a planned essential overlaps a Box Spending Budget or subscription, so a cost is included once.
- Distinguish cash needed in a specific account from aggregate Net Balance. Positive credit balance is not universally interchangeable with cash.
- Treat already-recorded card purchases/MSI differently from the future cash transfer used to settle the card. Do not subtract the same expense again from Net Balance; still show account-level payment timing.
- Keep expected income separate by default. An optional scenario may include explicitly selected expected receipts, with uncertainty visible.
- Show planning completeness: missing food, transport, or other essentials means the result cannot promise safe spending.
- Reuse Box Plans. Add data fields or a new planning entity only where the reviewed contract requires them; any database work uses new append-only Flyway migrations.

**Deliverable before implementation:** Agreed formula and worked examples for cash spending, a Box-funded expense, a recorded card purchase with unpaid statement, shared subscription reimbursements, MSI, irregular expected income, and an incomplete essentials plan. Record any deliberate change to ADR-0021/0022 rather than silently contradicting them.

**Acceptance:** One cost is never counted twice. Forecast changes cannot alter recorded balances. Horizon dates use the user's time zone. Every forecast identifies included/excluded costs and assumptions.

## Verification and rollout

- Use focused unit/integration tests for financial calculations, single-record effects, date states, and failure/empty distinctions. Do not add tests that merely mirror wording.
- Use browser checks with controlled representative fixtures for interactions and layouts: desktop and phone, Spanish and English, light and dark, keyboard navigation, visible focus, dialog dismissal, enlarged text, and long names. This audit covered only Spanish/dark; the broader matrix is implementation verification work.
- Use the 0A/0B fixture manifest, prepared before the dependent slices, for active/overdue/completed goals, underfunded budgets, an empty Box with negative Available to Spend, populated shared subscriptions absent from the audit flows, and `FX-DASH-NEG-01` for Phase 4's dashboard figures.
- Run the repository's relevant required checks per implementation change, then deploy to development and repeat that slice's normal-data journeys using its prepared fixtures. Run failure injection in the controlled test harness. Do not make a Phase 1 release depend on Phase 3 or Phase 5 journeys. The user reviews the development result before a separate production release decision.
- Preserve successful existing behavior: signed account balances, neutral Transfers, explicit Box Funding, user-confirmed suggestions, prospective plan revisions, and no duplicate Debt Payment income.

## Suggested delivery order

| Slice | Depends on | Expected size |
|---|---|---|
| 0A: Phase 1 fixtures and failure harness | Existing accounting contracts | Medium |
| 0B: later plan/subscription journey fixtures, including `FX-DASH-NEG-01` | 0A harness; finalize expected statuses with D2 | Medium |
| 1A-loader: failure/empty states | 0A; no D1/API dependency | Small |
| 1A-balance: explanations | 0A, D1; approved metadata API work only if needed | Medium |
| 1B: progress, existing-contract copy, initial validation | 0A; no D2 dependency | Small–medium |
| 1C: responsive alerts | 0A long-name fixtures | Small |
| 1D: obligation-status model | 0A, D2 | Medium |
| 2A: navigation and Box entry points | D4; 0B for populated-plan status checks | Medium |
| 2B: plan setup and integration shape | 0B, 2A | Medium |
| 2C: transaction journey and filtering | 0A, 2A; funding invariants | Medium–large |
| 3A: credit workflow | 0B, D1, 1A-balance, 1D | Medium |
| 3B: subscription summary, existing income links | 0B, D2, 1A-loader, 1B, 1D | Medium–large |
| 3C: receivables and supporting mobile screens | D4, 1B, 1C | Medium |
| 3D: optional provider-expense association | D3 constraint investigation and documented contract; Flyway database migration decision | Separate scope |
| 4: composed dashboard | D1, 0B (`FX-DASH-NEG-01`), 1A-loader/balance, 1D, 2B and 3A–3C summaries; 3D only if included | Large |
| 5: planning horizon | D5, 2B integration shape, 0B, 1D, Phase 4; 3D if required by formula | Separate scope |

Sizes are relative, not calendar commitments. Create implementation issues from these slices after review, using the repository's issue-tracker and triage conventions. This audit did not open issues or start implementation.
