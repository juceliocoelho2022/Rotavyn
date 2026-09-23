# Rotavyn MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a multi-company logistics workflow from shipment creation through proof of delivery, with human-reviewed incident recommendations.

**Architecture:** React/TypeScript client calls a Spring Boot modular monolith. PostgreSQL stores tenant-scoped operational records; a separate recommendation adapter may call an LLM, while deterministic delay detection runs within the backend.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring Security, Spring Data JPA, Flyway, PostgreSQL 17, React, TypeScript, Vite, Docker Compose, JUnit 5, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-23-rotavyn-design.md`

## Global Constraints

- Name: Rotavyn; tagline: Inteligência para mover o mundo.
- Tenant ID is resolved server-side from the authenticated principal; no client-provided tenant ID in operational writes.
- Persist instants in UTC; present in the tenant's configured time zone. Start in Portuguese.
- AI may create recommendations and follow-up tasks after human approval; it must never directly change shipment status.
- Business operations work without an LLM key. Never store real customer data in fixtures.
- Use a single Spring Boot deployment and PostgreSQL; introduce no Kafka or extra microservices in the MVP.

## Review Focus

1. Unknown tenant or user attempting cross-tenant access receives 404/403 without exposing resource details (Task 1).
2. Duplicate scan key cannot append another timeline event or transition (Task 3).
3. Two operators updating an old version receive 409 and preserve the successful event (Task 3).
4. Delivery deadline on a daylight-saving boundary uses UTC instants and the tenant's IANA time zone for display (Task 4).
5. LLM timeout or malformed response leaves shipment operations healthy and reports recommendation unavailable (Task 5).

---

## File map

- `backend/pom.xml`, `backend/src/main/resources/application.yml`, `compose.yml`: application and local runtime.
- `backend/src/main/resources/db/migration/V1__core.sql`: company, user, fleet, shipment, event, incident and recommendation tables with tenant indexes and constraints.
- `backend/src/main/java/br/com/rotavyn/identity/`: authentication, principal and tenant-scoped access.
- `backend/src/main/java/br/com/rotavyn/fleet/`: drivers and vehicles.
- `backend/src/main/java/br/com/rotavyn/shipment/`: shipment, transitions, assignment and timeline.
- `backend/src/main/java/br/com/rotavyn/incident/`: incidents, delays and recommendations.
- `backend/src/main/java/br/com/rotavyn/common/`: problem responses and correlation logging.
- `frontend/src/features/`: login, fleet, shipments, dashboard, recommendations and driver screens.
- `backend/src/test/java/br/com/rotavyn/`: unit and database integration tests.

### Task 1: Runtime, tenancy and access

**Files:** Create `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration/V1__core.sql`, `backend/src/main/java/br/com/rotavyn/RotavynApplication.java`, `backend/src/main/java/br/com/rotavyn/identity/{SecurityConfig,CurrentUser,TenantAccess,IdentityController}.java`, `backend/src/main/java/br/com/rotavyn/common/ApiErrors.java`, `backend/src/test/java/br/com/rotavyn/identity/TenantAccessIT.java`, `compose.yml`.

**Interfaces:** `CurrentUser(UUID userId, UUID tenantId, Role role)` with `Role={PLATFORM_ADMIN,TENANT_MANAGER,DISPATCHER,DRIVER}`; `TenantAccess.requireTenantResource(UUID resourceTenantId, CurrentUser actor): void`; `GET /actuator/health`, `POST /api/v1/auth/login` returns a signed session token. Dev bootstrap creates one platform administrator from environment credentials once.

- [ ] Write `TenantAccessIT` with tenants A/B: A's dispatcher cannot read B's shipment; unknown shipment returns 404; a driver cannot provision tenants. Write a login test that rejects a bad password.
- [ ] Run `cd backend && ./mvnw -q -Dtest=TenantAccessIT test`; expect missing application classes or failing assertions.
- [ ] Scaffold Spring Boot with Web, Validation, Security, JPA, Flyway, Actuator and PostgreSQL. Create migration with `tenant_id NOT NULL` on operational tables, `UNIQUE(tenant_id, tracking_code)` and tenant-scoped foreign keys. Implement credential login, secure password hashes, signed tokens, role guards, server-derived tenant context and ProblemDetail. Keep secrets in environment variables; supply `.env.example` with fake values.
- [ ] Run `docker compose up -d db` and `cd backend && ./mvnw -q test`; expect tests green; confirm Flyway applies to empty DB twice without change.
- [ ] Commit: `git add backend compose.yml .env.example && git commit -m "feat: establish tenant-safe backend"`.

### Task 2: Fleet and shipment creation

**Files:** Create `backend/src/main/java/br/com/rotavyn/fleet/{Driver,Vehicle,FleetService,FleetController}.java`, `backend/src/main/java/br/com/rotavyn/shipment/{Shipment,ShipmentService,ShipmentController}.java`, `backend/src/test/java/br/com/rotavyn/shipment/ShipmentCreationIT.java`.

**Interfaces:** `POST/GET /api/v1/drivers`, `POST/GET /api/v1/vehicles`, `POST/GET /api/v1/shipments`, `GET /api/v1/shipments/{id}`. `ShipmentService.create(CreateShipment command, CurrentUser actor): ShipmentView`; use UUID identifiers and tenant-scoped tracking codes.

- [ ] Write `ShipmentCreationIT`: A can create/list its shipment, B receives 404 for its ID, duplicate tracking code within A returns 409, same code in B succeeds, blank country or invalid delivery window returns 400.
- [ ] Run `cd backend && ./mvnw -q -Dtest=ShipmentCreationIT test`; expect red.
- [ ] Implement DTO validation, fleet availability checks, tenant-scoped queries and cursor/page based lists. Store ISO country code, address fields, promised deadline as UTC instant, and `version` for optimistic locking. Dispatchers create remittances; managers manage fleet.
- [ ] Run the test above, then `cd backend && ./mvnw -q test`; expect green.
- [ ] Commit: `git add backend && git commit -m "feat: manage fleet and shipments"`.

### Task 3: Assignment, status machine and history

**Files:** Create `backend/src/main/java/br/com/rotavyn/shipment/{ShipmentStatus,ShipmentEvent,TransitionPolicy,DispatchService,EventController}.java`, `backend/src/test/java/br/com/rotavyn/shipment/ShipmentLifecycleIT.java`.

**Interfaces:** `PUT /api/v1/shipments/{id}/assignment` accepts driver and vehicle IDs plus expected version; `POST /api/v1/shipments/{id}/events` accepts `eventType`, `idempotencyKey`, `expectedVersion`, `occurredAt` and note; `GET /api/v1/shipments/{id}/events` returns chronological history. `TransitionPolicy.next(ShipmentStatus state, EventType event): ShipmentStatus` throws a domain violation on illegal transitions.

- [ ] Write `ShipmentLifecycleIT`: full CREATED→ASSIGNED→PICKED_UP→IN_TRANSIT→DELIVERY_ATTEMPTED→IN_TRANSIT→DELIVERED; illegal transition 422; repeated key leaves event count unchanged; stale version 409; driver can post only on assigned shipments; all cross-tenant IDs rejected.
- [ ] Run `cd backend && ./mvnw -q -Dtest=ShipmentLifecycleIT test`; expect red.
- [ ] Implement transition table, transactionally append-only event and current status update, `UNIQUE(tenant_id, idempotency_key)` and JPA `@Version`; assignment checks driver and vehicle in same tenant. A retry with same key and same body returns original result; same key with different body returns 409.
- [ ] Run `cd backend && ./mvnw -q test`; expect green.
- [ ] Commit: `git add backend && git commit -m "feat: track shipments and immutable events"`.

### Task 4: Operational dashboard and driver view

**Files:** Create `frontend/package.json`, `frontend/src/{main.tsx,App.tsx,api/client.ts}`, `frontend/src/features/{auth,shipments,fleet,dashboard,driver}/*.tsx`, `frontend/src/styles.css`, `backend/src/main/java/br/com/rotavyn/shipment/DashboardController.java`, `backend/src/test/java/br/com/rotavyn/shipment/DashboardIT.java`.

**Interfaces:** `GET /api/v1/dashboard/summary` returns counts by state and overdue count; UI uses typed DTOs from Tasks 1–3 and keeps auth token in a session-scoped store. The driver view never offers shipment IDs outside the authenticated assignment.

- [ ] Write `DashboardIT` asserting tenant-specific counts and UTC deadline behavior at a time-zone boundary; create UI interaction checks for create, assignment, event submission and error feedback.
- [ ] Run `cd backend && ./mvnw -q -Dtest=DashboardIT test`; expect red. After scaffolding frontend, run `cd frontend && npm test -- --run`; expect missing views red.
- [ ] Build responsive navigation, shipment list/detail, event timeline, forms, alerts and driver view. Render timestamps with `Intl.DateTimeFormat` and configured IANA zone; add text labels to every status badge. Show pending/error states for API failures.
- [ ] Run `cd backend && ./mvnw -q test`, `cd frontend && npm test -- --run` and `cd frontend && npm run build`; expect green. Manually complete one shipment from the UI.
- [ ] Commit: `git add frontend backend && git commit -m "feat: deliver logistics operations panel"`.

### Task 5: Incidents and reviewed recommendations

**Files:** Create `backend/src/main/java/br/com/rotavyn/incident/{IncidentService,DelayDetector,RecommendationProvider,DeterministicProvider,RecommendationService,IncidentController}.java`, `backend/src/test/java/br/com/rotavyn/incident/RecommendationIT.java`, `frontend/src/features/recommendations/RecommendationsPage.tsx`, `docs/operations.md`.

**Interfaces:** `GET /api/v1/incidents`, `POST /api/v1/shipments/{id}/recommendations`, `POST /api/v1/recommendations/{id}/decision` with ACCEPTED or DISMISSED. `RecommendationProvider.generate(ShipmentContext context): RecommendationDraft`; acceptance creates `follow_up_task`, not a shipment event.

- [ ] Write `RecommendationIT`: overdue unfinished shipment detected; model response references only in-tenant event IDs; acceptance creates a task without changing shipment status; duplicate decision returns 409; timeout/malformed provider response yields unavailable state and shipment API remains healthy.
- [ ] Run `cd backend && ./mvnw -q -Dtest=RecommendationIT test`; expect red.
- [ ] Implement UTC-based delay detector, recommendation validation, audit fields, deterministic demo provider and optional remote adapter behind environment configuration with strict timeout. Document model mode visibly in UI and redact sensitive fields from prompts/logs.
- [ ] Run `cd backend && ./mvnw -q test`, `cd frontend && npm test -- --run`, `cd frontend && npm run build`; then run the README scenario in `docs/operations.md` with two tenants and AI provider disabled.
- [ ] Commit: `git add backend frontend docs && git commit -m "feat: add reviewed incident recommendations"`.

## Release check

- [ ] Execute complete two-tenant scenario with fictitious data; verify cross-tenant access fails.
- [ ] Verify health, logs with request IDs, all backend tests, frontend tests and production build.
- [ ] Document PowerShell and Bash setup, environment variables, demo accounts, expected output and limitations. Keep real credentials out of git.
