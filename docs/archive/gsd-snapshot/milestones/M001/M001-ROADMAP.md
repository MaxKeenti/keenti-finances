# M001: Personal Finance Tracker

**Vision:** A single-user personal finance app tracking ingresses/egresses in MXN, with a dashboard showing net balance and trends, subscription management with auto-generated billing records, debt tracking with partial payments, and a public read-only view for subscription members. Deployed on Railway.

## Success Criteria

- User can log in on mobile Safari and see a dashboard with real income vs. expenses charts and net balance
- User can add, edit, and delete transactions categorized by user-defined categories
- User can create personal and shared subscriptions with auto-generated upcoming payment records
- User can share a token link with subscription members showing their payment status
- User can record embroidery jobs as debts, accept partial payments that auto-register as income
- Full app accessible on desktop and mobile Safari, deployed on Railway with HTTPS

## Slices

- [x] **S01: S01** `risk:high` `depends:[]`
  > After this: Log in with password on mobile Safari, see authenticated empty shell; unauthenticated requests redirect to login

- [x] **S02: S02** `risk:medium` `depends:[]`
  > After this: Create, edit, and delete categories and contacts through the UI; data persists across sessions

- [x] **S03: S03** `risk:medium` `depends:[]`
  > After this: Add ingresses and egresses with categories, see them listed and persisted; edit and delete work

- [x] **S04: S04** `risk:medium` `depends:[]`
  > After this: View monthly bar chart and yearly trend line with real transaction data; net balance reflects actual income minus expenses

- [x] **S05: S05** `risk:high` `depends:[]`
  > After this: Create personal and shared subscriptions, assign members, see scheduler-generated upcoming payment records, record payments per member

- [x] **S06: S06** `risk:medium` `depends:[]`
  > After this: Create embroidery job debts per debtor, record partial payments, see auto-created ingress transactions in transaction list and dashboard

- [x] **S07: S07** `risk:low` `depends:[]`
  > After this: Open a UUID token link without login, see all members' payment status for that subscription; invalid tokens show 404

- [x] **S08: S08** `risk:medium` `depends:[]`
  > After this: Full app running on Railway with HTTPS, PostgreSQL provisioned, all features accessible from the internet on mobile Safari

## Boundary Map

### S01 → S02\n\nProduces:\n- Hexagonal package structure (domain/application/infrastructure layers)\n- Auth middleware and session management on SvelteKit\n- SvelteKit ↔ Quarkus proxy pattern established\n- Flyway migration infrastructure\n- Base REST adapter pattern for hexagonal ports\n\nConsumes:\n- nothing (first slice)\n\n### S02 → S03\n\nProduces:\n- Category domain model and CRUD through hexagonal ports\n- Contact domain model and CRUD through hexagonal ports\n- Established CRUD pattern (domain → application service → REST adapter → SvelteKit proxy → UI form)\n\nConsumes:\n- Auth middleware, proxy pattern, hexagonal structure from S01\n\n### S02 → S05\n\nProduces:\n- Contact domain model and repository port (for member assignment)\n- Category domain model (for subscription categorization if needed)\n\nConsumes:\n- Auth middleware, proxy pattern from S01\n\n### S02 → S06\n\nProduces:\n- Contact domain model and repository port (for debtor assignment)\n- Category domain model (for debt categorization)\n\nConsumes:\n- Auth middleware, proxy pattern from S01\n\n### S03 → S04\n\nProduces:\n- Transaction domain model with amount, date, description, category, direction\n- Transaction repository port and query capabilities\n- Transaction aggregation service (monthly/yearly income vs. expenses)\n\nConsumes:\n- Category model from S02, auth and proxy from S01\n\n### S03 → S06\n\nProduces:\n- Transaction creation service (used by debt payment auto-ingress)\n- Ingress transaction model and persistence\n\nConsumes:\n- Category model from S02, auth and proxy from S01\n\n### S05 → S07\n\nProduces:\n- Subscription domain model with members, payment records, billing periods\n- UUID token per shared subscription\n- Payment status data per member per period\n\nConsumes:\n- Contact model from S02, auth and proxy from S01\n\n### S01–S07 → S08\n\nProduces:\n- Deployed, verified production instance\n\nConsumes:\n- All application code, migrations, and configuration from S01–S07
