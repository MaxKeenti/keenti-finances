---
status: accepted
---

# Debt Payments auto-create INGRESS Transactions

When a Debt Payment is recorded, the application also creates a corresponding INGRESS Transaction via `TransactionUseCase`. The dashboard's Net Balance picks up the Transaction automatically; the Debt itself transitions to `PAID` when the running total of its Debt Payments meets the original amount.

The alternative — treating Debt repayment as a separate income source the dashboard knows about — was rejected to avoid manual double-entry and to keep one canonical money-movement type. Side effect: deleting a Debt Payment must also delete the auto-created Transaction; the link is mediated through the use case, not a hard FK.
