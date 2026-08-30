# FileServer

[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

A production-ready file server built with Spring Boot, providing secure file storage, user management, OAuth2 authentication, billing, logging, rate limiting, email notifications, and Telegram integration.

---

## 🚀 Features

* User registration and login
* JWT-based authentication
* Remember Me authentication
* Google OAuth2 login
* Email verification
* Forgot password and password reset
* User profile management
* File upload and download
* Chunked/resumable file upload
* File rename
* File move
* File search
* File sharing
* Folder creation and management
* Recycle Bin
* Restore deleted files and folders
* Permanent deletion
* Billing and storage usage tracking
* Logger Management UI
* Configurable logger levels
* Debug logger support
* Telegram file storage
* Telegram logging
* SMTP email integration
* Bucket4j rate limiting
* Redis support
* PostgreSQL persistence
* Flyway database migrations
* OpenAPI / Swagger documentation
* Docker support
* Docker Compose support

---

## 🛠️ Technology Stack

| Category                | Technology                       |
| ----------------------- | -------------------------------- |
| Backend                 | Spring Boot 3.2.4                |
| Language                | Java 17                          |
| Security                | Spring Security, JWT, OAuth2     |
| ORM                     | Spring Data JPA / Hibernate      |
| Database                | PostgreSQL                       |
| Cache / Session Support | Redis                            |
| Database Migration      | Flyway                           |
| File Storage            | Local Filesystem / Telegram      |
| Rate Limiting           | Bucket4j                         |
| Email                   | Spring Mail / SMTP               |
| API Documentation       | SpringDoc OpenAPI / Swagger      |
| Frontend                | HTML, CSS, JavaScript, Thymeleaf |
| Build Tool              | Maven                            |
| Containerization        | Docker / Docker Compose          |

---

## 📋 Prerequisites

Install the following before running the application:

* Java 17 or higher
* Maven 3.8+
* PostgreSQL 14+
* Redis (optional)
* Docker and Docker Compose (optional)
* SMTP account for email functionality
* Telegram bot configuration if Telegram features are enabled

---

## 📁 Project Structure

```text
FileServer/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/app/
│   │   │       ├── billing/
│   │   │       ├── config/
│   │   │       ├── core/
│   │   │       ├── drive/
│   │   │       ├── email/
│   │   │       ├── identity/
│   │   │       ├── logger/
│   │   │       ├── master/
│   │   │       ├── orchestrator/
│   │   │       ├── resource/
│   │   │       └── scheduler/
│   │   └── resources/
│   │       ├── static/
│   │       ├── templates/
│   │       ├── db/
│   │       │   └── migration/
│   │       └── application.yml
│   └── test/
│       └── java/
├── docs/
│   ├── api/
│   ├── deployment/
│   └── development/
├── Dockerfile
├── docker-compose.prod.yml
├── pom.xml
├── README.md
├── CONTRIBUTING.md
├── CHANGELOG.md
└── LICENSE
```

---

## ⚙️ Configuration

The application is configured through `application.yml`.

### PostgreSQL

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/fileserver
    username: fileserver
    password: fileserver123
```

### Redis

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

app:
  redis-enabled: false
```

Set `redis-enabled` to `true` when Redis functionality is required.

### File Upload

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2GB

app:
  upload:
    max-file-size: 2147483648
    chunk-size-mb: 50
    resumable: true
```

### Security

```yaml
app:
  security:
    master:
      key: ${APP_SECURITY_MASTER_KEY:your-master-key}

    jwt:
      secret: ${APP_SECURITY_JWT_SECRET:your-jwt-secret}
      access-token-validity: 43200000
      remember-me-validity: 604800000
```

### Admin User

```yaml
app:
  admin:
    email: ${APP_ADMIN_EMAIL:admin@example.com}
    password: ${APP_ADMIN_PASSWORD:change-me}
    name: ${APP_ADMIN_NAME:Admin}
```

### Telegram Storage

```yaml
app:
  telegram:
    storage:
      bot-token: ${TELEGRAM_STORAGE_BOT_TOKEN:your-bot-token}
      chat-id: ${TELEGRAM_STORAGE_CHAT_ID:your-chat-id}
```

### Telegram Logger

```yaml
app:
  telegram:
    logger:
      enabled: true
      bot-token: ${TELEGRAM_LOGGER_BOT_TOKEN:your-logger-bot-token}
      chat-id: ${TELEGRAM_LOGGER_CHAT_ID:your-logger-chat-id}
```

### Email

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${SPRING_MAIL_USERNAME:your-email@gmail.com}
    password: ${SPRING_MAIL_PASSWORD:your-app-password}

    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

---

## 🚀 Running Locally

Clone the repository:

```bash
git clone https://github.com/laksheyjasoria/FileServer.git
cd FileServer
```

Create PostgreSQL database:

```sql
CREATE DATABASE fileserver;
CREATE USER fileserver WITH PASSWORD 'fileserver123';
GRANT ALL PRIVILEGES ON DATABASE fileserver TO fileserver;
```

Start Redis if required:

```bash
docker run -d -p 6379:6379 redis:alpine
```

Set environment variables in PowerShell:

```powershell
$env:APP_SECURITY_MASTER_KEY="your-master-key"
$env:APP_SECURITY_JWT_SECRET="your-jwt-secret"
$env:APP_ADMIN_EMAIL="admin@example.com"
$env:APP_ADMIN_PASSWORD="secure-password"
```

Build:

```bash
mvn clean install
```

Run:

```bash
mvn spring-boot:run
```

---

## 🌐 Application URLs

Application:

```text
http://localhost:8080
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

Actuator Health:

```text
http://localhost:8080/actuator/health
```

---

## 🐳 Docker

Build:

```bash
docker build -t fileserver:prod .
```

Run:

```powershell
docker run -d `
  --name fileserver `
  -p 8080:8080 `
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/fileserver" `
  -e SPRING_DATASOURCE_USERNAME="fileserver" `
  -e SPRING_DATASOURCE_PASSWORD="fileserver123" `
  -e APP_SECURITY_MASTER_KEY="your-master-key" `
  -e APP_SECURITY_JWT_SECRET="your-jwt-secret" `
  -e APP_ADMIN_EMAIL="admin@example.com" `
  -e APP_ADMIN_PASSWORD="secure-password" `
  -v "${PWD}\uploads:/app/uploads" `
  fileserver:prod
```

Docker Compose:

```bash
docker-compose -f docker-compose.prod.yml up -d
```

---

## 📚 API Documentation

The complete API documentation is available at:

```text
http://localhost:8080/swagger-ui.html
```

Key API groups include:

* Authentication
* Users
* Files
* Folders
* Sharing
* Trash
* Billing
* Logger Management

See:

[API Documentation](docs/api/README.md)

---

## 🧪 Testing

Run all tests:

```bash
mvn test
```

Run a specific test:

```bash
mvn test -Dtest=UserServiceTest
```

Build without tests:

```bash
mvn clean install -DskipTests
```

---

## 🚢 Production Deployment

See:

[Deployment Guide](docs/deployment/README.md)

Production recommendations:

1. Use managed PostgreSQL.
2. Use Redis where required.
3. Store secrets outside source control.
4. Use Kubernetes Secrets, AWS SSM, Vault, or equivalent.
5. Use persistent storage for uploaded files.
6. Configure HTTPS.
7. Configure monitoring.
8. Configure database backups.
9. Configure file-storage backups.
10. Rotate secrets periodically.

---

## 🔐 Production Environment Variables

| Variable                     | Description                |
| ---------------------------- | -------------------------- |
| `SPRING_DATASOURCE_URL`      | PostgreSQL JDBC URL        |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username        |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password        |
| `APP_SECURITY_MASTER_KEY`    | Master security key        |
| `APP_SECURITY_JWT_SECRET`    | JWT signing secret         |
| `APP_ADMIN_EMAIL`            | Administrator email        |
| `APP_ADMIN_PASSWORD`         | Administrator password     |
| `APP_ADMIN_NAME`             | Administrator name         |
| `SPRING_MAIL_HOST`           | SMTP host                  |
| `SPRING_MAIL_PORT`           | SMTP port                  |
| `SPRING_MAIL_USERNAME`       | SMTP username              |
| `SPRING_MAIL_PASSWORD`       | SMTP password              |
| `TELEGRAM_STORAGE_BOT_TOKEN` | Telegram storage bot token |
| `TELEGRAM_STORAGE_CHAT_ID`   | Telegram storage chat ID   |
| `TELEGRAM_LOGGER_BOT_TOKEN`  | Telegram logger bot token  |
| `TELEGRAM_LOGGER_CHAT_ID`    | Telegram logger chat ID    |

---

## 🤝 Contributing

See:

[CONTRIBUTING.md](CONTRIBUTING.md)

---

## 📄 License

FileServer is distributed under the MIT License.

See:

[LICENSE](LICENSE)

---

## 📞 Support

GitHub Issues:

https://github.com/laksheyjasoria/FileServer/issues

GitHub Discussions:

https://github.com/laksheyjasoria/FileServer/discussions

---

Built with ❤️ using Spring Boot.
