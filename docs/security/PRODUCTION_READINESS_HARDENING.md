# FileServer production-readiness hardening

Branch: `hardening/fileserver-production-readiness`
Base: `feature/chunked-file-transfer`

This branch is a hardening workstream. It must be reviewed and tested before merging into
`feature/chunked-file-transfer`, and that feature branch must be tested before it is merged
into `main`.

## Changes in this workstream

- Removed credential values and unsafe secret defaults from `application.yml`.
- Added `.env.example` with placeholders only.
- Aligned production Compose variables with Spring Boot's actual property names.
- Added PostgreSQL and Redis health checks and healthy-dependency startup ordering.
- Added Redis password configuration to Spring Data Redis.
- Disabled email by default until SMTP credentials are configured.
- Validated Google ID-token audience, issuer, expiry and subject against the configured OAuth client.
- Routed shared bulk ZIP downloads through share validation, password validation, share permission checks,
  and owner/root checks.
- Scoped shared folder and multi-share file lookups to the share owner.
- Sanitized ZIP entry names to prevent path traversal on extraction.
- Added pessimistic row locking for chunk upload state transitions and pause/resume/cancel operations.
- Added per-user locking for chunked upload quota reservations and subscription creation.
- Made scheduled jobs configurable and made stale cleanup handle paused uploads without racing completion.
- Enforced a minimum master-key length and safer comparison.
- Added a stored file fingerprint to resumable upload sessions and reject mismatched resume attempts.
- Added SHA-256 checksums for uploaded chunks and verify them before storing data.
- Counted unfinished chunked uploads as storage reservations while retaining recoverable trash in storage usage.
- Deactivated expired subscriptions before replacement and added a partial unique index for one active
  subscription per user.
- Aligned Maven and README with Java 21 and removed duplicate/invalid Maven plugin declarations.
- Added focused tests for share bulk-download authorization and resumable file identity.

## Required environment configuration

Copy `.env.example` to `.env` and replace all placeholders with newly generated values.
Do not commit `.env`. Production credentials must be injected through a secret manager or protected
deployment environment.

The following must be configured before starting production:

- `DB_PASSWORD`
- `REDIS_PASSWORD`
- `JWT_SECRET`
- `MASTER_KEY`
- `TELEGRAM_STORAGE_BOT_TOKEN`
- `TELEGRAM_STORAGE_CHAT_ID`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

Email is disabled by default. Set `APP_EMAIL_ENABLED=true` only after configuring SMTP credentials.

## Database migrations

This branch adds:

- `V1.0.15__add_upload_file_fingerprint.sql`
- `V1.0.16__enforce_one_active_subscription_per_user.sql`

Review the subscription normalization in V1.0.16 against production data before deployment.
It preserves subscription history but deactivates expired and duplicate active rows, keeping the latest
active subscription per user.

## Manual security response — mandatory

Credentials were present in a public repository configuration before this hardening branch was created.
Removing them from the current file does not remove them from Git history and does not revoke them.

Before any production release:

1. Rotate/revoke the Telegram storage and logger bot tokens.
2. Rotate the Google OAuth client secret.
3. Rotate the SMTP app password.
4. Generate a new master key and JWT signing secret. Plan for existing JWTs and any encrypted values
   derived from the old master key; do not discard the old key until encrypted production data has been
   safely re-encrypted or verified.
5. Remove exposed secrets from every published Git branch/tag history using a coordinated history rewrite.
6. Check repository logs, deployment logs and backups for copies of the old credentials.

Do not print credentials into issue comments, pull request descriptions or build logs.

## Validation required before merge

Run with the required environment variables set:

```powershell
mvn clean verify
docker compose -f docker-compose.prod.yml config
docker compose -f docker-compose.prod.yml up --build
```

Then test:

- Google login using a token issued for this exact OAuth client; reject a token issued for another client.
- Public, password-protected and user-only share links.
- VIEW, DOWNLOAD and VIEW_DOWNLOAD permissions on single-file and bulk ZIP routes.
- Bulk ZIP attempts using unrelated file IDs, expired shares and removed shares.
- Upload multiple chunks concurrently, pause/resume, cancel during an in-flight chunk, and retry failed chunks.
- Resume with the original file and reject a different file with the same name and size.
- Corrupt a chunk in transit and verify the server rejects its checksum.
- Verify completed chunk count/bytes match the final file size.
- Verify quota accounting includes active uploads and recoverable trash, but excludes failed/cancelled upload placeholders.
- Apply migrations against a production-like copy and inspect the subscription rows before and after V1.0.16.
- Verify PostgreSQL and Redis health checks and Redis authentication in Docker.

## Known work still requiring a separate implementation/review

- Legacy non-chunked upload quota reservation still needs an atomic reservation lifecycle to prevent
  simultaneous legacy uploads from exceeding a quota.
- The sampled file fingerprint is an accidental-mismatch guard, not a complete digest of a multi-gigabyte
  file. Per-chunk SHA-256 checksums provide content integrity for the bytes actually uploaded.
- Rate limiting and resource caps for recursive ZIP creation need a production workload review.
- A full audit of all public share endpoints, download-token routes, logger/master-key routes and admin
  endpoints remains necessary.
- The Instagram database split must be completed and verified separately. Do not run the existing
  backup-and-remove script until table ownership and target restore have been validated.
- Maven, Docker, database migrations and end-to-end streaming have not been executed by this branch authoring
  session. GitHub currently reports no CI status checks for the latest hardening commit; passing status must come
  from CI and a production-like test environment.
