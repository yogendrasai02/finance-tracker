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