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
The headline dashboard figure: the sum of INGRESS Transactions minus EGRESS Transactions over a period.
_Avoid_: Balance (ambiguous — also used colloquially for "money in account"), cash flow (related but distinct; cash flow is the time series, net balance is the scalar).

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
One per-period entry on a Subscription's billing schedule, recording whether each Subscription Member has paid for that period. Generated daily, 7 days before the billing date, by the billing scheduler. Idempotent — re-running never duplicates.
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
> **Domain:** Right. Those are Payment Records. One per Subscription Member per billing period. The scheduler creates them 7 days before the period's billing date so Members see the upcoming due on the Public Subscription View.
>
> **Dev:** So if a Member doesn't pay, the Payment Record stays unpaid forever?
>
> **Domain:** Yes. Unpaid Payment Records accumulate visibly on the Public Subscription View. They never auto-resolve. The Subscription Owner records the payment when it arrives.
>
> **Dev:** And the Subscription Owner — that's the User who created the Subscription?
>
> **Domain:** It's the User who owns it now, yes. But in middleman mode the Owner is not one of the splitters — `owner_participates` is false, so the cost divides among Subscription Members only. When it's true, the Owner counts as a splitter alongside the Members.
