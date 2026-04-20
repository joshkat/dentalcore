# DentalCore PMS — Architecture Overview

A production-grade, web-based Dental Practice Management System inspired by Open Dental.

## 1. High-Level Architecture

**Style: Modular Monolith** — a single deployable Spring Boot application internally partitioned
into business-domain modules with enforced boundaries.

### Why a Modular Monolith (and not microservices)

| Concern | Reasoning |
|---|---|
| Transactional integrity | Dental workflows (check-in → procedure → ledger → claim) are deeply transactional. A monolith gives ACID guarantees without sagas/outboxes. |
| Operational simplicity | Dental offices and small SaaS teams cannot run a service mesh. One JVM + one Postgres + one reverse proxy is deployable anywhere. |
| Evolvability | Module boundaries are enforced at compile time (package structure + ArchUnit tests). If a module ever needs extraction (e.g., `reporting`), the seam already exists. |
| HIPAA surface area | Fewer network hops = fewer places PHI travels = smaller audit/encryption surface. |

### System diagram

```
┌──────────────────────────────────────────────────────────────┐
│  Browser (React + TS SPA, served by Nginx)                   │
└────────────────────────┬─────────────────────────────────────┘
                         │ HTTPS (JWT Bearer)
┌────────────────────────▼─────────────────────────────────────┐
│  Spring Boot 3 (Java 21) — Modular Monolith                  │
│                                                              │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌──────────────┐       │
│  │  auth   │ │  users  │ │ patients │ │  providers   │       │
│  └─────────┘ └─────────┘ └──────────┘ └──────────────┘       │
│  ┌──────────────┐ ┌────────────────┐ ┌────────────┐          │
│  │ appointments │ │ treatmentplans │ │ procedures │          │
│  └──────────────┘ └────────────────┘ └────────────┘          │
│  ┌───────────┐ ┌─────────┐ ┌───────────┐ ┌───────────┐       │
│  │ insurance │ │ billing │ │ documents │ │ reporting │       │
│  └───────────┘ └─────────┘ └───────────┘ └───────────┘       │
│  ┌───────┐ ┌────────┐ ┌────────────────┐                     │
│  │ audit │ │ shared │ │ infrastructure │                     │
│  └───────┘ └────────┘ └────────────────┘                     │
└────────────────────────┬─────────────────────────────────────┘
                         │ JDBC (HikariCP)
┌────────────────────────▼─────────────────────────────────────┐
│  PostgreSQL 16 (Flyway-managed schema)                       │
└──────────────────────────────────────────────────────────────┘
   File storage: pluggable (local volume now → S3-compatible later)
```

## 2. Domain-Driven Design Breakdown

### Bounded Contexts

| Context | Modules | Core Aggregates | Description |
|---|---|---|---|
| **Identity & Access** | `auth`, `users` | User, Role, RefreshToken, PasswordResetToken | Authentication, RBAC, credential lifecycle |
| **Patient Care** | `patients`, `providers` | Patient, MedicalAlert, FamilyLink, Provider | Demographics, medical alerts, family relationships, clinical staff |
| **Scheduling** | `appointments` | Appointment, Operatory, Schedule | Calendar, conflict detection, statuses |
| **Clinical** | `treatmentplans`, `procedures` | TreatmentPlan, PlannedProcedure, ProcedureCode, ClinicalNote | Treatment planning, procedure catalog (CDT-ready), clinical notes |
| **Revenue Cycle** | `insurance`, `billing` | InsuranceCarrier, InsurancePlan, PatientInsurance, Claim, LedgerEntry | Coverage, claims, charges/payments/adjustments |
| **Records** | `documents` | Document | File storage with metadata, categorization |
| **Compliance** | `audit` | AuditLog | Immutable append-only action log |
| **Analytics** | `reporting` | (read models) | Cross-context read-only queries |
| **Platform** | `shared`, `infrastructure` | — | Base entities, events, error handling, config, storage abstraction |

### Module communication rules

1. **API package only**: each module exposes `api/` (public service interfaces + DTOs + domain events). Everything else (`internal/`) is module-private.
2. **No cross-module entity references**: modules reference other aggregates by **UUID**, never by JPA relation across module boundaries. (e.g., `Appointment.patientId: UUID`, not `Appointment.patient: Patient`.)
3. **Synchronous calls** via public interfaces (e.g., `appointments` calls `PatientLookupApi.exists(patientId)`).
4. **Asynchronous decoupling** via Spring `ApplicationEventPublisher` domain events (e.g., `AppointmentCompletedEvent` → `billing` posts charges, `audit` logs it). Events keep `audit` and `reporting` from coupling to every module.
5. **Enforced by ArchUnit tests** in CI — a build fails if `billing.internal` imports `patients.internal`.

### Module dependency diagram

