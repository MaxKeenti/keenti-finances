# Navigation

How a User moves between the app's areas, and what happens to a User's own navigation preferences when that model changes.

## Decision record

**D4 — navigation wording and model: approved 9 September 2026 by the product owner.** The approval covers the desktop and mobile models below. The receivables wording (“Money owed to you” / “Te deben”) was part of the same decision gate; ADR-0023 has since superseded it, because a Debt now runs in both directions and the section is named “Debts” / “Deudas” with each side labelled underneath.

## Desktop

A single bar lists every area: Dashboard, Transactions, Boxes, Financial Accounts, Subscriptions, Debts, Settings, and Logout.

Each entry shows its name at all times. A name that appears only on pointer hover is unavailable to touch and to anyone scanning the bar, and it forces the icon alone to be recognized; the persistent label is also the link's accessible name, so the screen-reader name and the visible name are the same string. Pointer magnification remains a presentation flourish and is still governed by the User's `dockMagnification` preference.

## Mobile

The bar carries **Dashboard** first, then the User's pinned areas, then **More**, which opens the full list. Dashboard is a stable entry: it is always present and never occupies a pin.

Defaults for a User who has pinned nothing are Dashboard, Transactions, Boxes, and More, so recording money and planning are both reachable without opening More.

## Navigation preference migration

This is an upgrade of stored user navigation settings. It is **not** a Flyway database migration and must not be confused with one — no schema changes, and nothing is written.

`mobilePinnedNavItems` is a stored comma-separated list of hrefs. It is interpreted on read (`frontend/src/lib/navigation.ts`):

- every stored href that names a real destination is kept, in the stored order;
- a stored Dashboard pin is de-duplicated rather than shown twice, and the stored value is left untouched, so the User's recorded choice is unchanged and still editable in Settings;
- unrecognized entries are ignored without discarding the recognized ones;
- the new defaults apply only when nothing usable was stored.

A User who explicitly pinned areas therefore keeps exactly those areas after the change; only a User who never chose gets the new defaults.

New users receive the stored three-slot value `/,/transactions,/boxes`, compatible with the existing preferences API. The frontend fallback and User entity default agree. V35 changes only the database column default for future rows; it never updates existing preferences. This Flyway default change is distinct from interpreting saved navigation preferences on read.
