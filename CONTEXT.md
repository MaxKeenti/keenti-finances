# Keenti Finances

A multi-user personal finance tracker. Users record money flowing in and out, group it by Category and Contact, manage shared Subscriptions with cost-splitting, and track Debts with partial payments. All amounts are in MXN.

## Language

### Money movement

**Transaction**:
A single movement of money, in or out. Carries a Direction, an MXN amount, a date, a Category, and optionally a Contact and a Subscription link.
_Avoid_: Entry, record, line item.

**Direction**:
The orientation of a Transaction. `INGRESS` is money coming in; `EGRESS` is money going out. A Category also has a Direction (`INGRESS`, `EGRESS`, or `BOTH`) that constrains which Transactions can use it.
_Avoid_: Type (overloaded — see Subscription Type), in/out, debit/credit, income/expense (use INGRESS/EGRESS in code; "income" and "expense" are fine in user-facing copy).

**Category**:
A user-defined grouping for Transactions. Has a Direction and an OKLCH hue used to render its badge. Unique by name per User; soft-deleted Categories release their name slot.
_Avoid_: Tag, group, bucket.

**Contact**:
The counterparty on the other side of a Transaction — a person or entity from whom money is received or to whom money is sent.
_Avoid_: Party, counterparty (in user-facing copy), payee, payer.

**Net Balance**:
The headline dashboard figure: the all-time sum of INGRESS Transactions minus EGRESS Transactions. Internal Box allocations never change it.
_Avoid_: Balance (ambiguous — also used colloquially for "money in account"), cash flow (related but distinct; cash flow is the time series, net balance is the scalar).

### Boxes

