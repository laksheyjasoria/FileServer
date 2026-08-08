Production Docker and environment notes

Default Admin User:
- On first application startup, if no users exist and admin credentials are configured, a default admin is automatically created.
- Configure via environment variables or application.yml:
  - `APP_ADMIN_EMAIL`: Admin email address
  - `APP_ADMIN_PASSWORD`: Admin password (will be hashed before storage)
  - `APP_ADMIN_NAME` (optional): Admin display name (default: "Admin")
- Example (Docker):
  ```
  docker run -e APP_ADMIN_EMAIL="lkjasoria0@gmail.com" \
    -e APP_ADMIN_PASSWORD="your-secure-password" \
    ... fileserver:prod
  ```
- If no admin email is configured, the admin creation is skipped.

Auto-Initialized Logger:
- On every container initialization, a logger named "FileServer" is automatically created (if it doesn't exist).
- The logger is configured to track application-level events and errors.
- Initial startup message is logged to this logger.

Required environment variables (set these in your container/orchestrator):

- SPRING_DATASOURCE_URL: JDBC URL for production DB (e.g. jdbc:postgresql://db:5432/fileserver)
- SPRING_DATASOURCE_USERNAME
- SPRING_DATASOURCE_PASSWORD
- APP_SECURITY_MASTER_KEY: master key used by admin routes (X-MASTER-KEY)
- APP_SECURITY_JWT_SECRET: JWT secret for signing tokens
- APP_ADMIN_EMAIL: Email for default admin user (leave empty to skip admin creation)
- APP_ADMIN_PASSWORD: Password for default admin user (ignored if email is not set)
- SPRING_MAIL_HOST
- SPRING_MAIL_PORT
- SPRING_MAIL_USERNAME
- SPRING_MAIL_PASSWORD
- TELEGRAM_BOT_TOKEN (optional, for Telegram logger)
- TELEGRAM_CHAT_ID (optional)

Uploads and persistent data:
- Mount a volume for `/app/data` and `./uploads` so files persist across container restarts.

Build and run (local):

docker build -t fileserver:prod .

docker run \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host:5432/db" \
  -e SPRING_DATASOURCE_USERNAME=... \
  -e SPRING_DATASOURCE_PASSWORD=... \
  -e APP_SECURITY_MASTER_KEY=... \
  -e APP_SECURITY_JWT_SECRET=... \
  -e APP_ADMIN_EMAIL="lkjasoria0@gmail.com" \
  -e APP_ADMIN_PASSWORD="your-secure-password" \
  -p 8080:8080 \
  -v ./uploads:/app/uploads \
  fileserver:prod

Notes:
- The included `Dockerfile` is multi-stage and produces a small runtime image.
- Application reads Spring properties as usual; prefer environment variables in production.
- Ensure DB migrations are applied (add Flyway or run SQL migrations) before starting against an existing DB schema. Columns added recently: `parent_id`, `drive_type`, `access_type`, `photo_url` in `master_files`/`users` tables.
- For secrets, use your orchestrator's secret store (Kubernetes Secrets, Docker Secrets, AWS SSM, etc.) rather than baking into `application-prod.yml`.

Quick health check endpoint: `GET /actuator/health` (enabled in production profile if actuator is configured).