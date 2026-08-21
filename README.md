# File Server

A secure, production-oriented cloud file storage and management service built with **Java, Spring Boot, PostgreSQL, Redis, JWT authentication, Google OAuth2, multipart/chunked uploads, sharing, billing, webhooks, email, and centralized logger management**.

Repository: https://github.com/laksheyjasoria/FileServer

---

## Table of Contents

1. Overview
2. Key Features
3. Technology Stack
4. Architecture
5. Project Structure
6. Application Modules
7. Authentication & Security
8. API Overview
9. Authentication API
10. Drive API
11. Upload API
12. Chunk Upload API
13. Download API
14. Resource API
15. Share API
16. Billing API
17. Webhook API
18. Email API
19. Logger API
20. Health & Monitoring
21. Request Authentication
22. Master-Key Protected APIs
23. Data & Persistence
24. File Storage
25. Rate Limiting
26. Configuration
27. Environment Variables
28. Docker & Production
29. Default Administrator
30. Logger Initialization
31. API Response Conventions
32. HTTP Status Codes
33. Example API Calls
34. Frontend
35. API Documentation
36. Postman
37. Build & Run
38. Production Deployment
39. Persistent Volumes
40. Security Recommendations
41. Repository Documentation
42. Known Documentation Notes

---

# 1. Overview

**File Server** is a Spring Boot based file-management platform designed to provide authenticated users with a centralized location for uploading, storing, organizing, downloading, sharing, and managing files.

The application combines:

* User authentication
* JWT-based stateless authentication
* Google OAuth2 login
* PostgreSQL persistence
* Redis integration
* Single-request file uploads
* Large-file chunked uploads
* Resumable uploads
* File metadata management
* Resource actions
* Share links
* Optional password-protected sharing
* Share-link expiration
* Billing plans
* User subscriptions
* Webhooks
* Email delivery
* Centralized logger management
* Master-key protected administrative APIs
* Rate limiting
* Docker deployment
* Actuator health monitoring

The repository contains a dedicated API documentation directory under `docs/api`.

---

# 2. Key Features

## Authentication

* User registration
* User login
* JWT authentication
* Current-user endpoint
* Forgot-password flow
* Reset-password flow
* Google OAuth2 integration
* Stateless authentication architecture

## File Management

* Upload files
* List files
* Retrieve file metadata
* Perform resource actions
* Move resources
* Delete resources
* Batch resource operations

## Large File Upload

* Create upload jobs
* Upload individual chunks
* Resume interrupted uploads
* Check upload status
* Cancel uploads
* Track uploaded chunks

## File Sharing

* Generate share links
* Public/private access
* Optional share passwords
* Expiration support
* Token-based share validation

## Billing

* Create storage plans
* Configure storage limits
* Configure upload limits
* Configure daily upload limits
* Configure API request limits
* Assign plans to users
* Configure subscription validity

## Webhooks

* Register user webhooks
* Configure webhook secrets
* Enable/disable webhook processing
* Track retry information

## Email

* Send email
* Multiple recipients
* CC/BCC
* Plain-text or HTML mail

## Logging

* Create application loggers
* Update logger levels
* Delete loggers
* Submit informational/warning logs
* Submit error logs
* Master-key protection for logger administration

---

# 3. Technology Stack

| Component                      | Technology                  |
| ------------------------------ | --------------------------- |
| Language                       | Java                        |
| Build Tool                     | Maven                       |
| Framework                      | Spring Boot 3.2.4           |
| Java Target                    | Java 21                     |
| Database                       | PostgreSQL                  |
| ORM                            | Spring Data JPA / Hibernate |
| Cache / Session Infrastructure | Redis                       |
| Security                       | Spring Security             |
| Authentication                 | JWT                         |
| OAuth                          | Google OAuth2               |
| Mail                           | Spring Boot Mail            |
| Database Migration             | Flyway                      |
| Rate Limiting                  | Bucket4j                    |
| API Documentation              | SpringDoc OpenAPI / Swagger |
| Monitoring                     | Spring Boot Actuator        |
| Template Engine                | Thymeleaf                   |
| Containerization               | Docker                      |
| JSON                           | Jackson                     |
| Utility Libraries              | Apache Commons              |

