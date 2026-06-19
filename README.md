# DentalCore PMS

A production-grade, web-based **Dental Practice Management System**, built as a modular
monolith. It covers the full front-desk and clinical workflow — scheduling, charting,
treatment planning, insurance, billing, documents, reporting — with HIPAA-oriented
security, an append-only audit trail, granular permissions, and English/Spanish
internationalization.

---

## Table of contents

- [Stack](#stack)
- [Feature overview](#feature-overview)
- [Architecture](#architecture)
- [Quick start (Docker Compose)](#quick-start-docker-compose)
- [Configuration reference](#configuration-reference)
- [Deployment guides](#deployment-guides)
  - [1. Single VM / VPS (Docker Compose)](#1-single-vm--vps-docker-compose)
  - [2. Bare-metal / systemd (no Docker)](#2-bare-metal--systemd-no-docker)
  - [3. Managed Postgres + container host](#3-managed-postgres--container-host)
  - [4. Kubernetes](#4-kubernetes)
  - [5. PaaS (Render / Railway / Fly.io / App Service)](#5-paas-render--railway--flyio--azure-app-service)
- [TLS / reverse proxy](#tls--reverse-proxy)
- [Backups & data](#backups--data)
- [Local development](#local-development)
- [Tests](#tests)
- [Repository layout](#repository-layout)
- [Demo data](#demo-data)

---

## Stack

| Layer     | Technology |
|-----------|------------|
| Frontend  | React 18, TypeScript, Vite, React Router, TanStack Query, Tailwind CSS, React Hook Form, Zod, react-i18next |
| Backend   | Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA / Hibernate |
| Database  | PostgreSQL 16, Flyway migrations (V1 → V22) |
| Testing   | JUnit 5, Mockito, Testcontainers, ArchUnit, Vitest + React Testing Library, Playwright |
| Infra     | Docker, Docker Compose, Nginx (prod static + API proxy) |

---

## Feature overview

### Patients & providers
- Patient CRUD with demographics, phones, medical alerts, family links, status lifecycle,
  and soft delete.
- Trigram fuzzy search and a patient timeline that aggregates events across modules.
- **Patient merge** with duplicate detection (combines records, ledgers, and history safely).
- Provider management (dentist / hygienist / assistant), NPI / license, per-provider color.

### Scheduling
- Week / day calendar with operatory lanes, color coding, and status actions.
- Appointment create / edit / cancel / reschedule with a status state machine.
- **Double-booking prevention** via a conflict-detection service backed by PostgreSQL
  GiST exclusion constraints.
- **Scheduling power tools (Phase E):**
  - **Operatory blockouts** — block off a room for a time range with a reason.
  - **Recurring appointment series** — weekly / biweekly / monthly, auto-skipping
    occurrences that conflict or fall on blocked time.
  - **Appointment confirmations** — send / resend confirmation, timestamp tracked.
  - **Drag-to-reschedule** directly on the calendar grid (duration preserved, backend
    re-validates).
- Provider availability templates, time-off, and ASAP / unscheduled worklists.

### Clinical
- CDT-ready **procedure catalog** (seeded), fee schedules, and coverage tables.
- **Treatment plans** with planned procedures, priority/status, estimated cost, approval
  and completion tracking.
- **Odontogram charting** and periodontal (perio) charting.
- **Clinical notes** with sign-once semantics and reusable note templates.

### Insurance & claims
- Carriers, plans, and patient insurance with subscriber relationships and priority order.
- **Claims** + claim procedures with status tracking and deductible tracking.
- **Coordination of benefits (COB)** for secondary insurance.

### Billing & RCM
- Append-only **ledger** (charges / payments / adjustments) with balance computation.
- Event-driven charge posting on appointment completion; reversing-entry corrections.
- **Family billing** — guarantor accounts, family-level balances.
- **Statement runs** — batch-generate patient statements.
- **Guided checkout** flow: complete procedures → take payment → complete appointment.
- Financial dates use the **clinic's timezone** (`clinics.timezone`), never the server clock.

### Forms & e-signature
- Form templates and patient form submissions with **electronic signature** capture.

### Documents
- Upload / download behind a `StoragePort` (local volume now, S3-compatible interface
  defined) with content-type whitelist, size caps, and path-traversal-proof keys.

### Reporting
- Report registry with date-range filters and CSV export. Built-in reports:
  **Day sheet**, **Daily production**, **Patient growth**, **Provider utilization**,
  **Statement runs**.

### Reminders & recall
- Recall/reminder scheduling via a `NotificationPort` (pluggable SMS/email adapter).

### Security & administration
- Short-lived JWT access tokens (15 min) + rotating refresh tokens in `httpOnly`,
  `SameSite=Strict` cookies, with **reuse detection** that revokes the whole token family.
- BCrypt(12) hashing; **account lockout** after 5 failed attempts.
- **Granular permissions (Phase C):** 41 permission codes enforced via `hasAuthority`
  at the method level, on top of base roles (`ADMIN`, `DENTIST`, `HYGIENIST`,
  `FRONT_DESK`, `BILLING`, `READ_ONLY`). An admin **permission matrix** UI edits
  role → permission grants with **live cache invalidation** (no re-login required).
- Append-only **audit log** (who / what / when / before / after / IP) written in
  independent transactions.
- DTO-only API surface — JPA entities never cross the controller boundary.
- Password reset with single-use, time-boxed, hashed tokens; no account enumeration.
- Per-IP rate limiting on credential endpoints (`AUTH_RATE_LIMIT`, default 15/min → 429).

### Internationalization (Phase F)
- Full **English / Spanish** UI via react-i18next with per-feature namespace catalogs.
- **Per-user language preference** (set in Settings) for the UI and for PDF / data exports.
- **Instance default language** set by deployers via the `DEFAULT_LANGUAGE` env var,
  exposed through a public `/api/v1/config` endpoint.
- Backend PDF / export localization via Spring `MessageSource` with `?lang` resolution.

---

## Architecture

A **modular monolith**: each business capability is a module under
`com.dentalcore.<module>` split into a public `api` package and an `internal` package
(`web` / `service` / `repository` / `entity` / `dto`). Modules communicate only through
public APIs and reference each other by UUID — never by sharing entities. Boundaries are
enforced in CI by an ArchUnit `ModuleBoundaryTest`.

Modules: `auth`, `users`, `audit`, `patients`, `providers`, `appointments`,
`procedures`, `treatmentplans`, `clinicalnotes`, `insurance`, `billing`, `documents`,
`forms`, `reminders`, `reporting`, plus `shared` and `infrastructure`.

See `docs/architecture/` for the architecture overview, database design, folder
structure, and roadmap.

---

## Quick start (Docker Compose)

Requires Docker with Compose v2.

```bash
cp .env.example .env          # then edit secrets (see below)
docker compose up --build
```

- Frontend (Vite dev server): http://localhost:5173
- API: http://localhost:8080 (Swagger UI at `/swagger-ui.html`)
- The first admin is created from `ADMIN_EMAIL` / `ADMIN_PASSWORD` **only when the users
  table is empty**.

The dev compose runs Postgres, the backend (`dev` profile), and the frontend Vite server
with hot-reload (`./frontend/src` is bind-mounted).

---

## Configuration reference

All configuration is via environment variables (12-factor). Copy `.env.example` → `.env`.

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `DB_NAME` | yes | `dentalcore` | Postgres database name |
| `DB_USER` | yes | `dentalcore` | Postgres user |
| `DB_PASSWORD` | yes | `dentalcore` | Postgres password (**change for prod**) |
| `DB_HOST` | prod | `postgres` | DB host (set when using managed/external Postgres) |
| `DB_PORT` | no | `5432` | DB port |
| `JWT_SECRET` | **prod** | dev-only fallback | HS512 signing key. Generate: `openssl rand -base64 64 \| tr -d '\n'` |
| `ADMIN_EMAIL` | yes | `admin@dentalcore.local` | First-admin email (first boot only) |
| `ADMIN_PASSWORD` | yes | `change-me-admin-1` | First-admin password (**change immediately**) |
| `FRONTEND_URL` | yes | `http://localhost:5173` | Public URL of the SPA; used for links + CORS in prod |
| `CORS_ALLOWED_ORIGINS` | dev | LAN patterns | Comma-separated allowed origins (prod pins to `FRONTEND_URL`) |
| `DEFAULT_LANGUAGE` | no | `en` | Instance default UI/export language (`en` \| `es`) |
| `AUTH_RATE_LIMIT` | no | `15` | Max login/forgot-password requests per IP per minute |
| `DOCUMENT_STORAGE_PATH` | no | `/data/documents` | Local document storage root |
| `COOKIE_SECURE` | prod | `true` (prod compose) | Send refresh cookie only over HTTPS |
| `TRUST_PROXY_HEADERS` | prod | `true` (prod compose) | Trust `X-Forwarded-For` (only behind a trusted proxy) |
| `SEED_DEMO_DATA` | no | `false` | Seed demo dataset on startup (**evaluation only**) |

> **Production checklist:** set a strong `JWT_SECRET`, a unique `DB_PASSWORD`, change
> `ADMIN_PASSWORD` on first login, set `FRONTEND_URL` to your real HTTPS origin, keep
> `COOKIE_SECURE=true`, leave `SEED_DEMO_DATA=false`, and terminate TLS in front of the app.

---

## Deployment guides

The app is two stateless containers (backend JVM, frontend Nginx) plus a stateful
PostgreSQL and two volumes (`pgdata`, `documents`). Flyway runs migrations automatically
on backend startup, so deploys are just "ship the new image and restart."

### 1. Single VM / VPS (Docker Compose)

The simplest production deployment. Works on any Linux box with Docker (DigitalOcean
droplet, EC2, Hetzner, Lightsail, etc.).

```bash
# on the server
git clone <repo> dentalcore && cd dentalcore
cp .env.example .env
# edit .env: JWT_SECRET, DB_PASSWORD, ADMIN_*, FRONTEND_URL=https://your-domain
docker compose -f docker-compose.prod.yml up -d --build
```

The prod compose:
- Runs the backend with `SPRING_PROFILES_ACTIVE=prod`, `COOKIE_SECURE=true`,
  `TRUST_PROXY_HEADERS=true`, and pins CORS to `FRONTEND_URL`.
- Serves the built SPA from Nginx on **port 80**, which also proxies `/api` to the
  backend (the backend is **not** published to the host — only Nginx is).
- Uses `restart: unless-stopped` on all services.

Put a TLS-terminating reverse proxy (Caddy / Nginx / Traefik) or a cloud load balancer in
front of port 80 — see [TLS / reverse proxy](#tls--reverse-proxy).

### 2. Bare-metal / systemd (no Docker)

**Prerequisites:** JDK 21, Node 22 (build only), PostgreSQL 16, Nginx.

```bash
# Database
createdb dentalcore   # and create a user/role, grant privileges

# Backend — build a fat jar
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package   # → target/dentalcore-backend-*.jar

# Frontend — build static assets
cd ../frontend
npm ci
VITE_API_URL=/api npm run build      # → dist/ (serve behind Nginx; proxy /api to :8080)
```

Run the backend as a systemd unit:

```ini
# /etc/systemd/system/dentalcore.service
[Unit]
Description=DentalCore backend
After=network.target postgresql.service

[Service]
User=dentalcore
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DB_HOST=localhost
Environment=DB_NAME=dentalcore
Environment=DB_USER=dentalcore
Environment=DB_PASSWORD=...
Environment=JWT_SECRET=...
Environment=ADMIN_EMAIL=admin@your-domain
Environment=ADMIN_PASSWORD=...
Environment=FRONTEND_URL=https://your-domain
Environment=CORS_ALLOWED_ORIGINS=https://your-domain
Environment=COOKIE_SECURE=true
Environment=TRUST_PROXY_HEADERS=true
Environment=DOCUMENT_STORAGE_PATH=/var/lib/dentalcore/documents
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=75 -jar /opt/dentalcore/app.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now dentalcore
```

Point Nginx at `frontend/dist` for static files and `proxy_pass http://127.0.0.1:8080`
for `/api`. Ensure `DOCUMENT_STORAGE_PATH` is on persistent, backed-up storage.

### 3. Managed Postgres + container host

To use a managed database (RDS, Cloud SQL, Neon, Supabase, Azure Database for PostgreSQL):

1. Provision PostgreSQL 16 and create the database/user.
2. Drop the `postgres` service from your compose / orchestration and instead set
   `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` to the managed instance
   (use SSL — most managed providers require `sslmode=require`).
3. Run only the `backend` and `frontend` containers. Flyway migrates on backend boot.
4. Mount `documents` on durable storage, or implement the S3 `StoragePort` adapter and
   point it at object storage so the app stays stateless.

### 4. Kubernetes

The two app images are stateless and a natural fit for K8s:

- **backend** `Deployment` (2+ replicas) — env from a `Secret`/`ConfigMap`; liveness/
  readiness probes on `/actuator/health` (the image already defines a HEALTHCHECK).
  `-XX:MaxRAMPercentage=75` sizes the heap to the container memory limit.
- **frontend** `Deployment` (Nginx, stateless, horizontally scalable) behind a `Service`.
- **postgres** as a managed service or a `StatefulSet` with a `PersistentVolumeClaim`.
- **documents**: use the S3 adapter (recommended for multi-replica) or a `ReadWriteMany`
  PVC if you must keep the local FS adapter.
- An `Ingress` (nginx-ingress / Traefik) terminates TLS and routes `/` → frontend,
  `/api` → backend. Run multiple backend replicas safely — JWTs are stateless and the
  refresh-token family is stored in the DB.

### 5. PaaS (Render / Railway / Fly.io / Azure App Service)

Each service can be deployed from its `Dockerfile`:

- Provision a managed Postgres add-on; wire its connection info into the backend env vars.
- Deploy `backend/Dockerfile` (exposes 8080, has a health check) as a web service.
- Deploy `frontend/Dockerfile` with build target `prod` (exposes 80). Set the SPA to
  proxy/point `/api` at the backend's URL, and set `FRONTEND_URL` on the backend to the
  frontend's public URL so CORS and links resolve.
- Attach a persistent disk for `/data/documents`, or use the S3 adapter.

---

## TLS / reverse proxy

The prod frontend listens on plain HTTP/80 and expects TLS to be terminated upstream.
Because `TRUST_PROXY_HEADERS=true`, only put the app behind a **trusted** proxy that sets
`X-Forwarded-For`/`X-Forwarded-Proto`. Example with Caddy (automatic HTTPS):

```caddy
your-domain {
    reverse_proxy localhost:80
}
```

With `COOKIE_SECURE=true`, the refresh-token cookie is only sent over HTTPS — so TLS is
required for login to work in production.

---

## Backups & data

Two stateful pieces:

- **Postgres** (`pgdata` volume / managed DB) — back up with `pg_dump`:
  ```bash
  docker compose exec postgres pg_dump -U "$DB_USER" "$DB_NAME" > backup-$(date +%F).sql
  ```
- **Documents** (`documents` volume / `DOCUMENT_STORAGE_PATH`) — snapshot the volume or
  sync to object storage.

Schema changes are versioned Flyway migrations; never edit a database by hand.

---

## Local development

Run Postgres in Docker and the apps natively for fast iteration:

```bash
docker compose up postgres

# Backend (JDK 21 required — Homebrew may default to a newer JDK)
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  ADMIN_PASSWORD=change-me-admin-1 mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm run dev
```

---

## Tests

```bash
# Backend: unit + integration (Testcontainers) + ArchUnit boundary checks
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify

# Frontend: unit/component tests (Vitest + React Testing Library)
cd frontend
npm test

# End-to-end (Playwright) — needs the full stack running (docker compose up)
cd frontend
npm run test:e2e
```

> Backend integration tests use Testcontainers and need a running Docker daemon. Build
> the backend with **JDK 21** (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

---

## Repository layout

```
backend/    Spring Boot modular monolith (one package per module: api + internal)
  src/main/resources/db/migration/   Flyway migrations V1 → V22
frontend/   React SPA, feature-based folders under src/features/
  src/i18n/locales/{en,es}/          per-namespace translation catalogs
  e2e/                               Playwright specs
docs/architecture/                   architecture, DB design, folder structure, roadmap
docker-compose.yml                   dev stack (Vite hot-reload)
docker-compose.prod.yml              prod stack (Nginx static + API proxy)
```

---

## Demo data

Set `SEED_DEMO_DATA=true` (evaluation environments only — **never production**) to seed
demo providers, patients, appointments, a treatment plan, charting, and ledger activity on
startup. The seeder is idempotent.