**Box**:
A User-defined envelope that reserves part of the Net Balance for a purpose without moving money outside the app. A Box has a non-negative balance, personalized presentation, dated history, and at most one active Box Plan. A Box must have a zero balance and no active Box Plan before it can be archived; the User explicitly completes, abandons, or ends the plan first.
_Avoid_: Account (a Box does not represent a bank account), Category (describes a Transaction's purpose), wallet, pot, section.

**In Boxes**:
The sum of the current balances of all active Boxes. Displayed alongside Net Balance and Available to Spend; clicking it opens the Boxes overview.
_Avoid_: Savings (a Spending Budget also holds money), locked balance (the User may withdraw it).

**Available to Spend**:
The unallocated part of the Net Balance: `Net Balance - In Boxes`. It may be negative after real spending; that is an unreconciled state, not a reason to reject the Transaction. Depositing more money into Boxes is blocked until the shortfall is reconciled.
_Avoid_: Net Balance (includes money in Boxes), main balance, free balance.

**Box Movement**:
A dated internal allocation that deposits into, withdraws from, or transfers between Boxes. It is not a Transaction and never changes Net Balance. A Box Movement may be linked to an INGRESS Transaction for traceability.
_Avoid_: Transaction, transfer Transaction, saving Transaction.

**Box Funding**:
The amount of an EGRESS Transaction paid from a Box. One Transaction may have Box Funding from several Boxes; any remainder is paid from Available to Spend. Box Funding is part of the Transaction correction lifecycle and is reversed when that Transaction is deleted.
_Avoid_: Category (what the spending was for), Box Movement (an independent internal allocation).

**Box Plan**:
Optional guidance attached to a Box. A Box has at most one active plan, either a Saving Goal or a Spending Budget. Finished plans remain in history and do not archive the Box itself.
_Avoid_: Box Type (the Box remains the same when its plan changes), automation (plans never move money).

**Saving Goal**:
A Box Plan for growing the Box to a target balance by a target date. It has a daily, weekly, or monthly cadence, a cadence anchor, a regular saving commitment, and automatically evaluated Mini Goals. Reaching the target makes it ready for User-confirmed completion.
_Avoid_: Spending Budget, target Transaction.

**Mini Goal**:
One period of a Saving Goal. Achievement is based on the Box's net balance growth during that period. A shortfall is carried into the next period as arrears; clearing arrears returns later periods to the regular commitment.
_Avoid_: Deposit (withdrawals and spending also affect the result), Payment Record (belongs to Subscriptions).

**Spending Budget**:
A recurring Box Plan that recommends keeping a desired amount in the Box each period. Money rolls forward and the suggested top-up is `max(period amount - current Box balance, 0)`. It reports funded, spent, and remaining amounts rather than achieved/missed status.
_Avoid_: Saving Goal, Category budget (only explicitly funded Transactions consume it).

**Funding Trigger**:
A User-defined suggestion rule that reacts to an INGRESS Transaction's Category and proposes deposits into selected Boxes. Suggestions may come from active Box Plans or a configured fixed amount or percentage. The User must confirm every application; a Funding Trigger never moves money automatically.
_Avoid_: Automation, scheduled transfer, recurring Transaction.

### Subscriptions

**Subscription**:
A recurring payment obligation tracked by the app. Has a Subscription Type, a Subscription Owner, an Owner Participation setting, optional Subscription Members, and a billing schedule that generates Payment Records.
_Avoid_: Plan, recurrence, standing order.

**Subscription Type**:
`PERSONAL` (single-User, no member splitting) or `SHARED` (multiple Members, cost is split). Distinct from Owner Participation, which is orthogonal.
_Avoid_: Subscription Mode.

**Subscription Owner**:
The User who owns a Subscription. A role a User plays on a specific Subscription, not a separate concept from User.
_Avoid_: "Owner" alone (ambiguous), creator.

**Subscription Member**:
A person assigned to a Shared Subscription who owes part of the split. Identified by name, not by User account — Members generally are not Users of the system.
_Avoid_: "Member" alone (ambiguous — could mean User), participant, subscriber.

**Owner Participation** (a.k.a. **Middleman Mode** when off):
A boolean on a Shared Subscription. When true, the Owner counts as one of the cost-splitters: total cost / (memberCount + 1). When false ("middleman mode"), the Owner is just forwarding charges and the cost splits among Members only.
_Avoid_: Owner share, owner-includes-self.

**Payment Record**:
One per-period entry on a Subscription's billing schedule, recording whether each Subscription Member has paid for that period. Generated on demand from the Subscription detail page via the **Generate billing** button (one record per Member per period; see ADR-0019). Idempotent per period — re-triggering an already-generated period never duplicates.
_Avoid_: Invoice, bill, charge, period, billing entry.

**Public Subscription View**:
An unauthenticated, token-protected page where a Subscription's Members can see the status of their Payment Records without logging in. Each Shared Subscription has a UUID token.
_Avoid_: Share link, member portal.

### Debts

**Debt**:
A standalone amount of money owed, tracked outside Subscriptions. Has a status (`OPEN` / `PAID`) and supports partial payments via Debt Payments. Auto-transitions to `PAID` when fully settled.
_Avoid_: Loan, IOU, invoice.

**Debt Payment**:
A single partial payment toward a Debt. Recording a Debt Payment automatically creates a corresponding INGRESS Transaction so the dashboard reflects the income without manual double-entry.
_Avoid_: Repayment, instalment.

### Identity

**User**:
The owner of a slice of data in the app. Created just-in-time on the first request from an authenticated WorkOS identity. Holds personalization preferences (primary hue, heading font, body font). Each User's data is fully isolated — Categories, Contacts, Transactions, Subscriptions, and Debts are scoped by User.
_Avoid_: Account (the app has no separate "account" concept), tenant, customer.

## Flagged ambiguities

- **"Owner"** on its own is ambiguous. **User** is the identity; **Subscription Owner** is a role a User plays on a specific Subscription. Write the full term when there's any doubt.
- **"Member"** on its own is ambiguous. **User** is an authenticated identity; **Subscription Member** is a (usually non-User) person who owes a split on a Shared Subscription. They share no schema.
- **"Type"** is overloaded across the codebase (Direction, Subscription Type). When writing, name the field: "Subscription Type" or "Direction", never bare "type".
- **"Income" / "Expense"** are fine in user-facing copy but the canonical internal terms are **INGRESS** and **EGRESS**.

## Example dialogue

> **Dev:** When a Debt Payment comes in, do we generate an invoice?
>
> **Domain:** No — there's no Invoice in this app. The Debt itself is the thing that's owed. A Debt Payment just records a partial settlement against it and creates an INGRESS Transaction. The dashboard picks up the Transaction; the Debt's status flips to PAID once the running total catches up.
>
> **Dev:** And for Subscriptions — the monthly billing thing — that's also not an invoice?
>
> **Domain:** Right. Those are Payment Records. One per Subscription Member per billing period. You create them on demand with the **Generate billing** button on the Subscription's page; they then show up for Members on the Public Subscription View.
>
> **Dev:** So if a Member doesn't pay, the Payment Record stays unpaid forever?
>
> **Domain:** Yes. Unpaid Payment Records accumulate visibly on the Public Subscription View. They never auto-resolve. The Subscription Owner records the payment when it arrives.
>
> **Dev:** And the Subscription Owner — that's the User who created the Subscription?
>
> **Domain:** It's the User who owns it now, yes. But in middleman mode the Owner is not one of the splitters — `owner_participates` is false, so the cost divides among Subscription Members only. When it's true, the Owner counts as a splitter alongside the Members.
