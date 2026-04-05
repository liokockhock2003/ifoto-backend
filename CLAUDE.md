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
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP for password-reset emails |

## Architecture

### Layers
- **controller** — `@RestController` classes under `/api/v1/`. `AuthController` handles all auth flows; `UserController` handles admin user management.
- **service** — Business logic. `UserService` owns user CRUD, role management, and password hashing. `RefreshTokenService` and `PasswordResetTokenService` own their respective token lifecycles.
- **repository** — Spring Data JPA interfaces backed by MySQL.
- **model** — JPA entities (`User`, `Role`, `RefreshToken`, `PasswordResetToken`). Schema is managed exclusively by Flyway — Hibernate DDL is disabled (`ddl-auto=none`).
- **security** — `JwtUtil` (token creation/validation), `JwtAuthenticationFilter` (per-request token extraction), `CookieUtil` (HttpOnly refresh-token cookie helpers).
- **config** — `SecurityConfig` (filter chain, route authorization), `AppConfig` (BCrypt strength 12, `AuthenticationManager`), `WebConfig` (CORS origins from `app.cors.allowed-origins`).
- **dto/UserDTO** — Record-based request/response objects; never pass raw entities over HTTP.

### Auth flow
1. `POST /api/v1/auth/login` → returns short-lived JWT access token in body + long-lived refresh token in HttpOnly cookie.
2. Access token is sent as `Authorization: Bearer <token>` on subsequent requests.
3. `POST /api/v1/auth/refresh` → reads cookie, validates against DB (`refresh_tokens` table), issues new access token.
4. `POST /api/v1/auth/logout` → revokes DB entry, clears cookie.

### Authorization model
- Users have a `Set<Role>` (many-to-many via `user_roles`) but Spring Security authorities are derived **only** from `User.activeRole`.
- Role names are stored as `ROLE_*` (e.g. `ROLE_ADMIN`, `ROLE_GUEST`). The service layer normalizes bare names automatically.
- Route rules: `/api/v1/auth/**` and `/api/v1/register` are public. `PATCH /api/v1/users/{username}/active-role` requires any authenticated user. All other `/api/v1/users/**` require `ROLE_ADMIN`.

### Database migrations
Flyway migrations live in `src/main/resources/db/migration/` using the `V{n}__description.sql` naming convention. Never modify existing migration files — always add a new version.

### Profiles
- `dev` (default) — verbose SQL logging, CORS allows `localhost:5173` and `localhost:3000`.
- `prod` — configure via `application-prod.properties`; CORS and DB sourced from env vars.
- `test` — `application-test.properties`.
