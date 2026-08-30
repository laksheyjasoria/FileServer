
# FileServer Development Guide

This guide helps developers configure, build, test, debug, and extend FileServer.

## Requirements

Install:

* Java 17+
* Maven 3.8+
* Git
* PostgreSQL
* Redis (optional)
* Docker (optional)
* IntelliJ IDEA, VS Code, or another Java IDE

## Clone Repository

```bash
git clone https://github.com/laksheyjasoria/FileServer.git
cd FileServer
```

## Build

```bash
mvn clean install
```

## Run

```bash
mvn spring-boot:run
```

## Project Structure

```text
src/
â”œâ”€â”€ main/
â”‚   â”œâ”€â”€ java/
â”‚   â”‚   â””â”€â”€ com/app/
â”‚   â”‚       â”œâ”€â”€ billing/
â”‚   â”‚       â”œâ”€â”€ config/
â”‚   â”‚       â”œâ”€â”€ core/
â”‚   â”‚       â”œâ”€â”€ drive/
â”‚   â”‚       â”œâ”€â”€ email/
â”‚   â”‚       â”œâ”€â”€ identity/
â”‚   â”‚       â”œâ”€â”€ logger/
â”‚   â”‚       â”œâ”€â”€ master/
â”‚   â”‚       â”œâ”€â”€ orchestrator/
â”‚   â”‚       â”œâ”€â”€ resource/
â”‚   â”‚       â””â”€â”€ scheduler/
â”‚   â”‚
â”‚   â””â”€â”€ resources/
â”‚       â”œâ”€â”€ static/
â”‚       â”œâ”€â”€ templates/
â”‚       â”œâ”€â”€ db/
â”‚       â”‚   â””â”€â”€ migration/
â”‚       â””â”€â”€ application.yml
â”‚
â””â”€â”€ test/
    â””â”€â”€ java/
```

## Application Architecture

The application follows a layered architecture:

```text
Controller
    |
    v
Orchestrator
    |
    v
Service
    |
    v
Repository
    |
    v
Database
```

### Controller

Responsible for:

* HTTP requests
* Request validation
* Authentication context
* HTTP responses

Controllers should remain thin.

### Orchestrator

Responsible for:

* Coordinating workflows
* Combining multiple services
* Managing complex application flows

### Service

Responsible for:

* Business logic
* File operations
* Authentication
* User management
* Logger management
* Billing

### Repository

Responsible for:

* Database persistence
* Queries
* JPA operations

## Database

PostgreSQL is the primary persistence layer.

Major conceptual areas:

| Area    | Purpose              |
| ------- | -------------------- |
| Users   | User accounts        |
| Files   | Files and folders    |
| Shares  | Shared files         |
| Billing | Usage and billing    |
| Trash   | Deleted items        |
| Logger  | Logger configuration |

## Flyway

Migrations are located at:

```text
src/main/resources/db/migration
```

Naming convention:

```text
V1__initial_schema.sql
V2__add_file_share.sql
V3__add_logger_configuration.sql
```

Never modify an already-applied production migration.

Create a new migration.

## Testing

All tests:

```bash
mvn test
```

Specific test:

```bash
mvn test -Dtest=UserServiceTest
```

Integration tests:

```bash
mvn test -Dtest=*IntegrationTest
```

Build without tests:

```bash
mvn clean package -DskipTests
```

## Code Style

Use:

* Java 17
* 4 spaces
* Constructor injection
* Meaningful names
* Small focused methods
* Proper exception handling
* DTOs at API boundaries

Avoid:

* Hardcoded secrets
* Business logic in controllers
* Sensitive logging
* Unnecessary global state
* Large monolithic classes

## Logging

Use the project's logging abstraction.

Example:

```java
logger.info("File uploaded successfully: {}", fileId);
```

Never log:

* Passwords
* JWT tokens
* Master keys
* Telegram tokens
* SMTP passwords
* Sensitive user information

## Logger Management

FileServer supports configurable logger management.

Supported levels may include:

```text
ERROR
WARN
INFO
DEBUG
TRACE
```

When adding logging:

1. Use the project's logging system.
2. Use meaningful logger names.
3. Select appropriate log levels.
4. Avoid excessive logs.
5. Never expose secrets.

## File Operations

File operations must consider:

* Ownership
* Authorization
* Path traversal
* Duplicate names
* Storage limits
* Trash state
* Parent folder validity
* Large files
* Resumable uploads

## Security Development

Protected APIs use JWT:

```http
Authorization: Bearer <JWT_TOKEN>
```

Secrets must be provided through environment variables or secure configuration.

Important values include:

```text
APP_SECURITY_JWT_SECRET
APP_SECURITY_MASTER_KEY
```

## Adding an API Endpoint

1. Create request DTO.
2. Create response DTO.
3. Add controller endpoint.
4. Add orchestrator logic if required.
5. Implement service logic.
6. Add repository changes if required.
7. Add validation.
8. Add tests.
9. Update API documentation.

## Adding an Entity

1. Create entity.
2. Create repository.
3. Create service.
4. Create DTO.
5. Create Flyway migration.
6. Add tests.
7. Update documentation.

## Frontend Development

Frontend files are located under:

```text
src/main/resources/static
```

Templates are under:

```text
src/main/resources/templates
```

When implementing a UI feature:

1. Update HTML.
2. Update CSS.
3. Update JavaScript.
4. Connect the API.
5. Handle loading states.
6. Handle errors.
7. Verify authentication.
8. Test browser refresh.
9. Test navigation.
10. Test responsive behavior.

## Debugging

Development logging:

```yaml
logging:
  level:
    com.app: DEBUG
    org.springframework.web: DEBUG
```

Remote debugging:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

## Common Problems

### Port Already in Use

Change:

```yaml
server:
  port: 8081
```

### Database Connection Refused

Check:

* PostgreSQL is running
* JDBC URL
* Username
* Password
* Port

### Redis Connection Error

Check whether Redis is enabled and whether Redis is running.

### File Upload Failure

Check:

* Multipart configuration
* Maximum file size
* Disk permissions
* Disk space
* Upload directory

### Authentication Failure

Check:

* JWT secret
* Token expiration
* Authorization header
* User status
* Spring Security configuration

## Useful Maven Commands

Build:

```bash
mvn clean install
```

Test:

```bash
mvn test
```

Skip tests:

```bash
mvn clean install -DskipTests
```

Run:

```bash
mvn spring-boot:run
```

Dependency tree:

```bash
mvn dependency:tree
```

## Production Build

```bash
mvn clean package -DskipTests
```

Docker:

```bash
docker build -t fileserver:prod .
```

## Pull Request Checklist

Before creating a Pull Request:

* Code compiles
* Tests pass
* No secrets committed
* API documentation updated
* Flyway migration added if required
* Sensitive data is not logged
* UI tested
* Authentication tested
* Error handling tested
* Existing functionality verified

## Getting Help

Review:

* GitHub Issues
* Existing documentation
* API documentation
* Deployment guide
* Development guide

Create a GitHub Issue when a problem cannot be resolved through existing documentation.
