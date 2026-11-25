# Java Spring Boot Production Readiness Rules

## 1. Database Migrations
- Set `spring.jpa.hibernate.ddl-auto` to `none` or `validate` in production (never `update`/`create`).
- Manage schema changes using versioned migration files (e.g. Flyway `.sql` scripts in `src/main/resources/db/migration/`).

## 2. Configuration & Profiles
- Separate environments using profiles (`application-dev.yml`, `application-prod.yml`).
- Never hardcode credentials. Inject via environment variables: `password: ${DATABASE_PASSWORD:default_dev_pass}`.
- Use `@ConfigurationProperties` + `@Validated` to check configurations at startup.

## 3. Monitoring (Spring Boot Actuator)
- Enable Actuator. Configure `/actuator/health` to check database/critical services.
- Secure sensitive endpoints. Publicly expose only safe ones:
  `management.endpoints.web.exposure.include: health,info,metrics`

## 4. API Stability
- Annotate controllers with OpenAPI/Swagger (`@Tag`, `@Operation`, `@ApiResponse`).
- Enable graceful shutdown: `server.shutdown: graceful`.

