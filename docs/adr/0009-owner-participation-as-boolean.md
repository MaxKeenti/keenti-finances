---
status: accepted
---

# Owner Participation modelled as a boolean (`owner_participates`)

A Shared Subscription has a single boolean column `owner_participates`. When true, the cost splits as `total / (memberCount + 1)` and the Subscription Owner gets a Payment Record alongside each Subscription Member. When false ("middleman mode"), the split is `total / memberCount` and the Owner gets no Payment Record — the Owner is just forwarding charges.

The same User is sometimes a participant and sometimes a middleman, and the choice is per-Subscription, so a flag on the Subscription is the smallest model that captures both. A role/enum or a separate participant table was rejected as overkill for what is effectively a one-bit choice.
