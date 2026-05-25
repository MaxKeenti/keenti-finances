---
status: accepted
---

# Partial unique index lets soft-deleted Categories release their name slot

The `category` table uses a partial unique index — `UNIQUE (user_id, name) WHERE deleted_at IS NULL` — instead of a full `UNIQUE (user_id, name)` constraint. Soft-deleting a Category releases its name; the User can immediately create a new Category with the same name without colliding with the deleted row in trash.

A full unique constraint would force users to rename soft-deleted Categories before recreating them, defeating the point of soft-delete-as-a-quiet-undo. Appending timestamps to deleted names was rejected for making the trash view ugly. Partial unique indexes are a native PostgreSQL feature; tests on H2 may behave differently and would need adapting if H2 tests were ever added.
