---
name: start-postgres-docker
description: Start a PostgreSQL Docker container for the FinanceTracker application. Runs the postgres service defined in docker-compose.yml.
triggers:
  - "start postgres"
  - "start postgres docker"
  - "start pg docker"
  - "start database"
  - "docker postgres"
applyTo: ""
---

# Start PostgreSQL Docker Container

## Purpose

Start a PostgreSQL Docker container for local development using docker-compose.

## Prerequisites

- Docker Desktop installed and running
- `docker-compose.yml` exists in the project root
- No other service is using the PostgreSQL port (default: 5432)

## Instructions

Run the following command to start the PostgreSQL service:

```bash
docker compose up -d
```

This command:
- Starts all services defined in `docker-compose.yml` in detached mode (background)
- Creates the network if it doesn't exist
- Preserves any existing data through volumes
- Applies environment variables from `.env` (if present)

## Verify Container Is Running

Check if the container is running:

```bash
docker compose ps
```

## Stop the Container

When done, stop the container:

```bash
docker compose down
```

This stops and removes the containers but preserves volumes (data persists).

## Common Issues

### Port Already in Use

If port 5432 is already in use:

1. Find the process: `lsof -i :5432`
2. Kill it or change the PostgreSQL port in `docker-compose.yml`
3. Update database connection strings accordingly

### Container Won't Start

Check logs:
```bash
docker compose logs
```

### Connect to PostgreSQL

Once running, connect with `psql` or your IDE/client using:
- **Host**: localhost
- **Port**: 5432
- **Database**: (check docker-compose.yml)
- **Username**: (check docker-compose.yml or .env)
- **Password**: (check docker-compose.yml or .env)

## Related Commands

- `docker compose logs -f` — Stream PostgreSQL logs
- `docker compose exec postgres psql -U <user>` — Open psql shell inside container
- `docker compose pull` — Update to latest images
