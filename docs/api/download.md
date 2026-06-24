# Download API

## Purpose
Retrieve metadata for a single file (used before download or preview).

## Base path
`/download`

## Endpoints

### GET /download/{id}
- Purpose: Get `MasterFile` by id.
- Input: Path variable `id` (string)
- Output: `MasterFile` entity
  - `id`, `userId`, `name`, `fileId`, `size`, `contentType`, `createdAt`
