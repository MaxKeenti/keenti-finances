---
status: accepted
---

# Financial Accounts are a ledger; Transfers are neutral

Keenti needs to show where a User's money is held and how credit-card debt is settled without treating an internal transfer as income or an expense. A Box remains an internal allocation over Net Balance; it is not a Financial Account.

## Decision

A Financial Account is a User-scoped root entity. Its Account Kind is `CASH`, `DEBIT`, `CHECKING`, `SAVINGS`, or `CREDIT`. A Credit Financial Account is liability-oriented; all other kinds are asset-oriented. Account balances are signed:

```text
Account balance = opening balance
                + INGRESS Transactions
                - EGRESS Transactions
                + Transfers in
                - Transfers out

Net Balance = sum of signed active Financial Account balances
In Boxes = sum of active Box balances
Available to Spend = Net Balance - In Boxes
```

Negative Credit Financial Account balances are displayed as money owed. A positive Credit Financial Account balance is an overpayment/credit. Credit limits and negative asset balances are warnings, not blockers: Keenti records activity that actually happened and flags the discrepancy for reconciliation.

A Transfer is its own User-scoped aggregate with one source Financial Account, one destination Financial Account, a positive amount, an effective date, and optional notes. The source and destination must differ and resolve in the current User scope. Create, edit, delete, restore, and payment allocation updates are atomic. Transfers have no Direction, Category, Contact, Box Funding, or Box Distribution; they are excluded from income and expense reporting.

Financial Account tracking is opt-in for existing Users. Activation records a tracking date and Financial Account opening balances whose signed sum must exactly equal the User's pre-activation Net Balance. Existing Transactions remain historical, unassigned records for reports. New Transactions, including Debt Payment-generated INGRESS Transactions, require an active Financial Account. New Users set up at least one Financial Account before recording activity. The feature cannot be disabled after activation.

An Account Opening Balance is ledger seed data, not an INGRESS or EGRESS Transaction. New Financial Accounts created after activation start at zero unless the User deliberately introduces a previously untracked balance; such a change is visible as an Account Opening Balance and affects Net Balance.

Credit Financial Accounts also own Credit Statements and MSI Plans:

- A Credit Statement is continuously estimated from registered Credit Financial Account activity, then optionally confirmed with official closing date, due date, statement balance, minimum payment, payment required to avoid interest, and a note.
- Confirmed official figures are immutable snapshots until deliberately reconfirmed. Subsequent changes to prior activity flag a reconciliation mismatch rather than silently rewriting bank-issued history.
- A Transfer into a Credit Financial Account is a card payment and is allocated oldest-unpaid-statement first. Multiple partial payments are supported. A refund is an INGRESS Transaction on the Credit Financial Account, not a payment.
- An MSI Plan is limited to no-interest installments. Its full EGRESS Transaction affects Net Balance, outstanding debt, and available credit when recorded; only each scheduled installment is included in the relevant statement estimate.

Credit activity is tracked at Financial Account level. Several physical or virtual cards may share one Financial Account and do not need separate attribution. The first version is fully manual: no bank synchronization, statement-file import, pending Transfers, fees, or external reminders.

Financial Accounts are archived, not deleted. An Account can be archived only at a zero balance; Credit Financial Accounts must also have no unpaid Credit Statements or active MSI Plans. Historical activity remains readable, but changes require restoring the Account first.

## Consequences

- This supersedes ADR-0020's consequence that Transaction aggregates are the sole input to Net Balance. ADR-0020 remains authoritative that Boxes are internal allocations, not Financial Accounts, and that Box activity never changes Net Balance.
- Dashboard and Box readers use the account ledger's Net Balance after activation. Monthly income and expense reports continue to aggregate only INGRESS and EGRESS Transactions.
- The Transaction API, domain model, persistence model, and UI gain a Financial Account reference. Legacy Transaction rows retain a null reference and must not be retrospectively assigned by default.
- Transfers need their own persistence, REST resource, use case, history row, trash lifecycle, and account-specific statement presentation; modeling them as paired Transactions would require synthetic Categories and corrupt income/expense reporting.
- Credit statement estimation and payment allocation must be recalculated transactionally when an affected Transaction, Transfer, statement, or MSI Plan changes.
- Opening Credit debt, current official statement obligations, and remaining MSI schedules may be imported during activation as explanatory metadata. They must not double-count the Account Opening Balance.
- All schema changes ship through new append-only Flyway migrations.

## Considered options

- **Use a `TRANSFER` Transaction Direction:** rejected because a Transfer has two Accounts, no Category, and must not participate in income/expense aggregates.
- **Treat a Financial Account as a Box:** rejected because Accounts represent real assets and liabilities while Boxes only allocate Net Balance internally.
- **Require existing Users to classify all historical Transactions:** rejected because it would turn activation into a lengthy and often inaccurate migration. A reconciled cutover establishes accurate balances going forward.
- **Create a balance-correction Transaction when activation totals differ:** rejected. Activation is blocked until the User finds the missing activity, cash, or incorrect balance.
- **Model physical cards separately from the credit line:** rejected for version one because the User needs one PLATA balance, limit, statement cycle, and payment status.
- **Support interest-bearing installments:** deferred because amortization, interest, fees, and taxes need a separate design.
