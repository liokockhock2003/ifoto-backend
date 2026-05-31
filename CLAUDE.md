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
| `MAIL_HOST` | SMTP host (default: `smtp.resend.com`) |
| `MAIL_PORT` | SMTP port (default: `587`) |
| `MAIL_USERNAME` | SMTP username (`resend` for Resend) |
| `MAIL_PASSWORD` | SMTP password (Resend API key starting with `re_`) |
| `MAIL_SMTP_AUTH` | Enable SMTP auth (default: `true`) |
| `MAIL_SMTP_STARTTLS` | Enable STARTTLS (default: `true`) |
| `MAIL_SMTP_SSL` | Enable SSL (default: `false`; set `true` only for port 465) |
| `APP_MAIL_FROM` | Sender address (default: derived from `MAIL_USERNAME`) |
| `APP_MAIL_APP_NAME` | Sender display name (default: `iFoto`) |
| `APP_PASSWORD_RESET_TOKEN_EXPIRATION_MS` | Password reset token TTL in ms (default: `900000` = 15 min) |
| `APP_PASSWORD_RESET_URL_BASE` | Frontend reset-password URL (default: `http://localhost:5173/reset-password`) |
| `EMAIL_VERIFY_TOKEN_EXPIRY_MS` | Email verification token TTL in ms (default: `86400000` = 24 h) |
| `EMAIL_VERIFY_URL_BASE` | Frontend verify-email URL (default: `http://localhost:5173/verify-email`) |
| `BILLPLZ_API_KEY` | Billplz payment gateway API key |
| `BILLPLZ_COLLECTION_ID` | Billplz collection ID |
| `BILLPLZ_X_SIGNATURE_KEY` | Billplz webhook signature verification key |
| `BILLPLZ_CALLBACK_URL` | URL Billplz POSTs payment results to |
| `BILLPLZ_REDIRECT_URL` | URL Billplz redirects users to after payment |
| `BILLPLZ_BASE_URL` | Billplz API base URL (default: sandbox `https://www.billplz-sandbox.com/api/v3`) |
| `PORT` | Server port (default: `8080`) |

## Architecture