The current Maven configuration declares Spring Web, JPA, Redis, Security, OAuth2 Client, Mail, Validation, Actuator, Thymeleaf, PostgreSQL, Flyway, JWT, Bucket4j, SpringDoc and supporting libraries.

---

# 4. Architecture

The application is organized into domain-oriented modules rather than one large controller/service package.

High-level architecture:

```text
                        ┌─────────────────────┐
                        │      Frontend       │
                        │ HTML / CSS / JS     │
                        └──────────┬──────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │   Spring Boot API   │
                        │     Controllers     │
                        └──────────┬──────────┘
                                   │
                                   ▼
                        ┌─────────────────────┐
                        │    Orchestration    │
                        │      Services       │
                        └──────────┬──────────┘
                                   │
               ┌───────────────────┼──────────────────┐
               │                   │                  │
               ▼                   ▼                  ▼
        ┌────────────┐      ┌────────────┐     ┌────────────┐
        │ PostgreSQL │      │   Redis    │     │   Storage  │
        │    JPA     │      │            │     │ File/Cloud │
        └────────────┘      └────────────┘     └────────────┘

               ┌───────────────────┐
               │ External Services  │
               ├───────────────────┤
               │ Google OAuth       │
               │ SMTP               │
               │ Telegram Logger    │
               └───────────────────┘
```

---

# 5. Project Structure

The main application package is organized into the following domains:

```text
src/main/java/com/app/

├── billing/
├── config/
├── core/
├── drive/
├── email/
├── identity/
├── logger/
├── master/
├── orchestrator/
├── resource/
├── scheduler/
│   └── job/
├── share/
├── storage/
├── telegram/
├── upload/
├── webhook/
└── FileServerApplication.java
```

The repository currently exposes these modules in the main application package.

The major responsibilities are:

### `identity`

Authentication, users, credentials, JWT/OAuth-related functionality and user management.

### `drive`

User file listing and drive-related operations.

### `upload`

Single-request file upload functionality and upload metadata.

### `resource`

Generic resource operations such as move/delete/batch actions.

### `share`

Share-link creation and token-based access.

### `billing`

Plans and user subscriptions.

### `webhook`

Webhook registration and management.

### `email`

System email delivery.

### `logger`

Logger management and application logging APIs.

### `telegram`

Telegram-based logging integration.

### `storage`

File/storage implementation.

### `master`

Master-key based administrative/security functionality.

### `scheduler`

Scheduled background jobs.

### `orchestrator`

Application-level coordination between controllers and domain services.

---

# 6. Application Modules

```text
Authentication
    │
    ├── Register
    ├── Login
    ├── JWT
    ├── OAuth2
    ├── Forgot Password
    └── Reset Password

File Management
    │
    ├── Drive
    ├── Upload
    ├── Chunk Upload
    ├── Download
    └── Resource Actions

Sharing
    │
    ├── Share Link
    ├── Public Access
    ├── Password Protection
    └── Expiration

Platform Services
    │
    ├── Billing
    ├── Webhooks
    ├── Email
    └── Logger

Infrastructure
    │
    ├── PostgreSQL
    ├── Redis
    ├── Docker
    ├── Flyway
    ├── Actuator
    └── Rate Limiting
```

---

# 7. Authentication & Security

The application uses Spring Security with JWT-based authentication.

Authenticated requests use:

```http
Authorization: Bearer <JWT_TOKEN>
```

Example:

```http
GET /auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

The Auth API documentation specifies JWT/Bearer authentication for the authenticated-user endpoint.

## Authentication Flow

```text
User
 │
 ├── Register
 │      │
 │      ▼
 │   User Created
 │
 ├── Login
 │      │
 │      ▼
 │   JWT Token
 │
 └── API Request
        │
        ▼
 Authorization: Bearer JWT
        │
        ▼
 Spring Security
        │
        ▼
 Authenticated User
        │
        ▼
 Controller
