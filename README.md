# iFoto Backend

REST API backend for the iFoto photography club equipment rental and event management system. Built with Spring Boot 3.5.11 and Java 21.

## Overview

iFoto Backend manages the full lifecycle of equipment rentals (students borrowing cameras, lenses, and accessories) and event equipment requests (event committee borrowing gear for club events). It handles multi-role authorization, payment processing via Billplz, receipt/invoice generation, email notifications, and real-time SSE updates.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5.11 |
| Database | MySQL + Flyway migrations |
| ORM | Spring Data JPA (Hibernate) |
| Security | Spring Security + JWT (HS256, jjwt 0.12.6) |
| Email | Resend via SMTP bridge (`smtp.resend.com:587`) |
| Payments | Billplz (online, cash, bank transfer) |
| Rate limiting | Bucket4j + Caffeine |
| API docs | SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`) |

## Prerequisites

- Java 21
- Maven (or use the included `./mvnw` wrapper)
- MySQL 8+
- (Optional) Docker for local email testing via Mailpit

## Getting Started

### 1. Clone and copy environment template

```bash
cp .env.example .env
```

Edit `.env` and fill in all required values (see Environment Variables section below).

### 2. Source the env file

```bash
set -a && source .env && set +a
```

### 3. Run database migrations

```bash
./mvnw flyway:migrate
```

### 4. Start the server

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Local email testing

```bash
docker run -p 1025:1025 -p 8025:8025 axllent/mailpit
```

Then set these env vars:
```
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false
```

Mailpit UI: `http://localhost:8025`

## Environment Variables

### Required for `dev` profile

