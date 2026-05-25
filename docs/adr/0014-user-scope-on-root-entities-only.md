---
status: accepted
---

# `user_id` lives on root entities only; children inherit scope (and soft-delete) through their parent

Five root entities carry a direct `user_id` FK: Category, Contact, Transaction, Subscription, Debt. Child entities — Subscription Member, Payment Record, Debt Payment — do not. They are reachable only through their parent, so the parent's `userScope` filter already governs visibility. Likewise, child entities do not carry their own `deleted_at`: when a parent is soft-deleted, its children become invisible because the parent is filtered out. Restoring the parent restores the children automatically.

Domain POJOs (`com.keenti.finances.domain.model.*`) intentionally do **not** carry a `userId` field. Scoping is an infrastructure concern: repositories inject `UserContext` into Panache entities at write time; Hibernate filters handle reads. This keeps the domain layer framework-free per ADR-0001.

## Considered options

- **`user_id` on every table:** rejected — redundant on children that are only accessed via parents, and introduces a sync risk if `user_id` ever diverges between parent and child.
- **Independent `deleted_at` on children:** rejected — would create orphan-restore complexity (restore parent → which children?) with no user-facing benefit, since children are never accessed without their parent.
- **`userId` field on domain POJOs:** rejected — would leak an infrastructure concern into the domain layer.
