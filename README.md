[![CICD](https://github.com/amigoscode/spring-boot-fullstack-professional/actions/workflows/deploy.yml/badge.svg?branch=main)](https://github.com/amigoscode/spring-boot-fullstack-professional/actions/workflows/deploy.yml)

https://amigoscode.com/p/full-stack-spring-boot-react

![Cover](https://user-images.githubusercontent.com/40702606/111074799-bdfbcf00-84dc-11eb-98c0-d40a99aa0da7.png)

# Course Description
Spring Boot allows to take an idea/prototype and turn it into a real thing in matters minutes hours of months and years. A lot of companies use Spring Boot because it's easy to setup, learn and write code very fast without having to setup the low level platform code. Recently, Netflix has decided to switch their entire backend to Spring Boot. This shows that Spring Boot is a must if you are or want to become a software engineer in the Java world.
This course teaches how to build a full stack application from the ground up and touches on very import concepts used in real live software development. Concepts such as:

- Spring Boot Backend API
- Frontend with React.js Hooks and Functions Components
- Maven Build Tool
- Databases using Postgres on Docker
- Spring Data JPA
- Server and Client Side Error Handling
- Packaging applications for deployment using Docker and Jib
- AWS RDS & Elastic Beanstalk
- Software Deployment Automation with Github Actions
- Software Deployment Monitoring with Slack
- Unit and Integration Testing

This course focus on teaching you the process needed to build your own apps and deploy to real users using real software development techniques and skills. The skills gained at the end of this can be applied immediately on your own projects, university projects and at your work place.

Have you got what it takes to become a professional software engineer? Cool I'll see you inside. https://amigoscode.com/p/full-stack-spring-boot-react

![Screenshot 2021-03-11 at 22 56 19](https://user-images.githubusercontent.com/40702606/111074929-5003d780-84dd-11eb-8284-e7c92c7e2905.png)


<img width="773" alt="Screenshot 2021-03-12 at 20 48 48" src="https://user-images.githubusercontent.com/40702606/111074947-627e1100-84dd-11eb-9d3f-85fdbf23e290.png">

---

# Construction HRMS Backend

## Supabase Connection Setup (LF-205)

When running the application in a Staging or Production environment connected to Supabase, you must use the **connection pooler** URL instead of the direct database connection.

### Connection Parameters
- **Direct Connection Port**: `5432` (Avoid in high concurrency environments. Supabase has a low direct connection limit on free tier, which results in `SQLTransientConnectionException: Connection is not available` or socket drops).
- **Connection Pooler Port**: `6543` (Uses **PgBouncer** in transaction mode. Highly recommended for Spring Boot and HikariCP).
- **Datasource URL Format**: `jdbc:postgresql://<project-ref>.pooler.supabase.com:6543/postgres?user=<user>&password=<pass>`

### HikariCP Connection Pool Configurations
To prevent Supabase from silently dropping idle connections and to avoid connection pool exhaustion under moderate traffic, the following pooling rules are implemented in `application-staging.yml`:

- `spring.datasource.hikari.maximum-pool-size=20`: Right-sized to handle up to 20+ concurrent users without latency freezes.
- `spring.datasource.hikari.max-lifetime=300000`: Sets connection max lifetime to 5 minutes (300 seconds), which is shorter than Supabase's firewall idle timeout, ensuring dead connections are retired and replaced automatically.
- `spring.datasource.hikari.keepalive-time=30000`: Configures Hikari to send a keepalive query every 30 seconds to keep connections warm and active.
- `spring.datasource.hikari.connection-timeout=30000`: Allows up to 30 seconds to acquire a connection before throwing a timeout exception.

## Run Configuration
- **Local Dev Profile**: Starts with default Hikari connection pool configuration.
  ```bash
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev -P!build-frontend
  ```
- **Staging Profile**: Uses Supabase PgBouncer pooler and optimized Hikari settings.
  ```bash
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=staging -P!build-frontend
  ```


