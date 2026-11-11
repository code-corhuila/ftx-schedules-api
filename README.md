# ftx-schedules-api

> Schedules domain API: weekly opening hours of each court and the slots it offers on a date

Part of **Futbolix**, the distributed system for reserving the four football courts of a
single sports facility. Governance and documentation live in
[`ftx-docs`](https://github.com/code-corhuila/ftx-docs). User stories: HU-ADM-001 (manage
schedules) and HU-RES-001 (availability). Contract: the schedule operations of
`ftx-docs/07-api/contracts/openapi/court-service.yaml`.

## Endpoints

| Method and path | Who | Responses |
|---|---|---|
| `GET /api/v1/courts/{id}/schedules` | Public | 200 `{ "data": [Schedule] }` MON..SUN, 404 unknown court |
| `GET /api/v1/courts/{id}/bookable-slots?date=YYYY-MM-DD` | Public (the reservation-service calls it) | 200 `BookableSlots`, 400 missing or bad date, 404, 503 |
| `PUT /api/v1/admin/courts/{id}/schedules/{dayOfWeek}` | `ADMINISTRATOR` | 200 `Schedule`, 400 (time not `HH:mm:ss` or not a whole hour, unknown field, bad day), 401, 403, 404, 422 `INVALID_SCHEDULE` |
| `DELETE /api/v1/admin/courts/{id}/schedules/{dayOfWeek}` | `ADMINISTRATOR` | 204 (the court is closed that day), 401, 403, 404 |
| `GET /api/v1/health` | Public | 200 `{ "status": "ok", "timestamp", "dependencies": { "database": "ok" } }` (contract); 503 |
| `GET /health`, `/health/liveness`, `/health/readiness` | Probes | 200 `UP` |

Times are always `HH:mm:ss`. Bookable slots are the whole hours between opening and closing;
a court that is not `ACTIVE`, or a day without schedule, offers none. Reservations are not
subtracted here: that is the reservation-service (`GET /availability`).

## Decisions

- **Court status and price come from `ftx-courts-api`.** `bookable-slots` must return the
  court's status and current price, which belong to the courts domain. This service calls
  `GET /api/v1/courts/{id}` of `ftx-courts-api` inside the `platform` network (2 s timeout, the
  request's `X-Correlation-Id` forwarded). It never reads the `courts` schema: the database
  forbids it. If `ftx-courts-api` does not answer, the response is 503 `SERVICE_UNAVAILABLE`
  instead of guessing. The same call tells whether a court exists (404).
- **Port 8085.** The docs still describe a single `court-service` on 8082 (now `ftx-courts-api`);
  this service takes the next free port until the team records it in `ftx-docs`.

## Who can change a schedule

`/api/v1/admin/**` needs the administrator access token in `Authorization: Bearer <token>`, and
this service **checks it itself** (ADR-012): RS256, signed by a key of the JWKS that
`ftx-customers-api` publishes (`GET /api/v1/auth/jwks`), issuer `ftx-customers-api`, not expired,
with `sub`, `iat`, `exp`, `jti` and `roles`. No valid token: 401. Another role: 403. The keys
cannot be fetched: 503, and nothing is changed. Public reads never need the keys.

`X-User-Id` and `X-User-Role` are **ignored**: the port of this API is published on the host, so a
client could send them, and `ftx-docs/07-api/authentication.md` says a service must never trust
client-supplied identity headers. The gateway forwards the `Authorization` header unchanged. Every
change is logged on the `audit` logger with the administrator id (the `sub` of the token) and the
hours before and after (FR-036).

## Architecture (hexagonal)

```
domain/                  CourtSchedule, DayCode, Slot, BookableSlots, CourtInfo: rules only
application/             ScheduleService (use cases) and its ports
  port/in/               GetWeeklySchedule, GetBookableSlots, SaveSchedule
  port/out/              ScheduleRepository, CourtCatalog, ScheduleAuditTrail
adapter/in/web/          controllers, ErrorResponse, administrator guard, correlation id
adapter/out/persistence/ JdbcScheduleRepository (schedules.court_schedule)
adapter/out/courts/      RestCourtCatalog (client of ftx-courts-api)
adapter/out/audit/       structured audit log
adapter/config/          wiring; writes run in one transaction
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `SCHEDULES_DB_URL` | `jdbc:postgresql://postgres:5432/ftx` | The single instance of `ftx-infra-postgres` |
| `SCHEDULES_DB_USER` | `schedules_app` | Login user created by `ftx-infra-postgres` |
| `SCHEDULES_DB_PASSWORD` | none (required) | Its password, from `env/<environment>.env` of `ftx-infra-postgres` |
| `COURTS_API_URL` | `http://ftx-courts-api:8082` | Base URL of `ftx-courts-api` |
| `COURTS_API_TIMEOUT` | `2s` | Connect and read timeout of that call |
| `SCHEDULES_AUTH_JWKS_URL` | `http://ftx-customers-api:8081/api/v1/auth/jwks` | Public keys of the administrator tokens (cached by the service) |
| `SCHEDULES_AUTH_ISSUER` | `ftx-customers-api` | Required `iss` of the tokens |
| `PORT` | `8085` | HTTP port |

The service never runs migrations: the `schedules` schema belongs to `ftx-schedules-db`.

## Run it

Requirements: JDK 21, Maven 3.9, the database of `ftx-infra-postgres` with the
`ftx-schedules-db` migrations applied, and `ftx-courts-api` running.

```bash
mvn package -DskipTests
SCHEDULES_DB_URL=jdbc:postgresql://localhost:5432/ftx SCHEDULES_DB_PASSWORD=<schedules_app password> COURTS_API_URL=http://localhost:8082 java -jar target/ftx-schedules-api-0.1.0.jar
```

With Docker, on the `platform` network:

```bash
docker build -t ftx-schedules-api .
docker run --rm --network platform -p 8085:8085 -e SCHEDULES_DB_PASSWORD=<schedules_app password> ftx-schedules-api
```

## Tests

```bash
mvn verify
```

- Unit tests of the domain and the use cases (inactive court, closed day, unknown court,
  courts service down), web tests of every response of the contract (including a non-administrator
  that must be refused, spoofed `X-User-*` headers, expired, foreign and forged tokens, and the
  keys being unavailable; tokens are signed with a key made for each run), and tests of the `ftx-courts-api` client against a mock server
  (200, 404, 500, correlation id forwarded).
- Integration tests against a real PostgreSQL with the **real migrations** of the sibling
  `ftx-schedules-db` clone (`-Dschedules.db.dir=...`, default `../ftx-schedules-db`), connected
  as `schedules_app`. The database is a Testcontainers PostgreSQL 16 when Docker is available, or
  an existing one given by `FTX_IT_JDBC_URL`, `FTX_IT_ADMIN_USER` and `FTX_IT_ADMIN_PASSWORD` (it
  drops and rebuilds the `schedules` schema there: never point it at a shared database).

## Branching

`develop`, `qa` and `main` accept no direct commits. Per user story: `hu-<id>-dev → develop`, then
`git cherry-pick -x` into `hu-<id>-qa → qa`, then into a release branch `→ main` with the
instructor's approval and the tag.
