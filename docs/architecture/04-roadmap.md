# DentalCore PMS — Development Roadmap

Each phase ships compiling, tested, migration-backed code.

## Phase 1 — Foundation, Auth, Users, Roles
- Repo scaffolding: backend (Spring Boot 3 / Java 21 / Maven), frontend (Vite + React + TS + Tailwind), Docker Compose (postgres + backend + frontend), Flyway V1/V2.
- `shared` (BaseEntity, ProblemDetail errors, pagination), `infrastructure` (SecurityConfig, JWT filter, CORS, OpenAPI), `audit` core (event listener + append-only log).
- Auth: login, refresh rotation, logout, password reset request/confirm, lockout.
- Users: CRUD (ADMIN), role assignment, seeded admin.
- Frontend: auth pages, app shell/layout, protected routes, role-gated nav, users admin page.
- Tests: ArchUnit, security integration (Testcontainers), auth E2E.

## Phase 2 — Patients & Providers
- Flyway V3. Patient CRUD + demographics, phones, medical alerts, family links, status lifecycle, soft delete, trigram search, patient timeline endpoint (aggregating events).
- Provider CRUD (dentist/hygienist/assistant), NPI/license, color.
- Frontend: patient search, patient detail (tabs: demographics, alerts, family, timeline), provider management.

## Phase 3 — Scheduling
- Flyway V4. Operatories, appointments, status transitions state machine, conflict detection service + DB exclusion constraints, reschedule/cancel flows.
- Frontend: week/day calendar (provider & operatory lanes), appointment create/edit dialogs, color coding, status actions.

## Phase 4 — Treatment Plans & Procedures
- Flyway V5. Procedure catalog (CDT-ready, seeded), treatment plans, planned procedures with priority/status/estimated cost, approval + completion tracking, clinical notes (sign-once).
- Frontend: catalog admin, treatment plan builder, patient clinical tab.

## Phase 5 — Insurance
- Flyway V6. Carriers, plans, patient insurance (subscriber relationships, priority ordering), claims + claim procedures with status tracking.
- Frontend: carrier/plan admin, patient insurance tab, claims worklist.

## Phase 6 — Billing
- Flyway V7. Append-only ledger (charges/payments/adjustments), balance computation, charge posting on appointment completion (event-driven), reversing-entry corrections.
- Frontend: patient ledger view, payment/adjustment entry, account balance on patient header.

## Phase 7 — Documents
- Flyway V8. Storage port (local FS adapter now, S3 interface defined), upload/download with content-type validation and size limits, categorization, patient document tab.

## Phase 8 — Reporting
- Read-model queries: appointments by provider, daily production, patient growth, provider utilization. Extensible `ReportDefinition` registry.
- Frontend: reports page with date-range filters + tables/exports.

## Phase 9 — Hardening & Deployment
- Security pass (headers, rate limiting, dependency audit), prod compose + Nginx TLS, seed/demo data sets, logging/masking review, load smoke tests, full E2E suite, README/runbooks.
