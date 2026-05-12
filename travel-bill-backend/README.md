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

Run from the repository root. Compose reads the root `.env` and starts MySQL, Redis, and the backend:

```bash
docker compose up -d --build
```

The backend image is `travel-aa-bill-backend:latest`, and the container name is `travel-aa-bill-backend`.

API:

```text
http://localhost:24975/api
```

MySQL is exposed on host port `24976`, Redis is exposed on host port `24977`, and the application container connects to the Docker service names `mysql` and `redis`.

The same root `.env` also works for running `TravelBillApplication` directly from IDEA. In Docker Compose, the backend service overrides only the internal MySQL and Redis host values.

The tables are created and migrated automatically on startup.

Default admin credentials are configured in the root `.env`. The startup bootstrap creates the admin user only when the username does not already exist, so an existing database password is not overwritten.
