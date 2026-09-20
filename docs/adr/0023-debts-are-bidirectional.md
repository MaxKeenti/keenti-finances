---
status: accepted
---

# A Debt is bidirectional and carries a Direction

A Debt used to be only money owed **to** the User. Money the User owed someone else had nowhere to live: a Credit Financial Account covers a credit line with a bank, but not an informal "I borrowed 500 from Ana". The User asked to keep record of both sides in one interface, the way Transactions already carry both.

## Decision

A Debt carries a **Direction**, reusing the same two values a Transaction uses:

- `INGRESS` — the Contact owes the User. This is what every Debt recorded before this decision meant.
- `EGRESS` — the User owes the Contact.

A Debt's Direction **is** the Direction of the Transaction each of its Debt Payments creates. ADR-0005 is unchanged in substance — one Debt Payment still creates exactly one Transaction — but the Transaction's Direction now comes from the Debt instead of being hard-coded to `INGRESS`. Settling what the User owes therefore lowers Net Balance and draws from the chosen Financial Account, exactly as ordinary spending does; the Category offered must match the Direction.

Because that Transaction is an ordinary EGRESS, a payment on a Debt the User owes accepts **Box Funding** on the same terms spending does: funding lines may not exceed the payment amount, a Box may appear once, and a Box without the balance to cover its line is a conflict. A payment on a Debt owed *to* the User carries none — money coming in is not spending, and there is nothing for a Box to fund.

The two sides are **never netted**. Owing Ana 500 while Ana owes the User 300 is two obligations, not one of 200. `/debts` shows one headline total per side and groups outstanding amounts per counterpart per side, so one Contact can appear on both sides at once as two separate balances. A bulk payment settles one named Direction and leaves the other balance untouched.

A Debt's Direction is frozen once any Debt Payment exists. Flipping it would contradict Transactions that already moved money the other way, and those are not reversed.

Outstanding Debts in either Direction remain summaries of what is recorded. Neither side enters Net Balance, In Boxes, or Available to Spend, which count money that actually moved.

## Consequences

- `V36__add_debt_direction.sql` adds the column with a `'INGRESS'` default, which backfills every existing row to its historical meaning. `DebtRequest` and `BulkPaymentRequest` default an omitted Direction the same way, so the change is backward compatible on the wire.
- `DebtRepository.findActiveByContactIdOrderByCreatedAt` takes a Direction: a bulk payment queue is per side, not per Contact.
- The `/debts` drill-down key gains a side prefix (`in-contact-9503`, `out-debt-9914`) because a Contact id alone no longer identifies one balance. Old `?debtor=` links no longer resolve and fall back to showing everyone, which the loader already did for unknown keys.
- User-facing copy moves from "Money owed to you" / "Te deben" to "Debts" / "Deudas", with each side named underneath. This revisits decision D4 (10 September 2026), which named the section for the one direction it then had.
- A deliberate non-goal: there is no net-position figure across the two sides. Net Balance is the app's one headline scalar, and a second netted number next to it would read as a rival to it.
- Box Funding is accepted on a single Debt Payment only. A bulk payment creates one Transaction per Debt it settles, and splitting one funding line across them has no obvious right answer — pro-rata, oldest-first, and per-Debt lines all behave differently once a Box runs out mid-queue. Until that is decided, a bulk payment is funded wholly from Available to Spend.

## Considered options

- **`RECEIVABLE` / `PAYABLE` as the Direction values:** rejected. The values would then need mapping onto `INGRESS`/`EGRESS` at every payment, and the glossary already warns against inventing synonyms for Direction.
- **A separate route for money the User owes:** rejected. It duplicates the page, both loaders, and the nav entry to show the same records with the sign flipped.
- **A single net balance per Contact:** rejected. Two informal debts with different descriptions, dates, and settlement schedules are not interchangeable, and netting them destroys the record the User asked to keep.
- **Recording what the User owes as a Credit Financial Account:** rejected. A Financial Account is where money is held or owed to an institution; it has opening balances, statements, and transfers. An informal debt to a friend has none of those.