```

---

# 8. API Overview

Base URL example:

```text
http://localhost:8080
```

Production:

```text
https://your-domain.com
```

## Complete documented API inventory

| Module       | Method | Endpoint                                |
| ------------ | ------ | --------------------------------------- |
| Auth         | POST   | `/auth/register`                        |
| Auth         | POST   | `/auth/login`                           |
| Auth         | POST   | `/auth/forgot-password`                 |
| Auth         | POST   | `/auth/reset-password`                  |
| Auth         | GET    | `/auth/me`                              |
| Drive        | GET    | `/drive`                                |
| Upload       | POST   | `/upload`                               |
| Chunk Upload | POST   | `/chunk-upload/create`                  |
| Chunk Upload | POST   | `/chunk-upload/{uploadId}/{chunkIndex}` |
| Chunk Upload | GET    | `/chunk-upload/{uploadId}/resume`       |
| Chunk Upload | GET    | `/chunk-upload/{uploadId}/status`       |
| Chunk Upload | DELETE | `/chunk-upload/{uploadId}`              |
| Download     | GET    | `/download/{id}`                        |
| Resource     | POST   | `/resources/action`                     |
| Share        | POST   | `/share`                                |
| Share        | GET    | `/share/{token}`                        |
| Billing      | POST   | `/billing/plan`                         |
| Billing      | POST   | `/billing/assign`                       |
| Webhook      | POST   | `/webhooks`                             |
| Email        | POST   | `/email/send`                           |
| Logger       | POST   | `/logger/create`                        |
| Logger       | PUT    | `/logger/{id}`                          |
| Logger       | DELETE | `/logger/{id}`                          |
| Logger       | POST   | `/logger/log`                           |
| Logger       | POST   | `/logger/error`                         |
| Actuator     | GET    | `/actuator/health`                      |

The first 22 application API routes are documented in the repository's API documentation index and individual API files.

---

# 9. Authentication API

Base path:

```text
/auth
```

## 9.1 Register

```http
POST /auth/register
Content-Type: application/json
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password123",
  "name": "John Doe"
}
```

Fields:

| Field    | Type   | Required |
| -------- | ------ | -------- |
| email    | string | Yes      |
| password | string | Yes      |
| name     | string | Yes      |

Password must contain at least 6 characters according to the documented request validation.

---

## 9.2 Login

```http
POST /auth/login
Content-Type: application/json
```

Request:

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

Response:

```json
{
  "success": true,
  "data": "<JWT_TOKEN>"
}
```

The documented output is an `ApiResponse<String>` containing the authentication token.

---

## 9.3 Forgot Password

```http
POST /auth/forgot-password
```

Parameter:

```text
email=user@example.com
```

Purpose:

* Initiates password-reset flow.
* Sends reset information through configured email service.

Documented response:

```text
Reset email sent
```

---

## 9.4 Reset Password

```http
POST /auth/reset-password
```

Parameters:

```text
token=<RESET_TOKEN>
password=<NEW_PASSWORD>
```

Purpose:

```text
Reset the user's password using a valid reset token.
```

Documented response:

```text
Password updated
```

---

## 9.5 Current User

```http
GET /auth/me
Authorization: Bearer <JWT>
```

Returns:

```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "John Doe",
  "role": "USER",
  "enabled": true,
  "createdAt": "2026-08-21T12:00:00"
}
```

Password is not returned.

The documented user fields are `id`, `email`, `name`, `role`, `enabled`, and `createdAt`.

---

# 10. Drive API

Base path:

```text
/drive
```

## List Drive

```http
GET /drive
Authorization: Bearer <JWT>
```

Returns the authenticated user's files.

Conceptual response:

```json
[
  {
    "id": 1,
    "userId": 10,
    "name": "document.pdf",
    "fileId": "abc123",
    "size": 1048576,
    "contentType": "application/pdf",
    "createdAt": "2026-08-21T10:30:00"
  }
]
```

The documented `MasterFile` metadata contains `id`, `userId`, `name`, `fileId`, `size`, `contentType`, and `createdAt`.

---

# 11. Upload API

Base path:

```text
/upload
```

## Single File Upload

```http
POST /upload
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

