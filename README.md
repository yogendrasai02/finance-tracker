# README

## Local setup

1. Docker running.
2. `docker compose up -d` — starts Postgres 18 on `localhost:5432`. First run also fires `db/init/01-roles-and-schema.sh`, creating schemas `app`/`auth` and roles `ft_migrator`/`ft_app`.
3. Copy `.env.example` to `.env`. Set `FT_OWNER_EMAIL` and `FT_OWNER_PASSWORD` (12+ chars).
4. Export `.env` into your shell — Spring Boot doesn't read it, only Docker Compose does. Run this command from the root directory:
   ```
   set -a && source .env && set +a
   ```
5. `cd backend && ./mvnw spring-boot:run` — runs the Flyway migrations, sets the owner's password (first run only), starts on `localhost:8080`.
6. `cd frontend && npm install && npm run dev` — Vite on `localhost:3000`, proxies `/api/**` to `:8080`.

## Seeded user

`V4__seed_data.sql` seeds one row in `app.users`: `owner@ft.local`, no password. Migrations can't ship a password hash (SR-40).

The password is set once — the first time the backend starts against a row with no password, using whatever `FT_OWNER_PASSWORD` is in the environment at that moment. After that, `.env` changes do nothing: the bootstrap never overwrites an existing hash, and login checks the email in the database, not `FT_OWNER_EMAIL`.

**Reset the password** (forgot it, or want a new one):
```sql
UPDATE app.users SET password_hash = NULL WHERE email = 'owner@ft.local';
```
Restart the backend to re-run the bootstrap.

**Change the login email** (no self-service endpoint yet):
```sql
UPDATE app.users SET email = 'new@email.com' WHERE email = 'owner@ft.local';
```

Run either as `ft_migrator` or the superuser — `ft_app` can't see the row, RLS blocks it.

## Database access (DBeaver)

Host `localhost`, port `5432`, database `financetracker`, driver PostgreSQL.

| Role | Password (default) | Sees |
| --- | --- | --- |
| `financetracker` | `financetracker_dev` | Everything (superuser). |
| `ft_migrator` | `ft_migrator_dev` | Everything (schema owner, bypasses RLS). |
| `ft_app` | `ft_app_dev` | Nothing by default — RLS blocks it until `app.user_id` is set in the session. |

Use `financetracker` or `ft_migrator` to browse data. Defaults come from `docker-compose.yml`; check `.env` for overrides.

## Backend tests

`./mvnw verify` in `backend/` — needs Docker, nothing else. Spins up its own throwaway Postgres via Testcontainers, runs every migration, tears it down. Doesn't touch the `docker compose` container.

## Docker cheatsheet

```
docker compose ps          # container status
docker compose up -d       # start, detached
docker compose logs -f     # follow Postgres logs
docker compose down        # stop; volume survives
docker compose down -v     # stop and wipe the volume (!!WARNING!!)
```

## Database roles

Created once by `db/init/01-roles-and-schema.sh`, on first container start against an empty data directory:

- `ft_migrator` — owns the `app` schema, runs Flyway migrations.
- `ft_app` — DML only, what the app connects as.

A change under `db/init` needs `docker compose down -v` to take effect.

## Production

Activate the profile with `SPRING_PROFILES_ACTIVE=prod`.
Every value `application-prod.yml` needs comes from the environment, with no default — see the production section of `.env.example`.

`ProductionEnvironmentGuard` checks the following before anything tries to open a database connection, and refuses to start otherwise:

- `DB_URL` includes `sslmode=verify-full` (SECURITY.md SR-36).
- `FT_OWNER_EMAIL` and `FT_OWNER_PASSWORD` are both set (SR-35, SR-40).

Actuator exposes `health` only, with `show-details: never`, so a public health check reveals nothing about the database or the disk.

CORS, static-asset serving, and the dependency CVE build gate are still open — see `plans/STATUS.md`.
