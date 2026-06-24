# Chunk Upload API

## Purpose
Support large file uploads via chunked upload workflow.

## Base path
`/chunk-upload`

## Endpoints

### POST /chunk-upload/create
- Purpose: Initialize a chunked upload job.
- Input (JSON body - `CreateUploadRequest`):
  - `fileName` (string)
  - `totalSize` (long)
  - `totalChunks` (int)
  - Authenticated user (Authentication)
- Output: `UploadJob` entity
  - `id`, `userId`, `fileName`, `totalSize`, `totalChunks`, `uploadedChunks`, `status`, `createdAt`

### POST /chunk-upload/{uploadId}/{chunkIndex}
- Purpose: Upload a single chunk for an existing upload job.
- Input:
  - Path vars: `uploadId` (string), `chunkIndex` (int)
  - multipart form `file` (MultipartFile)
- Output: void (204)

### GET /chunk-upload/{uploadId}/resume
- Purpose: Return list of already-uploaded chunks for resumable clients.
- Input: path var `uploadId`
- Output: `List<UploadChunk>`
  - `id`, `uploadJobId`, `chunkIndex`, `telegramFileId`, `size`

### GET /chunk-upload/{uploadId}/status
- Purpose: Get progress/status summary for an upload job.
- Input: path var `uploadId`
- Output: `ChunkUploadResponse`:
  - `uploadId`, `uploadedChunks`, `totalChunks`, `status`

### DELETE /chunk-upload/{uploadId}
- Purpose: Cancel and cleanup an upload job.
- Input: path var `uploadId`
- Output: void