```
                        ┌────────┐
        all modules ───▶│ shared │◀─── infrastructure
                        └────────┘
  ┌──────┐    ┌───────┐
  │ auth │───▶│ users │
  └──────┘    └───┬───┘
                  │ (UserApi)
  ┌──────────┐ ┌──┴────────┐
  │ patients │ │ providers │
  └────┬─────┘ └─────┬─────┘
       │ PatientApi  │ ProviderApi
       ▼             ▼
  ┌─────────────────────┐     ┌────────────┐
  │    appointments     │     │ procedures │
  └─────────┬───────────┘     └─────┬──────┘
            │ events                │ ProcedureCatalogApi
            ▼                       ▼
  ┌────────────────┐      ┌────────────────┐
  │ treatmentplans │─────▶│   insurance    │
  └───────┬────────┘      └───────┬────────┘
          │ events                │ events
          ▼                       ▼
  ┌─────────────────────────────────────┐
  │              billing                │
  └─────────────────────────────────────┘
  ┌───────────┐  ┌───────────┐  ┌───────┐
  │ documents │  │ reporting │  │ audit │ ◀── domain events (all modules)
  └───────────┘  └───────────┘  └───────┘
```

Arrows = allowed compile-time dependencies (always on `api/` packages only).
`audit` and `reporting` depend on nothing; they consume events / read models.

## 3. Security Architecture

### Authentication
- **JWT access tokens** (short-lived, 15 min, HS512/RS256-ready) + **rotating refresh tokens**
  (opaque, hashed at rest, 7-day TTL, single-use rotation with reuse detection → family revocation).
- **Password hashing**: BCrypt (strength 12), upgradeable via `DelegatingPasswordEncoder`.
- **Password reset**: time-boxed single-use token (hashed at rest), email delivery abstracted
  behind `NotificationPort` (logged locally now, SMTP/SES later).
- Login throttling: per-account failed-attempt lockout with exponential backoff.

### Authorization (RBAC)
Roles: `ADMIN`, `DENTIST`, `HYGIENIST`, `FRONT_DESK`, `BILLING`, `READ_ONLY`.

- Enforced at **three layers**:
  1. URL-level rules in `SecurityFilterChain`
  2. Method-level `@PreAuthorize` on every service/controller mutation
  3. Frontend route guards + role-based UI visibility (defense-in-depth UX only — never trusted)
- Permission matrix is data-driven (role → authority set) so granular permissions can be added
  without schema change.

### HIPAA-oriented practices (now)
- All significant actions audit-logged (who/when/what/before/after) — append-only table.
- No entity ever serialized to API consumers — DTOs only, MapStruct-mapped.
- Input validation at boundary (Bean Validation + Zod on frontend).
- PHI never in logs (logback masking converter), never in JWT claims, never in URLs.
- Soft deletes for clinical data — nothing clinical is ever physically destroyed.
- TLS termination at reverse proxy; secure/strict cookies for refresh token (httpOnly, SameSite=Strict).

### Designed-for-later (seams in place)
- **MFA**: `AuthenticationStep` chain in `auth` — TOTP step slots in without rework.
- **SSO/OIDC**: Spring Security's `oauth2Login` can coexist; `users` keys identity by email + external `idp_subject` column reserved.
- **Field-level encryption**: JPA `AttributeConverter` seam on designated PHI columns.
- **Session timeout / BAA logging / access reports**: audit schema already captures the data.

## 4. Deployment Architecture

```
                    ┌─────────────────────────────┐
   Internet ──────▶ │ Nginx (TLS, static SPA,     │
                    │ /api reverse proxy)         │
                    └────────────┬────────────────┘
                                 │
                    ┌────────────▼────────────────┐
                    │ backend (Spring Boot, JRE21)│──▶ /data/documents (volume)
                    └────────────┬────────────────┘
                                 │
                    ┌────────────▼────────────────┐
                    │ postgres:16 (volume)        │
                    └─────────────────────────────┘
```

- **Dev**: `docker compose up` → postgres + backend (hot reload via devtools) + Vite dev server.
- **Prod**: `docker-compose.prod.yml` → Nginx serving built SPA + proxying `/api`, backend with
  prod profile, managed secrets via env file, health checks (`/actuator/health`) gating restarts.
- **Multi-clinic ready**: every clinical table carries `clinic_id` from day one; a `clinics` table
  exists in Phase 1 with a default clinic seeded. Row-level scoping is a service-layer filter now,
  upgradeable to Postgres RLS later.

## 5. API Conventions

- Base path `/api/v1` (URL versioning).
- Pagination: `?page=0&size=25&sort=lastName,asc` (Spring Pageable, capped size).
- Consistent envelope: success returns resource/page directly; errors return RFC-7807
  `application/problem+json` via global `@RestControllerAdvice`.
- OpenAPI 3 via springdoc — `/swagger-ui.html` (disabled in prod profile).

## 6. Testing Strategy

| Layer | Tools | Policy |
|---|---|---|
| Backend unit | JUnit 5 + Mockito | Services, mappers, validators |
| Backend integration | Testcontainers (Postgres) | Repositories, Flyway, security filter chain, full REST slices |
| Architecture | ArchUnit | Module boundary enforcement |
| Frontend component | Vitest + React Testing Library | Components, hooks, forms |
| Frontend E2E | Playwright | Auth flow, patient CRUD, scheduling golden paths |
