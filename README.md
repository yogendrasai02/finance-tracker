# README

## Running backend tests

`./mvnw verify` in `backend/` needs Docker running, and nothing else.
The schema tests start their own throwaway `postgres:18-alpine` container via Testcontainers, apply the four Flyway migrations, and tear it down at the end of the run.
A hand-started container from `docker compose up` is not required and is not used — the tests open their own connections to their own container.

## Sample docker commands

In reference to `docker-componse.yml`

```
docker compose ps          # list containers this compose file manages, with status
docker compose up -d       # start the container(s) defined here, detached (background) -> creates the network, starts the container
docker compose logs -f     # follow Postgres's own log output
docker compose down        # stop and remove the container; volume survives
```

## Database roles

`db/init/01-roles-and-schema.sh` creates two Postgres roles on first container start:
`ft_migrator` (owns the `app` schema, runs Flyway migrations) and `ft_app` (DML only — what the
application connects as). It only runs once, against an empty data directory, so a change under
`db/init` needs `docker compose down -v` before it takes effect again.

## Working with the app locally

### Docker: Postgres 18 in a container

Docker must be running.

Set values in `.env` file, it needs two values: `FT_OWNER_EMAIL` and `FT_OWNER_PASSWORD` (atleast 12 chars). Without these, the app boots BUT no one can login since the seeded user has no password.

Run this command to start Postgres 18 in a container: `docker compose up -d`

On the first ever start, `db/init/01-roles-and-schema.sh` is run and it creates the `app`, `auth` schemas as well as the 2 DB roles:  `ft_migrator`, `ft_app`

**Note:** Postgres runs at `localhost:5173`

### Running backend

`.env` is only auto-read by Docker Compose, for the Postgres container. Spring Boot does not read it.

`FT_OWNER_EMAIL` and `FT_OWNER_PASSWORD` must be valid environment variables in the shell that runs `./mvnw`, or the app starts with a warning and nobody can log in.
Export them first (not from the `backend` directory, these needs to be run from the root):

```
set -a
source .env
set +a
```

Or as a single command: `set -a && source .env && set +a`

Then, `cd` onto `backend` directory, and run `./mvnw spring-boot:run` which does 3 main things:

1. runs the flyway migrations as `ft_migrator` to build the db schema
2. sets the owner's password from the environment, the first time it runs
3. starts spring boot app at `http://localhost:8080`

Verify these logs for #1:
```
2026-09-14T15:44:24.552+05:30  INFO 28995 --- [backend-local] [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5432/financetracker?currentSchema=app (PostgreSQL 18.6)
2026-09-14T15:44:24.592+05:30  INFO 28995 --- [backend-local] [           main] o.f.core.internal.command.DbValidate     : Successfully validated 6 migrations (execution time 00:00.015s)
2026-09-14T15:44:24.615+05:30  INFO 28995 --- [backend-local] [           main] o.f.core.internal.command.DbMigrate      : Current version of schema "app": 6
2026-09-14T15:44:24.616+05:30  INFO 28995 --- [backend-local] [           main] o.f.core.internal.command.DbMigrate      : Schema "app" is up to date. No migration necessary.
```

Verify these logs for #2: `c.f.auth.OwnerCredentialBootstrap        : Owner credential already present, leaving it unchanged`

### Running the frontend

`cd` onto `frontend` directory, run `npm install` to keep the deps upto (and for the first time as well).

Then run `npm run dev` which starts Vite at `http://localhost:3000`

Any request to `/api/**` is proxied to the backend at port 8080.

### Accessing the database

To use DBeaver, use the below config:

- Host: `localhost`
- Port: `5432`
- Database: `financetracker`
- Driver: PostgreSQL
- Username & Password auth: `finanacetracker` is the username, `financetracker_dev` is the password (see 01-roles-and-schema.sh file)