Form field:

```text
file=<FILE>
```

Example:

```bash
curl -X POST http://localhost:8080/upload \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@document.pdf"
```

The endpoint accepts a `MultipartFile` and delegates the upload operation to the upload service.

---

# 12. Chunk Upload API

Base path:

```text
/chunk-upload
```

Chunk upload is intended for large files and resumable uploads.

## 12.1 Create Upload Job

```http
POST /chunk-upload/create
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request:

```json
{
  "fileName": "large-video.mp4",
  "totalSize": 524288000,
  "totalChunks": 50
}
```

Response contains:

```text
id
userId
fileName
totalSize
totalChunks
uploadedChunks
status
createdAt
```

---

## 12.2 Upload Chunk

```http
POST /chunk-upload/{uploadId}/{chunkIndex}
Authorization: Bearer <JWT>
Content-Type: multipart/form-data
```

Form:

```text
file=<CHUNK>
```

Example:

```bash
curl -X POST \
  http://localhost:8080/chunk-upload/abc123/0 \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@chunk-0.part"
```

Successful response:

```text
204 No Content
```

---

## 12.3 Resume Upload

```http
GET /chunk-upload/{uploadId}/resume
Authorization: Bearer <JWT>
```

Returns uploaded chunks.

```json
[
  {
    "id": 1,
    "uploadJobId": "abc123",
    "chunkIndex": 0,
    "telegramFileId": "...",
    "size": 10485760
  }
]
```

---

## 12.4 Upload Status

```http
GET /chunk-upload/{uploadId}/status
Authorization: Bearer <JWT>
```

Response:

```json
{
  "uploadId": "abc123",
  "uploadedChunks": 25,
  "totalChunks": 50,
  "status": "UPLOADING"
}
```

---

## 12.5 Cancel Upload

```http
DELETE /chunk-upload/{uploadId}
Authorization: Bearer <JWT>
```

Cancels and cleans up the upload job.

---

# 13. Download API

Base path:

```text
/download
```

## Get File Metadata

```http
GET /download/{id}
Authorization: Bearer <JWT>
```

Returns the corresponding `MasterFile`.

Documented metadata:

```text
id
userId
name
fileId
size
contentType
createdAt
```

---

# 14. Resource API

Base path:

```text
/resources
```

## Resource Action

```http
POST /resources/action
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request:

```json
{
  "action": "MOVE",
  "ids": [
    "file-id-1",
    "file-id-2"
  ],
  "destination": "folder-id"
}
```

Fields:

| Field       | Type           | Required |
| ----------- | -------------- | -------- |
| action      | ResourceAction | Yes      |
| ids         | List<String>   | Yes      |
| destination | String         | Optional |

This API is intended for batch operations against multiple resources.

---

# 15. Share API

Base path:

```text
/share
```

## 15.1 Create Share

```http
POST /share
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request:

```json
{
  "fileId": "abc123",
  "publicAccess": true,
  "password": null,
  "expiry": "2026-08-30T23:59:59"
}
```

Fields:

| Field        | Type          | Required |
| ------------ | ------------- | -------- |
| fileId       | string        | Yes      |
| publicAccess | boolean       | Yes      |
| password     | string        | Optional |
| expiry       | LocalDateTime | Optional |

Response:

```json
{
  "url": "https://example.com/share/abc...",
  "token": "abc..."
}
```

---

## 15.2 Validate Share

```http
GET /share/{token}
```

Optional:

```text
?password=<PASSWORD>
```

Example:

```http
GET /share/abc123?password=secret
```

Returns the shared resource after access validation.

---

# 16. Billing API

Base path:

```text
/billing
```

## 16.1 Create Plan

```http
POST /billing/plan
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request:

```json
{
  "name": "Pro",
  "storageLimitBytes": 107374182400,
  "maxUploadSizeBytes": 5368709120,
  "dailyUploadLimit": 50,
  "apiRequestLimit": 10000,
  "price": 9.99
}
```

