---
status: accepted
---

# Soft-delete does not cascade across root-entity boundaries

Soft-deleting a Debt or Subscription hides the parent and its child entities (Debt Payments, Payment Records, Subscription Members) via the parent's `deleted_at` filter, per ADR-0014. It does **not** soft-delete any peer-root Transactions linked to it — neither the INGRESS Transactions auto-created by Debt Payments (ADR-0005), nor Transactions retroactively linked to a Subscription via the nullable FK (ADR-0010).

The money those Transactions describe actually moved. The Debt or Subscription record is just bookkeeping on top of that movement. Trashing the bookkeeping shouldn't erase the financial history. The dashboard's net balance reflects reality and should keep doing so even after the user deletes the grouping. If the user wants the Transaction gone too, they can delete it separately — its own trash entry is independent.

## Considered options

- **Cascade soft-delete to linked Transactions:** rejected — conflates "I no longer want to track this Debt/Subscription" with "this money never moved." Restoring the parent would also have to restore its peer Transactions, which complicates restore semantics for what is, at root, just a tagging relationship.
- **Block soft-delete of a Debt/Subscription that has linked Transactions:** rejected — forces a multi-step flow on the user for a quiet undo operation, defeating the point of soft-delete.
