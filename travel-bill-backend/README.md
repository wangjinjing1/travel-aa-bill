# Travel AA Bill

## Local run

The Spring Boot app serves both the API and the responsive Web frontend. It listens on port `24975` by default.

```bash
mvn spring-boot:run
```

Open:

```text
http://localhost:24975/
```

## Docker deployment

Prepare external MySQL and Redis first, then update the root `.env`. Run from the repository root:

```bash
docker compose up -d --build
```

Compose starts only the backend service. The backend image is `travel-aa-bill-backend:latest`, and the container name is `travel-aa-bill-backend`.

API:

```text
http://localhost:24975/api
```

MySQL and Redis are external services configured in the root `.env`.

The tables are created and migrated automatically on startup.

Default admin credentials are configured in the root `.env`. The startup bootstrap creates the admin user only when the username does not already exist, so an existing database password is not overwritten.
