# DentalCore PMS — Folder Structure

## Repository layout

```
opendental-clone/
├── docs/
│   └── architecture/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/dentalcore/
│       │   │   ├── DentalCoreApplication.java
│       │   │   ├── auth/
│       │   │   │   ├── api/                 # public: interfaces, DTOs, events
│       │   │   │   └── internal/
│       │   │   │       ├── web/             # controllers
│       │   │   │       ├── service/
│       │   │   │       ├── repository/
│       │   │   │       ├── entity/
│       │   │   │       └── mapper/
│       │   │   ├── users/        (same api/internal layout)
│       │   │   ├── patients/
│       │   │   ├── providers/
│       │   │   ├── appointments/
│       │   │   ├── treatmentplans/
│       │   │   ├── procedures/
│       │   │   ├── insurance/
│       │   │   ├── billing/
│       │   │   ├── documents/
│       │   │   ├── reporting/
│       │   │   ├── audit/
│       │   │   ├── shared/                  # BaseEntity, errors, pagination, events
│       │   │   └── infrastructure/          # security config, storage port, web config
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       └── db/migration/            # Flyway V1__*.sql ...
│       └── test/java/com/dentalcore/
│           ├── architecture/                # ArchUnit boundary tests
│           ├── <module>/...                 # unit + integration per module
│           └── support/                     # Testcontainers base classes
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig.json, tailwind.config.js
│   ├── Dockerfile
│   ├── playwright.config.ts
│   ├── e2e/
│   └── src/
│       ├── app/                             # router, providers, layout
│       ├── components/                      # reusable UI library (Button, Input, Table…)
│       ├── lib/                             # api client, auth storage, utils
│       ├── features/
│       │   ├── auth/        (components/, hooks/, api/, schemas/)
│       │   ├── dashboard/
│       │   ├── patients/
│       │   ├── providers/
│       │   ├── appointments/
│       │   ├── treatment-plans/
│       │   ├── insurance/
│       │   ├── billing/
│       │   └── documents/
│       └── types/
├── docker-compose.yml                       # dev
├── docker-compose.prod.yml
├── nginx/nginx.conf
├── .env.example
└── README.md
```

## Backend module anatomy (example: `patients`)

```
patients/
├── api/
│   ├── PatientApi.java            # interface other modules consume
│   ├── PatientSummaryDto.java
│   └── event/PatientCreatedEvent.java
└── internal/
    ├── web/PatientController.java
    ├── service/PatientService.java
    ├── repository/PatientRepository.java
    ├── entity/Patient.java
    ├── dto/                       # request/response DTOs (REST surface)
    ├── mapper/PatientMapper.java  # MapStruct
    └── validation/
```

Rules (ArchUnit-enforced):
- `internal` of module A is invisible to module B.
- Controllers depend only on their own module's services.
- Entities never appear in controller signatures.
- Only `shared` and module `api` packages are cross-module importable.