### Layers
- **controller** — `@RestController` classes under `/api/v1/`. See route summary below.
- **service** — Business logic. Includes payment handler strategy pattern under `service/payment/`.
- **repository** — Spring Data JPA interfaces backed by MySQL.
- **model** — JPA entities. Schema managed exclusively by Flyway (`ddl-auto=none`). Enumerators live in `model/enumerator/`, JPA converters in `model/converter/`.
- **security** — `JwtUtil` (token creation/validation), `JwtAuthenticationFilter` (per-request token extraction), `CookieUtil` (HttpOnly refresh-token cookie helpers).
- **config** — `SecurityConfig` (filter chain, route authorization), `AppConfig` (BCrypt strength 12, `AuthenticationManager`, `@EnableScheduling`, `@EnableAsync`), `WebConfig` (CORS), `BillplzConfig` (Billplz REST client), `GlobalExceptionHandler`.
- **security** (additional) — `RateLimitFilter`: `OncePerRequestFilter` using Bucket4j (in-memory). Runs after `JwtAuthenticationFilter`. Public endpoints keyed by client IP, authenticated endpoints keyed by username. Limits: login 5/15 min, forgot-password 3/hr, register 5/hr, refresh 20/15 min, POST rentals 3/10 min, POST rentals/\*/pay 5/15 min. Returns HTTP 429 with `Retry-After` header on exhaustion.
- **scheduler** — `RentalScheduler`: daily cron at midnight to auto-mark rentals `ACTIVE`/`OVERDUE` and event equipment requests `ACTIVE`. Also runs all three jobs on startup via `@PostConstruct` to catch up any missed midnight tick during restarts/redeploys.
- **validation** — Custom constraint annotations (`@DateRangeValid`, `@SubEquipmentQuantityValid`) with corresponding validators; `DateRangeValidatable` marker interface.
- **exception** — `TokenException` with enum reasons: `MISSING`, `INVALID`, `ALREADY_USED`, `EXPIRED`.
- **event** — `ReceiptReadyEvent`: Spring `ApplicationEvent` published by `ReceiptService` after each receipt/invoice is persisted; consumed by `RentalNotificationService` via `@TransactionalEventListener(AFTER_COMMIT)`.
- **service/MailService** — All send methods are `@Async` (fire-and-forget; SMTP failure never blocks the caller). Sends HTML emails via `MimeMessageHelper`. Provider: Resend via SMTP bridge (`smtp.resend.com:587`). For local testing use Mailpit (`docker run -p 1025:1025 -p 8025:8025 axllent/mailpit`) with `MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false`.
- **dto/** — Record-based request/response objects grouped by feature: `UserDTO/`, `EquipmentDTO/`, `EquipmentRentalDTO/`, `EventDTO/`, `EventEquipmentRequestDTO/`, `PaymentDTO/`, `ReceiptDTO/`, `RentalPricingDTO/`, `ReportDTO/`. Never pass raw entities over HTTP.

### Auth flow
1. `POST /api/v1/auth/login` → returns short-lived JWT access token in body + long-lived refresh token in HttpOnly cookie.
2. Access token is sent as `Authorization: Bearer <token>` on subsequent requests.
3. `POST /api/v1/auth/refresh` → reads cookie, validates against DB (`refresh_tokens` table), issues new access token.
4. `POST /api/v1/auth/logout` → revokes DB entry, clears cookie.
5. `POST /api/v1/auth/forgot-password` / `POST /api/v1/auth/reset-password` — password reset flow via email token.
6. `GET /api/v1/auth/verify-email` — email address verification via token.

### Authorization model
- Users have a `Set<Role>` (many-to-many via `user_roles`). Spring Security grants all roles from that set.
- Role names are stored as `ROLE_*` (e.g. `ROLE_ADMIN`). The service layer normalizes bare names automatically.
- Roles in use: `ADMIN`, `HIGH_COMMITTEE`, `EQUIPMENT_COMMITTEE`, `EVENT_COMMITTEE`, `STUDENT`, `NON_STUDENT`.

### Route authorization summary

| Path pattern | Method | Required role(s) |
|---|---|---|
| `/api/v1/auth/**`, `/api/v1/register` | any | public |
| `/swagger-ui/**`, `/v3/api-docs/**` | any | public |
| `/api/v1/users/**` | any | `ADMIN` or `HIGH_COMMITTEE` |
| `/api/v1/rental-pricing/**` | GET | public |
| `/api/v1/rental-pricing/**` | PUT | `EQUIPMENT_COMMITTEE` |
| `/api/v1/equipment/**` | POST/PUT/DELETE | `EQUIPMENT_COMMITTEE` |
| `/api/v1/equipment/**` | GET | authenticated |
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
| `/api/v1/event-equipment-requests` | POST | `EVENT_COMMITTEE` or `HIGH_COMMITTEE` |
| `/api/v1/event-equipment-requests/event/**` | GET | `EVENT_COMMITTEE` or `HIGH_COMMITTEE` |
| `/api/v1/event-equipment-requests/**/review` | PATCH | `EQUIPMENT_COMMITTEE` |
| `/api/v1/event-equipment-requests/trigger-active` | POST | `EQUIPMENT_COMMITTEE` |
| `/api/v1/reports/**` | GET | `EQUIPMENT_COMMITTEE` or `HIGH_COMMITTEE` |
| `/api/v1/payments/callback` | POST | public (Billplz webhook) |
| `/api/v1/payments/result` | GET | public (Billplz redirect) |
| `/api/v1/receipts/**` | GET | authenticated; service enforces owner-or-`EQUIPMENT_COMMITTEE` per rental |

### Key models
- `User`, `Role`, `RefreshToken`, `PasswordResetToken`, `EmailVerificationToken`
- `MainEquipment`, `MainEquipmentStatus`, `SubEquipment`, `SubEquipmentQuantityHold` — equipment catalogue with status tracking and quantity reservation
- `EquipmentRental`, `EquipmentRentalItem`, `EquipmentRentalSubItem` — rental orders with line items and sub-items
- `Event`, `EventEquipmentRequest`, `EventEquipmentRequestItem`, `EventEquipmentRequestSubItem` — club events and their equipment requests
- `RentalCategory`, `RentalPricing` — pricing tiers (e.g. member vs non-member rates)
- `Payment`, `Receipt` — Billplz payment records and generated receipts (invoices + receipts, with `DocumentType`)

**Enumerators:** `RentalStatus`, `RentalPaymentMethod`, `RentalPaymentStatus`, `RentalPricingCategory`, `EventEquipmentRequestStatus`, `MainEquipmentStatusType`, `PaymentType`, `PaymentRecordStatus`, `MemberType`, `DocumentType`

### Rental status lifecycle
`PENDING_REVIEW` → `APPROVED` / `REJECTED` → `PENDING_PAYMENT` → `PAID` → `ACTIVE` → `RETURNED`  
Side paths: `CANCELLED` (anytime before ACTIVE), `OVERDUE` (from ACTIVE when due date passes)

### Event equipment request lifecycle
`PENDING_REVIEW` → `APPROVED` / `REJECTED` → `ACTIVE` (auto, when start date reached) → `RETURNED`  
Side path: `CANCELLED`

### SSE notification flow
`ReceiptService.buildAndSave()` publishes a `ReceiptReadyEvent` after each document is persisted. `RentalNotificationService` listens via `@Async @TransactionalEventListener(AFTER_COMMIT)` and pushes a `receipt-ready` SSE event to all active emitters for that rental. Emitters are registered when the frontend calls `GET /api/v1/receipts/events/rental/{rentalId}` and cleaned up on timeout (3 min), completion, or error.

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

### Receipt endpoints (`/api/v1/receipts/`)
- `GET /invoice/rental/{rentalId}` — invoice for an approved rental (created on approval)
- `GET /receipt/rental/{rentalId}` — receipt for a paid rental (created on payment confirmation)
- `GET /overdue-invoice/rental/{rentalId}` — penalty invoice (created when rental returned late)
- `GET /overdue-receipt/rental/{rentalId}` — penalty receipt (created on penalty payment confirmation)
- `GET /events/rental/{rentalId}` — SSE stream (`text/event-stream`); pushes `receipt-ready` event each time a document is created. Use `fetch()` with `Authorization` header (native `EventSource` does not support custom headers). Server closes stream after 3 minutes; reconnect if needed. Existing documents are pushed immediately on subscribe (catch-up).

### Database migrations
Flyway migrations live in `src/main/resources/db/migration/` using the `V{n}__description.sql` naming convention. Never modify existing migration files — always add a new version. Latest migration: `V5__seed_data.sql`.

### Profiles
- `dev` (default) — verbose SQL logging, CORS allows `localhost:5173`, `localhost:3000`, `127.0.0.1:5173`.
- `prod` — configure via `application-prod.properties`; CORS restricted to `https://ifoto-frontend.vercel.app/`, SQL logging disabled, API docs disabled.
- `test` — `application-test.properties`; CORS allows `https://staging-ifoto-frontend.vercel.app`.