Plan properties:

```text
id
name
storageLimitBytes
maxUploadSizeBytes
dailyUploadLimit
apiRequestLimit
price
active
```

---

## 16.2 Assign Plan

```http
POST /billing/assign
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request:

```json
{
  "userId": "10",
  "planId": "2",
  "validityDays": 30
}
```

Response contains:

```text
id
userId
plan
startDate
expiryDate
active
```

---

# 17. Webhook API

Base path:

```text
/webhooks
```

## Create Webhook

```http
POST /webhooks
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request:

```json
{
  "url": "https://example.com/webhook",
  "secret": "webhook-secret"
}
```

Response:

```json
{
  "id": 1,
  "userId": 10,
  "url": "https://example.com/webhook",
  "secret": "webhook-secret",
  "enabled": true,
  "retryCount": 0,
  "createdAt": "2026-08-21T10:00:00"
}
```

The repository currently documents webhook creation with URL and secret fields.

---

# 18. Email API

Base path:

```text
/email
```

## Send Email

```http
POST /email/send
Authorization: Bearer <JWT>
Content-Type: application/json
```

Request:

```json
{
  "to": [
    "user@example.com"
  ],
  "cc": [],
  "bcc": [],
  "subject": "File Server Notification",
  "body": "Your file has been uploaded.",
  "html": false
}
```

Fields:

| Field   | Type         | Required |
| ------- | ------------ | -------- |
| to      | List<String> | Yes      |
| cc      | List<String> | No       |
| bcc     | List<String> | No       |
| subject | String       | Yes      |
| body    | String       | Yes      |
| html    | boolean      | No       |

Response:

```text
Email sent successfully
```

---

# 19. Logger API

Base path:

```text
/logger
```

The Logger API provides both administrative logger management and event submission.

---

## 19.1 Create Logger

```http
POST /logger/create?name=FileServer
X-MASTER-KEY: <MASTER_KEY>
```

Purpose:

```text
Create a named logger.
```

Response:

```text
<LOGGER_ID>
```

The repository documentation explicitly marks this endpoint as master-key protected.

---

## 19.2 Update Logger

```http
PUT /logger/{id}
X-MASTER-KEY: <MASTER_KEY>
```

Parameters:

```text
info=true
warn=true
```

Example:

```http
PUT /logger/1?info=true&warn=true
X-MASTER-KEY: <MASTER_KEY>
```

---

## 19.3 Delete Logger

```http
DELETE /logger/{id}
X-MASTER-KEY: <MASTER_KEY>
```

---

## 19.4 Submit Log

```http
POST /logger/log
```

Parameters:

```text
loggerId
level
message
```

Example:

```http
POST /logger/log?loggerId=1&level=INFO&message=Upload completed
```

---

## 19.5 Submit Error

```http
POST /logger/error
```

Parameters:

```text
loggerId
message
```

Example:

```http
POST /logger/error?loggerId=1&message=Upload failed
```

The repository documents logger administration as master-key protected while log/error submission is exposed as a public logging API.

---

# 20. Health & Monitoring

The application includes Spring Boot Actuator.

Production documentation identifies:

```http
GET /actuator/health
```

as the health-check endpoint.

Example:

```bash
curl http://localhost:8080/actuator/health
```

Typical Spring Boot response:

```json
{
  "status": "UP"
}
```

---

# 21. Request Authentication

For authenticated APIs:

```http
Authorization: Bearer <JWT_TOKEN>
```

Example:

```bash
curl http://localhost:8080/drive \
  -H "Authorization: Bearer eyJ..."
```

Do not send the JWT in query parameters.

---

# 22. Master-Key Protected APIs

Administrative logger operations use:

```http
X-MASTER-KEY: <MASTER_KEY>
```

Current documented master-key protected endpoints:

```text
POST   /logger/create
PUT    /logger/{id}
DELETE /logger/{id}
```

Production configuration identifies:

```text
APP_SECURITY_MASTER_KEY
```

as the master key used by admin routes.

---

# 23. Data & Persistence

The application uses PostgreSQL through Spring Data JPA.

