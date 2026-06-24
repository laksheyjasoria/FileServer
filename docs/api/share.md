# Share API

## Purpose
Create share links for files and validate access via token (with optional password).

## Base path
`/share`

## Endpoints

### POST /share
- Purpose: Create a share link for a file (authenticated user).
- Input (JSON body - `CreateShareRequest`):
  - `fileId` (string)
  - `publicAccess` (boolean)
  - `password` (string, optional)
  - `expiry` (LocalDateTime, optional)
  - Authenticated user (Authentication)
- Output: `ShareResponse`:
  - `url`, `token`

### GET /share/{token}
- Purpose: Validate access and return the shared resource.
- Input: path var `token`, optional query param `password`
- Output: `SharedResource` entity
  - `id`, `token`, `fileId`, `createdBy`, `publicAccess`, `password`, `expiry`, `createdAt`
