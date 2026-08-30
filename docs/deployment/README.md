
# FileServer Deployment Guide

This guide describes deployment of FileServer in production environments.

## Prerequisites

* Java 17+
* PostgreSQL 14+
* Redis
* Docker
* Docker Compose
* SMTP server
* Persistent storage
* Telegram configuration if Telegram features are enabled

## Production Architecture

```text
Internet
   |
HTTPS / Reverse Proxy
   |
FileServer Application
   |
   +---- PostgreSQL
   |
   +---- Redis
   |
   +---- Persistent File Storage
   |
   +---- Telegram
```

## Environment Variables

### Required

| Variable                     | Description         |
| ---------------------------- | ------------------- |
| `SPRING_DATASOURCE_URL`      | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password |
| `APP_SECURITY_MASTER_KEY`    | Master security key |
| `APP_SECURITY_JWT_SECRET`    | JWT secret          |
| `APP_ADMIN_EMAIL`            | Admin email         |
| `APP_ADMIN_PASSWORD`         | Admin password      |

### Optional

| Variable                     | Description        |
| ---------------------------- | ------------------ |
| `APP_ADMIN_NAME`             | Admin display name |
| `SPRING_MAIL_HOST`           | SMTP host          |
| `SPRING_MAIL_PORT`           | SMTP port          |
| `SPRING_MAIL_USERNAME`       | SMTP username      |
| `SPRING_MAIL_PASSWORD`       | SMTP password      |
| `TELEGRAM_STORAGE_BOT_TOKEN` | Storage bot token  |
| `TELEGRAM_STORAGE_CHAT_ID`   | Storage chat ID    |
| `TELEGRAM_LOGGER_BOT_TOKEN`  | Logger bot token   |
| `TELEGRAM_LOGGER_CHAT_ID`    | Logger chat ID     |

## Docker Build

```bash
docker build -t fileserver:prod .
```

## Docker Compose

```bash
docker-compose -f docker-compose.prod.yml up -d
```

Check containers:

```bash
docker-compose -f docker-compose.prod.yml ps
```

View logs:

```bash
docker-compose -f docker-compose.prod.yml logs -f
```

## Health Check

```bash
curl http://localhost:8080/actuator/health
```

## Database Migration

Flyway runs database migrations automatically during application startup.

Migration directory:

```text
src/main/resources/db/migration
```

Migration naming:

```text
V1__initial_schema.sql
V2__add_file_share.sql
V3__add_logger_configuration.sql
```

Do not modify an already-applied migration.

Create a new migration instead.

## Kubernetes

Example:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fileserver
spec:
  replicas: 3

  selector:
    matchLabels:
      app: fileserver

  template:
    metadata:
      labels:
        app: fileserver

    spec:
      containers:
        - name: fileserver
          image: fileserver:prod

          ports:
            - containerPort: 8080

          env:
            - name: SPRING_DATASOURCE_URL
              value: "jdbc:postgresql://postgres-service:5432/fileserver"

            - name: SPRING_DATASOURCE_USERNAME
              value: "fileserver"

            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: fileserver-secrets
                  key: spring-datasource-password

            - name: APP_SECURITY_MASTER_KEY
              valueFrom:
                secretKeyRef:
                  name: fileserver-secrets
                  key: app-security-master-key

            - name: APP_SECURITY_JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: fileserver-secrets
                  key: app-security-jwt-secret

          volumeMounts:
            - name: uploads
              mountPath: /app/uploads

      volumes:
        - name: uploads
          persistentVolumeClaim:
            claimName: fileserver-uploads-pvc
```

## Database Backup

```bash
pg_dump -U fileserver fileserver > backup.sql
```

Restore:

```bash
psql -U fileserver fileserver < backup.sql
```

## File Backup

Example Linux backup:

```bash
tar -czf uploads-backup.tar.gz /app/uploads
```

Always test backups by performing a restoration test.

## Security Recommendations

1. Enable HTTPS.
2. Never commit secrets.
3. Use strong JWT secrets.
4. Use a strong master key.
5. Restrict database access.
6. Protect administrative APIs.
7. Rotate credentials.
8. Use persistent storage.
9. Keep dependencies updated.
10. Enable rate limiting.
11. Monitor logs.
12. Configure backups.

## Performance

Potential JVM configuration:

```text
-Xms1g
-Xmx2g
```

Database pool example:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
```

Monitor:

* CPU
* Memory
* Disk
* Network
* Database connections
* Redis usage
* Upload throughput

## Troubleshooting

### Database Connection Failure

Check:

* Database availability
* JDBC URL
* Username
* Password
* Network access

### Upload Failure

Check:

* Multipart limits
* Disk permissions
* Disk space
* Persistent volume
* Request size limits

### Email Failure

Check:

* SMTP host
* SMTP port
* Credentials
* TLS
* Firewall

### Telegram Failure

Check:

* Bot token
* Chat ID
* Network access
* Telegram configuration

### Docker Logs

```bash
docker logs fileserver
```

### Kubernetes Logs

```bash
kubectl logs deployment/fileserver
```