Major persistence concepts include:

```text
User
MasterFile
UploadJob
UploadChunk
Share
Plan
Subscription
Webhook
Logger
```

The production documentation specifically references the `master_files` and `users` database tables and notes recently added columns:

```text
parent_id
drive_type
access_type
photo_url
```

Flyway is included for database migration management.

---

# 24. File Storage

The application supports persistent file storage and containerized deployment.

Production deployment expects persistent storage for:

```text
/app/data
./uploads
```

The production documentation recommends mounting persistent volumes so uploaded data survives container restarts.

---

# 25. Rate Limiting

The project includes:

```text
Bucket4j 8.10.1
```

for rate limiting.

This allows the application to enforce request/upload limits rather than allowing unlimited API traffic.

Billing plans also contain:

```text
dailyUploadLimit
apiRequestLimit
maxUploadSizeBytes
storageLimitBytes
```

This provides a foundation for plan-level usage enforcement.

---

# 26. Configuration

Application configuration is located under:

```text
src/main/resources/application.yml
```

The repository also contains Docker and production configuration.

The Maven project uses Spring Boot configuration support and environment-based production configuration.

---

# 27. Environment Variables

Production documentation identifies the following configuration values.

## Database

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Example:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/fileserver
```

## Security

```text
APP_SECURITY_MASTER_KEY
APP_SECURITY_JWT_SECRET
```

## Default Administrator

```text
APP_ADMIN_EMAIL
APP_ADMIN_PASSWORD
APP_ADMIN_NAME
```

## Mail

```text
SPRING_MAIL_HOST
SPRING_MAIL_PORT
SPRING_MAIL_USERNAME
SPRING_MAIL_PASSWORD
```

## Telegram Logger

Optional:

```text
TELEGRAM_BOT_TOKEN
TELEGRAM_CHAT_ID
```

These variables are documented in the repository's production deployment guide.

---

# 28. Docker & Production

The repository includes:

```text
Dockerfile
docker-compose.prod.yml
PRODUCTION.md
```

The Docker image can be built using:

```bash
docker build -t fileserver:prod .
```

Run example:

```bash
docker run \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host:5432/db" \
  -e SPRING_DATASOURCE_USERNAME="username" \
  -e SPRING_DATASOURCE_PASSWORD="password" \
  -e APP_SECURITY_MASTER_KEY="master-key" \
  -e APP_SECURITY_JWT_SECRET="jwt-secret" \
  -e APP_ADMIN_EMAIL="admin@example.com" \
  -e APP_ADMIN_PASSWORD="secure-password" \
  -p 8080:8080 \
  -v ./uploads:/app/uploads \
  fileserver:prod
```

The repository's production guide provides the corresponding Docker deployment configuration.

---

# 29. Default Administrator

On the first application startup, if no users exist and administrator credentials are configured, the application can automatically create a default administrator.

Configuration:

```text
APP_ADMIN_EMAIL
APP_ADMIN_PASSWORD
APP_ADMIN_NAME
```

The password is hashed before storage.

If `APP_ADMIN_EMAIL` is not configured, administrator creation is skipped.

---

# 30. Logger Initialization

During container initialization, the application automatically creates a logger named:

```text
FileServer
```

if it does not already exist.

The production documentation states that this logger is used for application-level events and errors and receives an initial startup message.

---

# 31. API Response Conventions

Some APIs use a generic:

```text
ApiResponse<T>
```

wrapper.

For example:

```json
{
  "success": true,
  "data": "..."
}
```

The exact serialized structure depends on the application's `ApiResponse` implementation.

Authentication APIs explicitly document `ApiResponse<String>` outputs.

Other endpoints return entities or collections directly.

Examples:

```text
List<MasterFile>
MasterFile
UploadJob
UploadChunk
Plan
Subscription
Webhook
SharedResource
ShareResponse
```

---

# 32. HTTP Status Codes

Common status codes expected by the application:

| Status | Meaning                                  |
| ------ | ---------------------------------------- |
| 200    | Successful request                       |
| 201    | Resource created                         |
| 204    | Successful request with no response body |
| 400    | Invalid request                          |
| 401    | Authentication required/invalid          |
| 403    | Access denied                            |
| 404    | Resource not found                       |
| 409    | Conflict                                 |
| 429    | Rate limit exceeded                      |
| 500    | Internal server error                    |

The chunk-upload endpoint explicitly documents `204 No Content` for successful chunk upload.

---

# 33. Example API Calls

## Register

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email":"user@example.com",
    "password":"password123",
    "name":"John Doe"
  }'
```

