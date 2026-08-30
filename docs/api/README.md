
# FileServer API Documentation

FileServer provides REST APIs for authentication, users, files, folders, sharing, trash, billing, and logger management.

## Base URL

```text
http://localhost:8080/api
```

## Swagger UI

```text
http://localhost:8080/swagger-ui.html
```

## Authentication

Protected APIs require:

```http
Authorization: Bearer <JWT_TOKEN>
```

## Authentication APIs

| Method | Endpoint                | Description            |
| ------ | ----------------------- | ---------------------- |
| POST   | `/auth/register`        | Register user          |
| POST   | `/auth/login`           | Login                  |
| POST   | `/auth/refresh`         | Refresh JWT            |
| POST   | `/auth/logout`          | Logout                 |
| POST   | `/auth/forgot-password` | Request password reset |
| POST   | `/auth/reset-password`  | Reset password         |
| GET    | `/auth/verify-email`    | Verify email           |

## User APIs

| Method | Endpoint             | Description       |
| ------ | -------------------- | ----------------- |
| GET    | `/users/me`          | Get current user  |
| PUT    | `/users/me`          | Update user       |
| PUT    | `/users/me/password` | Change password   |
| GET    | `/users/me/storage`  | Get storage usage |

## File APIs

| Method | Endpoint            | Description        |
| ------ | ------------------- | ------------------ |
| POST   | `/files/upload`     | Upload file        |
| GET    | `/files/{id}`       | Download file      |
| DELETE | `/files/{id}`       | Move file to trash |
| PUT    | `/files/{id}`       | Update file        |
| GET    | `/files`            | List files         |
| GET    | `/files/search`     | Search files       |
| POST   | `/files/{id}/move`  | Move file          |
| POST   | `/files/{id}/share` | Share file         |

## Folder APIs

| Method | Endpoint             | Description          |
| ------ | -------------------- | -------------------- |
| POST   | `/folders`           | Create folder        |
| GET    | `/folders/{id}`      | Get folder           |
| DELETE | `/folders/{id}`      | Move folder to trash |
| PUT    | `/folders/{id}`      | Rename folder        |
| POST   | `/folders/{id}/move` | Move folder          |

## Trash APIs

| Method | Endpoint                | Description        |
| ------ | ----------------------- | ------------------ |
| GET    | `/trash`                | List trash         |
| POST   | `/trash/restore/{id}`   | Restore item       |
| DELETE | `/trash/permanent/{id}` | Permanently delete |
| DELETE | `/trash/empty`          | Empty trash        |

## Billing APIs

| Method | Endpoint         | Description      |
| ------ | ---------------- | ---------------- |
| GET    | `/billing/usage` | Get usage        |
| GET    | `/billing/plan`  | Get current plan |
| PUT    | `/billing/plan`  | Change plan      |

## Logger APIs

| Method | Endpoint         | Description          |
| ------ | ---------------- | -------------------- |
| GET    | `/logger/levels` | Get logger levels    |
| PUT    | `/logger/levels` | Update logger levels |
| GET    | `/logger/logs`   | Get recent logs      |

## Error Response

```json
{
  "timestamp": "2026-08-21T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/files/upload"
}
```

## HTTP Status Codes

| Status | Meaning                    |
| ------ | -------------------------- |
| 200    | Successful                 |
| 201    | Created                    |
| 204    | Successful without content |
| 400    | Bad Request                |
| 401    | Unauthorized               |
| 403    | Forbidden                  |
| 404    | Not Found                  |
| 409    | Conflict                   |
| 413    | Payload Too Large          |
| 429    | Too Many Requests          |
| 500    | Internal Server Error      |

## Rate Limiting

The API uses Bucket4j-based request throttling.

When the configured rate is exceeded:

```text
HTTP 429 Too Many Requests
```

is returned.

## Example File Upload

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -F "file=@example.pdf"
```

## Security

Never expose:

* JWT tokens
* Passwords
* Master keys
* Database passwords
* SMTP passwords
* Telegram bot tokens

in logs or API responses.
