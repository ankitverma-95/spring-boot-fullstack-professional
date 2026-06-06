
---

# Construction HRMS Backend

## Forked Repository Choice
We forked the **SpringBoot Employee Management System** (https://github.com/amigoscode/spring-boot-fullstack-professional) because of its lightweight, modular architecture and clean JPA starter configuration.

## AI Tools Used
We used **Antigravity** (Google DeepMind's agentic coding assistant) for:
- Codebase structure exploration and architecture planning.
- Structuring JPA entities, validation annotations, and partial database unique constraints.
- Implementing transaction-safe event publishers and post-commit listeners.
- Configuring Lettuce connection timeouts, Redis template serializers, and robust cache fallback error handlers.
- Structuring atomic git commits for clean history.

## Design Decisions & Tradeoffs

### 1. Database-Level Constraints vs. Java Logic
- **Tradeoff**: Traditional validation checks in Java are susceptible to race conditions.
- **Decision**: We implemented a partial unique index `CREATE UNIQUE INDEX idx_active_clock_in ON attendance_logs (worker_id) WHERE clock_out IS NULL;` in PostgreSQL. This ensures at the database engine level that no worker can have multiple concurrent active clock-ins, ensuring absolute data integrity under high traffic.

### 2. Caching Strategy and Robustness
- **Decision**: Active workers are cached under individual keys `active_worker:{workerId}` with a 16-hour TTL.
- **Degradation**: If Redis goes offline, a custom `CacheErrorHandler` intercepts Lettuce connection failures, logs a warning, and falls back to fetching active workers directly from the database (`clock_out IS NULL`), keeping the site running without downtime.
- **Profile Segregation**: Added environment-specific configs (`application.yml` and `application-staging.yml`) to tune HikariCP limits, keepalive queries (every 30s), and connection lifetimes (5 mins) specifically tailored to survive Supabase's idle connection drops.

### 3. If We Had More Time...
- **Historical Wages**: A worker's daily wage rate is currently read directly from their profile. If a worker's wage changes, historical overtime entry amounts won't reflect the rate at the time of the shift. We would introduce a `worker_wage_rates` history table.
- **Message Broker**: Currently, SMS dispatches use Spring's internal `ApplicationEventPublisher`. For production, we'd replace this with a broker (RabbitMQ/Kafka) to support persistent queues, retries, and rate limiting.
- **Database Migrations**: We would use **Flyway** or **Liquibase** instead of Hibernate's `ddl-auto: update` to strictly version database schemas across local, staging, and production.

---

## Local Setup & Run Instructions

### Prerequisites
- **Java 17** or higher
- **Local Redis** (Default port `6379`. If Redis is offline, caching is skipped and database fallback kicks in automatically).
- **Supabase Account** (For database hosting).

### Supabase Connection Setup
1. Create a project at [supabase.com](https://supabase.com).
2. Go to **Project Settings** -> **Database**.
3. Under **Connection Pooler**, copy the **Transaction Mode** connection string (ensure the port is `6543` and pgBouncer is enabled).
4. Update the connection string in `src/main/resources/application.yml` or `src/main/resources/application-staging.yml`:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://<project-ref>.pooler.supabase.com:6543/postgres?user=<user>&password=<pass>
       username: <your-username>
       password: <your-password>
   ```

### Execution Commands
Run the following Maven commands in the project root directory:

- **Run Dev Profile (Local DB/Default Settings)**:
  ```bash
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
  ```
- **Run Staging Profile (Supabase Pooler & Optimized Pool)**:
  ```bash
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=staging
  ```
- **Execute Backend Unit Tests**:
  ```bash
  .\mvnw.cmd test
  ```



