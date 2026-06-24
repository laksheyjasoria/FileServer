# Upload API

## Purpose
Simple single-request file upload endpoint (multipart).

## Base path
`/upload`

## Endpoints

### POST /upload
- Purpose: Upload a file in a single multipart request.
- Input: multipart form `file` (MultipartFile)
- Output: `String` — returns a String from `UploadService.upload()` (likely file id or job id)