## Login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email":"user@example.com",
    "password":"password123"
  }'
```

## Current User

```bash
curl http://localhost:8080/auth/me \
  -H "Authorization: Bearer <JWT>"
```

## List Drive

```bash
curl http://localhost:8080/drive \
  -H "Authorization: Bearer <JWT>"
```

## Upload

```bash
curl -X POST http://localhost:8080/upload \
  -H "Authorization: Bearer <JWT>" \
  -F "file=@document.pdf"
```

## File Metadata

```bash
curl http://localhost:8080/download/123 \
  -H "Authorization: Bearer <JWT>"
```

## Create Share

```bash
curl -X POST http://localhost:8080/share \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "fileId":"123",
    "publicAccess":true,
    "password":null,
    "expiry":"2026-08-30T23:59:59"
  }'
```

## Logger Administration

```bash
curl -X POST \
  "http://localhost:8080/logger/create?name=FileServer" \
  -H "X-MASTER-KEY: <MASTER_KEY>"
```

## Health Check

```bash
curl http://localhost:8080/actuator/health
```

---

# 34. Frontend

The repository contains frontend resources in:

```text
src/main/resources/static
```

and also root-level:

```text
index.html
login.html
```

The static resources are served by the Spring Boot application.

The project also contains email templates under:

```text
src/main/resources/templates/email
```

The repository structure confirms both static frontend resources and email templates are included.

---

# 35. API Documentation

The project maintains controller-level API documentation under:

```text
docs/api/
```

Current documentation files:

```text
docs/api/README.md
docs/api/auth.md
docs/api/billing.md
docs/api/chunk-upload.md
docs/api/download.md
docs/api/drive.md
docs/api/email.md
docs/api/logger.md
docs/api/resource.md
docs/api/share.md
docs/api/upload.md
docs/api/webhook.md
```

There is also:

```text
docs/api/postman_collection.json
```

The API documentation index explicitly lists these API modules.

---

# 36. Postman

A Postman collection is included in the repository:

```text
docs/api/postman_collection.json
```

and another application resource is available at:

```text
src/main/resources/FileServer.postman_collection.json
```

These collections can be imported into Postman for API testing.

---

# 37. Build & Run

## Requirements

Recommended environment:

```text
Java 21
Maven 3.x
PostgreSQL
Redis
```

The Maven compiler is configured for Java 21.

---

## Maven Build

Linux/macOS:

```bash
./mvnw clean package
```

Windows:

```powershell
.\mvnw.cmd clean package
```

Or with installed Maven:

```bash
mvn clean package
```

---

## Run

```bash
java -jar target/file-server-1.0.0.jar
```

---

# 38. Production Deployment

Recommended production architecture:

```text
                    Internet
                       │
                       ▼
                 Reverse Proxy
                       │
                       ▼
              ┌─────────────────┐
              │   File Server   │
              │ Spring Boot     │
              └───────┬─────────┘
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
      PostgreSQL    Redis       Storage
