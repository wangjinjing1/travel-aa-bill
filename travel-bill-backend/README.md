# Travel Bill Backend

## Local run

The backend listens on port `24975` by default.

```bash
mvn spring-boot:run
```

## Docker deployment

Start MySQL and the backend:

```bash
docker compose up -d --build
```

The backend image is `travel-bill-backend:latest`, and the container name is `travel-bill-backend`.

Backend API:

```text
http://localhost:24975/api
```

MySQL is exposed on host port `24976`, and the application container connects to the Docker service name `mysql`.

The tables are created automatically by Spring JPA on startup.
