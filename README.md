# README

## Sample docker commands

In reference to `docker-componse.yml`

```
docker compose ps          # list containers this compose file manages, with status
docker compose up -d       # start the container(s) defined here, detached (background) -> creates the network, starts the container
docker compose logs -f     # follow Postgres's own log output
docker compose down        # stop and remove the container; volume survives
```