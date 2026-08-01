---
status: accepted
---

# Boxes are internal allocations, not Transactions or accounts

A Box is a User-scoped envelope over the existing all-time Net Balance. Depositing, withdrawing, or transferring Box money is an internal allocation and never creates a Transaction. Actual money movement remains represented exclusively by INGRESS and EGRESS Transactions.

The three headline amounts obey:

```text
Net Balance        = all-time INGRESS - all-time EGRESS
In Boxes           = sum of active Box balances
Available to Spend = Net Balance - In Boxes
```

An EGRESS Transaction may carry exact Box Funding amounts from several Boxes; its remainder comes from Available to Spend. This funding is part of the Transaction lifecycle: editing is atomic and deletion refunds the Box amounts. An INGRESS Transaction may instead be followed by independent Box Movement deposits linked to it for traceability. Those deposits are not silently reversed if the source Transaction later changes because their money may already have been reallocated or spent.

Box balances never go negative. Available to Spend may go negative because recording a real Transaction takes precedence over enforcing an envelope. The negative amount is an unreconciled state that blocks new Box deposits but not Transaction entry, corrections, withdrawals, or other actions needed to reconcile it.

Box is a new root entity under the query-scoped multitenancy model of ADR-0011 and ADR-0014. Its movements and plan records are children that inherit User scope through their Box. Any cross-root link to Transaction must validate that both records resolve within the current User scope. Infrastructure entities and repositories follow ADR-0001, ADR-0012, and ADR-0018.

Archiving is a Box lifecycle state, not soft-delete. A Box must have a zero balance and no active Box Plan before archive. Closing the plan is a separate, explicit User decision; archive never completes, abandons, or ends it automatically. History and Transaction links remain readable. Corrections that would change an archived Box require restoring it first.

## Consequences

- Existing Transaction aggregates remain the sole input to Net Balance.
- Dashboard and app-shell readers subtract current Box allocations to compute Available to Spend.
- Category continues to describe why a Transaction occurred; Box Funding describes which reserved money paid it.
- Direct Box transfers are atomic paired allocations and do not pass through a synthetic Transaction.
- Box history needs effective dates and audit timestamps. Backdated corrections must preserve non-negative running Box balances.
- Database work must use a new Flyway migration; deployed migrations remain append-only.

## Considered options

- **Represent Box deposits and withdrawals as Transactions:** rejected because no money enters or leaves and Net Balance would become false.
- **Treat a Box as a bank account:** rejected because the app has no account ledger or transfer reconciliation and a Box is only an organizational envelope.
- **Reject EGRESS Transactions when Available to Spend is insufficient:** rejected because the tracker must record what actually happened even when the User overspent.
- **Automatically reverse INGRESS distributions with their source Transaction:** rejected because downstream Box spending could make the reversal impossible or produce a negative Box.
