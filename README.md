# DentalCore PMS

A production-grade, web-based Dental Practice Management System (modular monolith).
Inspired by Open Dental. Architecture docs live in [`docs/architecture/`](docs/architecture/).

## Stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript, Vite, React Router, TanStack Query, Tailwind CSS, React Hook Form, Zod |
| Backend | Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA / Hibernate |
| Database | PostgreSQL 16, Flyway migrations |
| Testing | JUnit 5, Mockito, Testcontainers, ArchUnit, Vitest + React Testing Library, Playwright |
| Infra | Docker, Docker Compose, Nginx (prod) |

## Quick start (Docker)

```bash
cp .env.example .env        # set ADMIN_PASSWORD (and JWT_SECRET for non-dev)
docker compose up --build
```

- Frontend: http://localhost:5173
- API: http://localhost:8080 (Swagger UI: http://localhost:8080/swagger-ui.html)
- First admin is created automatically from `ADMIN_EMAIL` / `ADMIN_PASSWORD` when the
  database is empty.

## Local development (without Docker for the apps)

```bash
docker compose up postgres            # database only

cd backend
ADMIN_PASSWORD=change-me-admin-1 mvn spring-boot:run -Dspring-boot.run.profiles=dev

cd frontend
npm install
npm run dev                           # http://localhost:5173, proxies /api -> :8080
```

## Tests

```bash
# Backend: unit + ArchUnit + Testcontainers integration (Docker required)
# Build with JDK 21 (e.g. export JAVA_HOME=$(/usr/libexec/java_home -v 21) on macOS)
cd backend && mvn verify

# Frontend: component tests
cd frontend && npm test

# Frontend: end-to-end (stack must be running)
cd frontend && npm run test:e2e
```

## Production

```bash
cp .env.example .env   # set strong DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD, FRONTEND_URL
docker compose -f docker-compose.prod.yml up --build -d
```

Terminate TLS in front of the `frontend` service (load balancer or host nginx) and set
`FRONTEND_URL` to the public https origin. `COOKIE_SECURE=true` is enforced in prod compose.

## Security highlights

- JWT access tokens (15 min) + rotating refresh tokens in `httpOnly` `SameSite=Strict`
  cookies, with reuse detection that revokes the whole token family
- BCrypt(12) password hashing; account lockout after 5 failed attempts
- Role-based access control (`ADMIN`, `DENTIST`, `HYGIENIST`, `FRONT_DESK`, `BILLING`,
  `READ_ONLY`) enforced at URL, method, and UI levels
- Append-only audit log (who/what/when/before/after/IP) written in independent transactions
- DTO-only API surface — JPA entities never cross the controller boundary (ArchUnit-enforced)
- Password reset with single-use, time-boxed, hashed tokens; no account enumeration
- Per-IP rate limiting on credential endpoints (`AUTH_RATE_LIMIT`, default 15/min → 429)
- Document storage behind a `StoragePort` (local volume now, S3-compatible later) with
  content-type whitelist, size caps, and path-traversal-proof keys
- Financial dates use the clinic's timezone (`clinics.timezone`), not the server clock

## Repository layout

```
backend/    Spring Boot modular monolith (auth, users, audit, shared, infrastructure, …)
frontend/   React SPA (feature-based folders)
docs/       Architecture documentation
```

## Demo data

Set `SEED_DEMO_DATA=true` in `.env` (evaluation only) to seed demo providers, patients,
appointments, a treatment plan, charting, and ledger activity on startup. Idempotent.

## Feature status

All roadmap phases are implemented: foundation/auth → patients & providers → scheduling
→ treatment plans & procedures + clinical notes → insurance & claims → clinical charting
(odontogram) & profile depth → billing ledger → documents → reporting → hardening.
See `docs/architecture/` for design decisions.
