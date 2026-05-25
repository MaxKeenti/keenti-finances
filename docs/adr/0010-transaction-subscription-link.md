---
status: accepted
---

# Transaction↔Subscription link is a nullable FK with retroactive multi-select

`transaction.subscription_id` is a nullable foreign key. Tagging a Transaction with the Subscription it relates to happens after the fact, via a multi-select with inline previews (amount, date, description, category badge) on the Subscription detail page. No synthetic Transaction or Debt is ever created when linking — the link is a pure annotation.

The user is migrating historical data from other services, so retroactive tagging needs to be cheap and reversible. A join table or synthetic record approach was rejected as more machinery than this needs. Most Transactions stay unlinked; the FK is nullable for that reason.
