# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean package -DskipTests

# Run (dev profile is active by default)
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=IfotoBackendApplicationTests

# Flyway operations (requires DEV_DB_URL, DEV_DB_USER, DEV_DB_PASS env vars)
./mvnw flyway:info
./mvnw flyway:repair
```

## Required Environment Variables

The dev profile reads these from the environment (no `.env` file checked in):

| Variable | Purpose |
|---|---|
| `DEV_DB_URL` | JDBC URL for local MySQL, e.g. `jdbc:mysql://localhost:3306/ifoto_dev` |
| `DEV_DB_USER` | DB username |
| `DEV_DB_PASS` | DB password |
| `JWT_SECRET` | HS256 signing key, must be ≥ 32 chars |
| `JWT_EXPIRATION_MS` | Access token TTL in ms (e.g. `900000` = 15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | Refresh token TTL in ms (default `604800000` = 7 days) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP for password-reset and verification emails |
| `BILLPLZ_API_KEY` | Billplz payment gateway API key |
| `BILLPLZ_COLLECTION_ID` | Billplz collection ID |
| `BILLPLZ_X_SIGNATURE_KEY` | Billplz webhook signature verification key |
| `BILLPLZ_CALLBACK_URL` | URL Billplz POSTs payment results to |
| `BILLPLZ_REDIRECT_URL` | URL Billplz redirects users to after payment |
| `BILLPLZ_BASE_URL` | Billplz API base URL (default: sandbox `https://www.billplz-sandbox.com/api/v3`) |

## Architecture

### Layers
- **controller** — `@RestController` classes under `/api/v1/`. See route summary below.
- **service** — Business logic. Includes payment handler strategy pattern under `service/payment/`.
- **repository** — Spring Data JPA interfaces backed by MySQL.
- **model** — JPA entities. Schema managed exclusively by Flyway (`ddl-auto=none`). Enumerators live in `model/enumerator/`, JPA converters in `model/converter/`.
- **security** — `JwtUtil` (token creation/validation), `JwtAuthenticationFilter` (per-request token extraction), `CookieUtil` (HttpOnly refresh-token cookie helpers).
- **config** — `SecurityConfig` (filter chain, route authorization), `AppConfig` (BCrypt strength 12, `AuthenticationManager`), `WebConfig` (CORS), `BillplzConfig` (Billplz REST client), `GlobalExceptionHandler`.
- **scheduler** — `RentalScheduler`: daily cron at midnight to auto-mark rentals `ACTIVE` (when start date reached) and `OVERDUE` (when due date passed, recalculates penalties).
- **validation** — Custom constraint annotations (`@DateRangeValid`, `@SubEquipmentQuantityValid`) with corresponding validators.
- **dto/** — Record-based request/response objects grouped by feature: `UserDTO/`, `EquipmentDTO/`, `EquipmentRentalDTO/`, `EventDTO/`, `PaymentDTO/`, `ReceiptDTO/`, `RentalPricingDTO/`, `ReportDTO/`. Never pass raw entities over HTTP.

### Auth flow
1. `POST /api/v1/auth/login` → returns short-lived JWT access token in body + long-lived refresh token in HttpOnly cookie.
2. Access token is sent as `Authorization: Bearer <token>` on subsequent requests.
3. `POST /api/v1/auth/refresh` → reads cookie, validates against DB (`refresh_tokens` table), issues new access token.
4. `POST /api/v1/auth/logout` → revokes DB entry, clears cookie.

### Authorization model
- Users have a `Set<Role>` (many-to-many via `user_roles`). Spring Security grants all roles from that set.
- Role names are stored as `ROLE_*` (e.g. `ROLE_ADMIN`). The service layer normalizes bare names automatically.
- Roles in use: `ADMIN`, `HIGH_COMMITTEE`, `EQUIPMENT_COMMITTEE`, `EVENT_COMMITTEE`, `STUDENT`, `NON_STUDENT`.

### Route authorization summary

| Path pattern | Method | Required role(s) |
|---|---|---|
| `/api/v1/auth/**`, `/api/v1/register` | any | public |
| `/api/v1/users/**` | any | `ADMIN` or `HIGH_COMMITTEE` |
| `/api/v1/rental-pricing/**` | GET | public |
| `/api/v1/rental-pricing/**` | PUT | `EQUIPMENT_COMMITTEE` |
| `/api/v1/equipment/**` | POST/PUT/DELETE | `EQUIPMENT_COMMITTEE` |
| `/api/v1/events/committee/**` | GET | `HIGH_COMMITTEE` or `EVENT_COMMITTEE` |
| `/api/v1/events/users/**` | GET | `ADMIN` or `HIGH_COMMITTEE` |
| `/api/v1/events/**` | any | `HIGH_COMMITTEE` |
| `/api/v1/rentals` | POST | `STUDENT` or `NON_STUDENT` |
| `/api/v1/rentals/my` | GET | `STUDENT` or `NON_STUDENT` |
| `/api/v1/rentals/*/pay` | POST | `STUDENT` or `NON_STUDENT` |
| `/api/v1/rentals/*` | DELETE | `STUDENT` or `NON_STUDENT` |
| `/api/v1/rentals` | GET | `EQUIPMENT_COMMITTEE` |
| `/api/v1/rentals/**` | PATCH | `EQUIPMENT_COMMITTEE` |
| `/api/v1/rentals/trigger-active`, `/trigger-overdue` | POST | `EQUIPMENT_COMMITTEE` |
| `/api/v1/reports/**` | GET | `EQUIPMENT_COMMITTEE` or `HIGH_COMMITTEE` |
| `/api/v1/payments/callback` | POST | public (Billplz webhook) |
| `/api/v1/payments/result` | GET | public (Billplz redirect) |
| `/api/v1/receipts/my` | GET | `STUDENT` or `NON_STUDENT` |

### Key models
- `User`, `Role`, `RefreshToken`, `PasswordResetToken`, `EmailVerificationToken`
- `MainEquipment`, `SubEquipment` — equipment catalogue (main + sub-items)
- `EquipmentRental`, `EquipmentRentalItem` — rental orders and their line items
- `RentalCategory`, `RentalPricing` — pricing tiers (e.g. member vs non-member rates)
- `Payment`, `Receipt` — Billplz payment records and generated receipts
- `Event` — club events

### Rental status lifecycle
`PENDING_REVIEW` → `APPROVED` / `REJECTED` → `PENDING_PAYMENT` → `PAID` → `ACTIVE` → `RETURNED`  
Side paths: `CANCELLED` (anytime before ACTIVE), `OVERDUE` (from ACTIVE when due date passes)

### Payment flow
- `POST /api/v1/rentals/{id}/pay` → `PaymentService` selects handler via `PaymentMethodHandler` strategy (`CashPaymentHandler` or `OnlinePaymentHandler`).
- Online payments: creates a Billplz bill via `BillplzService`, returns redirect URL.
- Billplz POSTs callback to `/api/v1/payments/callback`; `BillplzXSignatureService` verifies HMAC-SHA256 signature before updating rental status.
- Amounts stored in **cents** (integer) internally; `ReportingService` converts to `BigDecimal` for API responses.

### Reporting endpoints (`/api/v1/reports/`)
- `GET /kpi` — total rentals this month, total revenue, active count, overdue count
- `GET /rental-status` — rental count grouped by status
- `GET /rental-volume?months=12` — monthly rental count for last N months (max 60)
- `GET /revenue?months=12` — monthly base + penalty revenue for last N months
- `GET /equipment-utilization` — rental count per sub-equipment item

### Database migrations
Flyway migrations live in `src/main/resources/db/migration/` using the `V{n}__description.sql` naming convention. Never modify existing migration files — always add a new version. Latest migration: `V22__rentals_update_status_enums.sql`.

### Profiles
- `dev` (default) — verbose SQL logging, CORS allows `localhost:5173`, `localhost:3000`, `127.0.0.1:5173`.
- `prod` — configure via `application-prod.properties`; CORS and DB sourced from env vars.
- `test` — `application-test.properties`.
