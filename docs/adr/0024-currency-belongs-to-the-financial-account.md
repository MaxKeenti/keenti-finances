---
status: accepted
---

# Currency belongs to the Financial Account; totals are per currency

Keenti was MXN-only: every amount column was a bare `DECIMAL(12,2)` and every screen formatted it as pesos. The User is leaving for a months-long student exchange in Europe. They will hold euros in a local bank account and in cash, pay rent and groceries in euros, and split costs with flatmates. A foreign amount noted beside an MXN Transaction does not answer "how many euros do I have left", so the ledger itself has to know currencies.

## Decision

**Currency** is an ISO 4217 code from a small supported set (`MXN`, `EUR`, `USD`, `GBP`). Adding a code is a code change, not a migration.

**A Financial Account has one currency, fixed at creation.** Every Transaction on it, its Account Opening Balance, its credit limit, its Credit Statements, and its MSI Plans are in that currency. A Transaction stores its currency, copied from its Financial Account when it is created. Legacy Transactions without a Financial Account are `MXN`. A Transaction may later move only to another Financial Account in the same currency.

**Nothing in the ledger is ever converted.** Each Financial Account balance is exact in its own currency. Money moves between currencies only through a **Transfer**, which records what left the source and what arrived in the destination:

```text
Transfer.amount             in the source Account's currency
Transfer.destinationAmount  in the destination Account's currency
```

Between Accounts in the same currency both amounts must be equal. Between currencies the effective exchange rate is `destinationAmount / amount`: it is what the User actually paid, including fees and spread, and it is never looked up. A Transfer into a Credit Financial Account pays its Credit Statements with `destinationAmount`.

**Headline figures are per currency.** ADR-0022's formulas still hold, but only within one currency:

```text
Net Balance(c)        = sum of signed active Financial Account balances in c
In Boxes(c)           = sum of active Box balances in c
Available to Spend(c) = Net Balance(c) - In Boxes(c)
```

Before Financial Account tracking is activated, Net Balance(`MXN`) keeps the ADR-0020 definition and every other currency is zero. Tracking must be active before a User can hold another currency.

**Everything else that holds or owes money takes a currency too.**

- A **Box** has a currency, fixed at creation. Box Funding, Box Distributions and Box Movements linked to a Transaction require the Box and the Transaction to share a currency. A Box Transfer requires both Boxes to share one. A deposit checks Available to Spend in the Box's currency. Box Plans are in the Box's currency.
- A **Debt** has a currency. Its Debt Payments must draw from, or land in, a Financial Account in that currency. It is frozen once a Debt Payment exists, like the Debt Direction (ADR-0023). A Debt Counterpart is one Contact, one Direction, and one currency, and a bulk payment settles only one of them.
- A **Subscription** has a currency. Member shares and Payment Records are in it, and a linked Transaction must share it. It is frozen once a Payment Record exists.
- A **Funding Trigger** only suggests deposits into Boxes in the currency of the INGRESS Transaction that fired it.

**Primary Currency** is a User preference, `MXN` by default. It pre-selects the currency for new Financial Accounts, Boxes, Debts and Subscriptions, and it is the currency the dashboard, the Boxes overview and the planning preview open in. The User switches it when their day-to-day money changes, for example to `EUR` for the exchange. Switching it changes no stored amount.

**Summaries are read in one Viewing Currency at a time.** The dashboard, the Boxes overview and the planning preview take the currency they report in, defaulting to the Primary Currency, and list the other currencies the User holds so the screen can switch between them. The dashboard's attention list is the one exception. It has no totals and shows obligations from every currency, each labelled, so a statement due in the currency not being viewed is never hidden.

**A Converted Total is an estimate for display only.** The dashboard may show every currency's Net Balance converted into the Primary Currency at the latest daily reference rate (European Central Bank rates via Frankfurter, cached per day). It never enters a ledger, a Box, a plan, a validation, or a stored figure. When the rate source cannot be reached, the Converted Total is unavailable rather than zero or stale-without-saying-so.

## Consequences

- One migration adds `currency CHAR(3) NOT NULL DEFAULT 'MXN'` to `financial_account`, `transaction`, `box`, `debt` and `subscription`, adds `transfer.destination_amount` backfilled from `amount`, and adds `app_user.primary_currency`. Every existing row keeps its meaning as MXN.
- Repository reads that sum money (`getTotalBalance`, `getNetBalance`, the monthly summary, the Box total) take a currency. A sum without a currency is a bug.
- ADR-0022's activation rule becomes per currency. The MXN opening balances must equal the pre-activation Net Balance, because every legacy Transaction is MXN. Opening balances in other currencies introduce money Keenti never recorded, so they are unconstrained.
- The frontend formats money with the amount's own currency and never assumes MXN. Every money-bearing payload carries its currency.
- The backend gains its first outbound HTTP dependency (the rate source), behind an out port so tests and outages can replace it.

## Considered options

- **Keep MXN as the only ledger currency and note a foreign amount on the Transaction:** rejected. It is fine for a card charge the bank converts, but it cannot say how many euros are left in a euro account, which is the question the User will ask every day of the exchange.
- **Convert every amount to MXN on write and keep one Net Balance:** rejected. A euro balance would drift with every rate change even though no money moved, and the rate at write time is a guess, not what the bank applied.
- **One global Net Balance converted at today's rate:** rejected as a ledger figure. It changes when nothing happened, and it cannot drive Available to Spend, which Box deposits validate against. It survives only as the display-only Converted Total.
- **Let a Transfer store an exchange rate instead of a destination amount:** rejected. The User reads the arrived amount off their statement. A stored rate would have to be rounded and could not reproduce the exact cents that arrived.
- **Allow Box Funding across currencies:** rejected. It would need a rate at funding time, and a Box's balance would stop meaning a fixed amount of money.