| Variable | Example | Purpose |
|---|---|---|
| `DEV_DB_URL` | `jdbc:mysql://localhost:3306/ifoto_dev` | JDBC URL |
| `DEV_DB_USER` | `root` | DB username |
| `DEV_DB_PASS` | `secret` | DB password |
| `JWT_SECRET` | ≥32-char string | HS256 signing key |
| `JWT_EXPIRATION_MS` | `900000` | Access token TTL (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` | Refresh token TTL (7 days) |
| `MAIL_HOST` | `smtp.resend.com` | SMTP host |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | `resend` | SMTP username |
| `MAIL_PASSWORD` | `re_...` | Resend API key |
| `MAIL_SMTP_AUTH` | `true` | Enable SMTP auth |
| `MAIL_SMTP_STARTTLS` | `true` | Enable STARTTLS |
| `MAIL_SMTP_SSL` | `false` | Enable SSL (port 465 only) |
| `APP_MAIL_FROM` | `noreply@example.com` | Sender address |
| `APP_MAIL_APP_NAME` | `iFoto` | Sender display name |
| `BILLPLZ_API_KEY` | | Billplz API key |
| `BILLPLZ_COLLECTION_ID` | | Billplz collection ID |
| `BILLPLZ_X_SIGNATURE_KEY` | | Webhook HMAC key |
| `BILLPLZ_CALLBACK_URL` | | Billplz webhook URL |
| `BILLPLZ_REDIRECT_URL` | | Post-payment redirect URL |
| `BILLPLZ_BASE_URL` | `https://www.billplz-sandbox.com/api/v3` | API base (sandbox default) |
| `PORT` | `8080` | Server port |

### Optional / defaults

| Variable | Default | Purpose |
|---|---|---|
| `APP_PASSWORD_RESET_TOKEN_EXPIRATION_MS` | `900000` | Password reset token TTL |
| `APP_PASSWORD_RESET_URL_BASE` | `http://localhost:5173/reset-password` | Frontend reset URL |
| `EMAIL_VERIFY_TOKEN_EXPIRY_MS` | `86400000` | Email verification TTL |
| `EMAIL_VERIFY_URL_BASE` | `http://localhost:5173/verify-email` | Frontend verify URL |

## Build & Test

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=IfotoBackendApplicationTests

# Flyway info / repair
./mvnw flyway:info
./mvnw flyway:repair
```

## API Reference

Base path: `/api/v1`  
Swagger UI (dev only): `/swagger-ui.html`

### Authentication

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/register` | Register new user | Public |
| POST | `/auth/login` | Login → access token (body) + refresh token (cookie) | Public |
| POST | `/auth/refresh` | Refresh access token using cookie | Public |
| POST | `/auth/logout` | Revoke refresh token, clear cookie | Public |
| POST | `/auth/forgot-password` | Send password reset email | Public |
| POST | `/auth/reset-password` | Reset password with token | Public |
| GET | `/auth/verify-email?token=` | Verify email address | Public |

All protected endpoints require `Authorization: Bearer <access_token>`.

### Equipment (`/api/v1/equipment`)

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/` | List all equipment (filterable by date range) | Authenticated |
| GET | `/available` | Available equipment for a date range (rental or event context) | Authenticated |
| POST | `/main` | Create main equipment | `EQUIPMENT_COMMITTEE` |
| PUT | `/main/{id}` | Update main equipment | `EQUIPMENT_COMMITTEE` |
| DELETE | `/main/{id}` | Delete main equipment | `EQUIPMENT_COMMITTEE` |
| POST | `/main/{id}/statuses` | Add status record | `EQUIPMENT_COMMITTEE` |
| GET | `/main/{id}/statuses` | List status records | Authenticated |
| PUT | `/main/{id}/statuses/{statusId}` | Update status | `EQUIPMENT_COMMITTEE` |
| DELETE | `/main/{id}/statuses/{statusId}` | Delete status | `EQUIPMENT_COMMITTEE` |
| POST | `/sub` | Create sub-equipment | `EQUIPMENT_COMMITTEE` |
| PUT | `/sub/{id}` | Update sub-equipment | `EQUIPMENT_COMMITTEE` |
| DELETE | `/sub/{id}` | Delete sub-equipment | `EQUIPMENT_COMMITTEE` |
| POST | `/sub/{id}/quantity-holds` | Add quantity hold | `EQUIPMENT_COMMITTEE` |
| GET | `/sub/{id}/quantity-holds` | List quantity holds | Authenticated |
| PUT | `/sub/{id}/quantity-holds/{holdId}` | Update quantity hold | `EQUIPMENT_COMMITTEE` |
| DELETE | `/sub/{id}/quantity-holds/{holdId}` | Delete quantity hold | `EQUIPMENT_COMMITTEE` |

### Equipment Rentals (`/api/v1/rentals`)

**Renter endpoints** (`STUDENT` or `NON_STUDENT`):

| Method | Path | Description |
|---|---|---|
| POST | `/` | Submit rental request |
| GET | `/my` | Get my rentals |
| POST | `/{id}/pay` | Initiate payment |
| DELETE | `/{id}` | Cancel rental |

**Committee endpoints** (`EQUIPMENT_COMMITTEE`):

| Method | Path | Description |
|---|---|---|
| GET | `/` | List all rentals (paginated, searchable) |
| GET | `/my-approvals` | Rentals awaiting my review |
| GET | `/equipment/{equipmentId}` | Rentals by main equipment |
| GET | `/sub-equipment/{subEquipmentId}` | Rentals by sub-equipment |
| GET | `/{id}/equipment-schedules` | Equipment booking schedules for a rental |
| PATCH | `/{id}/review` | Approve / reject |
| PATCH | `/{id}/mark-picked-up` | Mark equipment as picked up |
| PATCH | `/{id}/logistics` | Update pickup/return datetimes |
| PATCH | `/{id}/equipment` | Modify equipment selection |
| PATCH | `/{id}/confirm-manual-payment` | Confirm cash or bank transfer payment |
| PATCH | `/{id}/mark-active` | Manually mark rental as active |
| PATCH | `/{id}/mark-returned` | Mark as returned |
| POST | `/trigger-active` | Run scheduler: PAID → ACTIVE |
| POST | `/trigger-overdue` | Run scheduler: ACTIVE → OVERDUE |

### Rental Status Lifecycle

```
PENDING_REVIEW → APPROVED → PENDING_PAYMENT → PAID → (PICKED_UP) → ACTIVE → RETURNED
             ↘ REJECTED
                                                    ↘ OVERDUE (when due date passes)
CANCELLED — available at any point before ACTIVE
```

`PICKED_UP` sets the `picked_up_at` timestamp; the rental moves to `ACTIVE` when `program_start_date` is reached (scheduler or manual trigger).

### Events (`/api/v1/events`)

| Method | Path | Auth |
|---|---|---|
| GET | `/` | `HIGH_COMMITTEE` |
| GET | `/my` | `HIGH_COMMITTEE` |
| POST | `/` | `HIGH_COMMITTEE` |
| PUT | `/{id}` | `HIGH_COMMITTEE` |
| DELETE | `/{id}` | `HIGH_COMMITTEE` |

### Event Equipment Requests (`/api/v1/event-equipment-requests`)

**Event committee** (`EVENT_COMMITTEE` or `HIGH_COMMITTEE`):

| Method | Path | Description |
|---|---|---|
| POST | `/` | Submit equipment request for an event |
| GET | `/event/{eventId}` | Requests for an event |
| DELETE | `/{id}` | Cancel request |

**Equipment committee** (`EQUIPMENT_COMMITTEE`):

| Method | Path | Description |
|---|---|---|
| GET | `/` | List all requests (paginated, searchable) |
| GET | `/equipment/{equipmentId}` | Requests by main equipment |
| GET | `/sub-equipment/{subEquipmentId}` | Requests by sub-equipment |
| GET | `/{id}/equipment-schedules` | Booking schedules |
| PATCH | `/{id}/review` | Approve / reject |
| PATCH | `/{id}/mark-picked-up` | Mark as picked up |
| PATCH | `/{id}/logistics` | Update pickup/return datetimes |
| PATCH | `/{id}/equipment` | Modify equipment selection |
| PATCH | `/{id}/mark-returned` | Mark as returned |
| POST | `/trigger-active` | Run scheduler: APPROVED → ACTIVE |

### Event Equipment Request Lifecycle

```
PENDING_REVIEW → APPROVED → ACTIVE (auto when start_datetime reached) → RETURNED
             ↘ REJECTED
CANCELLED — available before ACTIVE
```

### Receipts (`/api/v1/receipts`)

| Method | Path | Auth |
|---|---|---|
| GET | `/invoice/rental/{rentalId}` | Invoice (created on approval) | Owner or `EQUIPMENT_COMMITTEE` |
| GET | `/receipt/rental/{rentalId}` | Receipt (created on payment) | Owner or `EQUIPMENT_COMMITTEE` |
| GET | `/overdue-invoice/rental/{rentalId}` | Penalty invoice | Owner or `EQUIPMENT_COMMITTEE` |
| GET | `/overdue-receipt/rental/{rentalId}` | Penalty receipt | Owner or `EQUIPMENT_COMMITTEE` |
| GET | `/events/rental/{rentalId}` | SSE stream — `receipt-ready` events | Owner or `EQUIPMENT_COMMITTEE` |

SSE note: use `fetch()` with `Authorization` header (native `EventSource` doesn't support custom headers). Server closes after 3 minutes; reconnect as needed. Existing documents are pushed immediately on subscribe.

### Payments (`/api/v1/payments`)

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/callback` | Billplz webhook (HMAC verified) | Public |
| GET | `/result` | Billplz post-payment redirect | Public |

### Rental Pricing (`/api/v1/rental-pricing`)

| Method | Path | Auth |
|---|---|---|
| GET | `/` | Public |
| PUT | `/bulk` | `EQUIPMENT_COMMITTEE` |

### Reports (`/api/v1/reports`)

All require `EQUIPMENT_COMMITTEE` or `HIGH_COMMITTEE`.

| Method | Path | Description |
|---|---|---|
| GET | `/kpi` | This month: total rentals, revenue, active, overdue |
| GET | `/rental-status` | Rental count by status |
| GET | `/rental-volume?months=12` | Monthly rental count (max 60 months) |
| GET | `/revenue?months=12` | Monthly base + penalty revenue |
| GET | `/equipment-utilization` | Rental count per sub-equipment |

### Users (`/api/v1/users`)

All require `ADMIN` or `HIGH_COMMITTEE`. Full CRUD + role management.

## Architecture

```
controller/       @RestController, maps HTTP → service
service/          Business logic; payment/ contains strategy pattern
  payment/        PaymentMethodHandler interface + Cash/Online/BankTransfer handlers
repository/       Spring Data JPA interfaces (19 repositories)
model/            JPA entities (Flyway-managed schema)
  enumerator/     11 enums (RentalStatus, PaymentType, DocumentType, ...)
  converter/      StringListConverter (List<String> ↔ JSON column)
security/         JwtUtil, JwtAuthenticationFilter, CookieUtil, RateLimitFilter
config/           SecurityConfig, AppConfig, WebConfig, BillplzConfig, GlobalExceptionHandler
scheduler/        RentalScheduler — midnight cron + @PostConstruct startup catch-up
validation/       @DateRangeValid, @DateTimeRangeValid, @SubEquipmentQuantityValid
exception/        TokenException (MISSING | INVALID | ALREADY_USED | EXPIRED)
event/            ReceiptReadyEvent → RentalNotificationService SSE push
dto/              Record-based request/response objects, never expose raw entities
```

### Key design decisions

- **Amounts in cents** — all monetary values stored as `int` cents; converted to `BigDecimal` only in `ReportingService` API responses.
- **SSE notification** — after each receipt/invoice is persisted (`ReceiptService.buildAndSave()`), a `ReceiptReadyEvent` is published and consumed `@Async @TransactionalEventListener(AFTER_COMMIT)` to push SSE to the client.
- **Availability buffer** — `app.rental.buffer-minutes=60` pads each rental window so equipment can't be double-booked across pickup/return logistics.
- **Email fire-and-forget** — all `MailService` methods are `@Async`; SMTP failure never blocks the caller.
- **Dev email redirect** — `app.mail.dev-override-recipient` in `application-dev.properties` redirects all outbound emails to a single address during development.

## Database Migrations

Flyway migrations in `src/main/resources/db/migration/` — **never modify existing files, always add a new version**.

| Version | File | Contents |
|---|---|---|
| V1 | `V1__schema_auth.sql` | `users`, `roles`, `user_roles`, tokens |
| V2 | `V2__schema_equipment.sql` | Equipment catalog, pricing |
| V3 | `V3__schema_events.sql` | Events, committee join table |
| V4 | `V4__schema_rentals.sql` | Rentals, payments, receipts, event requests |
| V5 | `V5__seed_data.sql` | Seed / test data |

## Roles

| Role | Who |
|---|---|
| `ADMIN` | System administrators |
| `HIGH_COMMITTEE` | Club leadership, full event access |
| `EQUIPMENT_COMMITTEE` | Manages equipment and reviews rentals |
| `EVENT_COMMITTEE` | Submits event equipment requests |
| `STUDENT` | Student members — can rent equipment |
| `NON_STUDENT` | Non-student members — can rent equipment |

Role names are stored as `ROLE_*` in the database; the service layer normalises bare names automatically.

## Profiles

| Profile | Trigger | Notes |
|---|---|---|
| `dev` | Default | SQL logging on, CORS: `localhost:5173/3000`, email redirect to override recipient |
| `prod` | `--spring.profiles.active=prod` | SQL off, API docs off, CORS: `ifoto-frontend.vercel.app` |
| `test` | `@ActiveProfiles("test")` | CORS: `staging-ifoto-frontend.vercel.app` |