```

Secrets should be supplied through:

* Kubernetes Secrets
* Docker Secrets
* AWS SSM
* Environment variables
* Other secure secret-management systems

The production documentation specifically recommends using an orchestrator secret store instead of baking secrets into configuration files.

---

# 39. Persistent Volumes

For Docker deployments, persist:

```text
/app/data
/app/uploads
```

Example:

```bash
-v ./uploads:/app/uploads
```

Without persistent storage, uploaded files may be lost when the container is recreated.

The production guide explicitly recommends persistent mounts for application data/uploads.

---

# 40. Security Recommendations

## Never commit secrets

Do not commit:

```text
APP_SECURITY_JWT_SECRET
APP_SECURITY_MASTER_KEY
APP_ADMIN_PASSWORD
SPRING_MAIL_PASSWORD
TELEGRAM_BOT_TOKEN
```

Use environment variables or a secret manager.

## HTTPS

Production deployments should run behind HTTPS.

## JWT

Use a strong random JWT secret.

## Master Key

Use a high-entropy master key and never expose it to frontend code.

## File Access

Always validate authenticated ownership before allowing file operations.

## Share Links

Use expiration and passwords for sensitive files.

## Database

Do not expose PostgreSQL directly to the public internet.

## Redis

Restrict Redis to the internal application network.

## Logging

Do not log:

* Passwords
* JWT tokens
* Master keys
* SMTP credentials
* Telegram bot tokens

---

# 41. Repository Documentation

The repository currently contains:

```text
README.md
PRODUCTION.md
docs/api/README.md
docs/api/auth.md
docs/api/billing.md
docs/api/chunk-upload.md
docs/api/download.md
docs/api/drive.md
docs/api/email.md
docs/api/logger.md
docs/api/resource.md
docs/api/share.md
docs/api/upload.md
docs/api/webhook.md
docs/api/postman_collection.json
Dockerfile
docker-compose.prod.yml
pom.xml
```

The repository itself currently has no meaningful root-level GitHub README description displayed on the repository page, while detailed API documentation is already present under `docs/api`.

---

# 42. Known Documentation Notes

The current repository documentation should be treated as the API contract currently documented by the project.

There are two important points to be aware of:

### 1. Java version

The Maven configuration declares:

```xml
<java.version>17</java.version>
```

but the compiler plugin explicitly sets:

```xml
<source>21</source>
<target>21</target>
```

Therefore, the actual compiler configuration is Java 21 even though the Spring Boot property still says 17. This should ideally be made consistent in the project.

### 2. API documentation vs source implementation

The repository has an API documentation layer under `docs/api`, but API documentation should be kept synchronized with controller mappings whenever endpoints are added/removed.

For production documentation, the recommended source of truth should ultimately be:

```text
Spring Controller mappings
        +
OpenAPI specification
        +
docs/api/*.md
        +
Postman collection
```

---

# API Quick Reference

```text
AUTH
POST   /auth/register
POST   /auth/login
POST   /auth/forgot-password
POST   /auth/reset-password
GET    /auth/me

DRIVE
GET    /drive

UPLOAD
POST   /upload

CHUNK UPLOAD
POST   /chunk-upload/create
POST   /chunk-upload/{uploadId}/{chunkIndex}
GET    /chunk-upload/{uploadId}/resume
GET    /chunk-upload/{uploadId}/status
DELETE /chunk-upload/{uploadId}

DOWNLOAD
GET    /download/{id}

RESOURCE
POST   /resources/action

SHARE
POST   /share
GET    /share/{token}

BILLING
POST   /billing/plan
POST   /billing/assign

WEBHOOK
POST   /webhooks

EMAIL
POST   /email/send

LOGGER
POST   /logger/create
PUT    /logger/{id}
DELETE /logger/{id}
POST   /logger/log
POST   /logger/error

HEALTH
GET    /actuator/health
```

---

# Project Summary

File Server provides a complete backend platform for secure file storage and management.

Its architecture combines:

```text
Spring Boot
     │
     ├── Authentication
     │     ├── JWT
     │     └── Google OAuth2
     │
     ├── File Management
     │     ├── Upload
     │     ├── Chunk Upload
     │     ├── Drive
     │     ├── Download
     │     └── Resource Actions
     │
     ├── Sharing
     │     ├── Token
     │     ├── Password
     │     └── Expiration
     │
     ├── Platform
     │     ├── Billing
     │     ├── Webhooks
     │     ├── Email
     │     └── Logger
     │
     └── Infrastructure
           ├── PostgreSQL
           ├── Redis
           ├── Flyway
           ├── Bucket4j
           ├── Docker
           └── Actuator
```
