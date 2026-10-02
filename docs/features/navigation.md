# Navigation

How a User moves between the app's areas, and what happens to a User's own navigation preferences when that model changes.

## Decision record

**D4 — navigation wording and model: approved 9 September 2026 by the product owner.** The approval covers the desktop and mobile models below. The receivables wording (“Money owed to you” / “Te deben”) was part of the same decision gate; ADR-0023 has since superseded it, because a Debt now runs in both directions and the section is named “Debts” / “Deudas” with each side labelled underneath.

D4 also required every desktop dock entry to show its name at all times. The product owner withdrew that requirement on 1 October 2026: the desktop dock now shows names on hover, as described below. The rest of D4 stands.

## Desktop

A single dock lists the main areas: Dashboard, Transactions, Boxes, Financial Accounts, Subscriptions, Debts, Planning, and Settings, followed by **More**. More opens the full list, which adds Categories, Contacts, Trash, and Logout. Settings is highlighted while one of those three management areas is open.

Planning opens the [30-day planning preview](planning-preview.md). The desktop dock was browser-checked at 640 px with no document-level horizontal overflow, including while magnified.

Each tile is an icon. Its name appears above it on pointer hover and on keyboard focus, and is the tile's accessible name at all times. On a touch screen wide enough to get the desktop dock, the name is not shown.

The tile for the current area is filled with the User's primary hue. A dot under a tile marks an area opened this session; Dashboard always carries one. The dots are kept in memory only and reset on reload.

Pointer magnification remains a presentation flourish and is still governed by the User's `dockMagnification` preference. The dock keeps a fixed height, so magnified tiles rise above it.

## Mobile

The bar carries **Dashboard** first, then the User's pinned areas, then **More**, which opens the full list, including Planning. Dashboard is a stable entry: it is always present and never occupies a pin.

Defaults for a User who has pinned nothing are Dashboard, Transactions, Boxes, and More, so recording money and planning are both reachable without opening More.

Planning can be chosen as a pin in Settings; the preferences API accepts `/planning` alongside the other pinnable areas. Adding the choice changes neither the defaults nor any stored pins.

## Navigation preference migration

This is an upgrade of stored user navigation settings. It is **not** a Flyway database migration and must not be confused with one — no schema changes, and nothing is written.

`mobilePinnedNavItems` is a stored comma-separated list of hrefs. It is interpreted on read (`frontend/src/lib/navigation.ts`):

- every stored href that names a real destination is kept, in the stored order;
- a stored Dashboard pin is de-duplicated rather than shown twice, and the stored value is left untouched, so the User's recorded choice is unchanged and still editable in Settings;
- unrecognized entries are ignored without discarding the recognized ones;
- the new defaults apply only when nothing usable was stored.

A User who explicitly pinned areas therefore keeps exactly those areas after the change; only a User who never chose gets the new defaults.

New users receive the stored three-slot value `/,/transactions,/boxes`, compatible with the existing preferences API. The frontend fallback and User entity default agree. V35 changes only the database column default for future rows; it never updates existing preferences. This Flyway default change is distinct from interpreting saved navigation preferences on read.
