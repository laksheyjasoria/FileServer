# Drive API

## Purpose
List user's files in the drive.

## Base path
`/drive`

## Endpoints

### GET /drive
- Purpose: Return a list of files owned by the authenticated user.
- Input: `Authentication` (current user automatically resolved)
- Output: `List<MasterFile>` where `MasterFile` contains:
  - `id`, `userId`, `name`, `fileId`, `size`, `contentType`, `createdAt`
