# FileServer — Production-Ready Chunked File Transfer Implementation Plan

**Document status:** Architecture and implementation baseline approved  
**Date:** 2026-09-06  
**Repository:** https://github.com/laksheyjasoria/FileServer.git  
**Primary goal:** Build a production-ready, Google-Drive-like upload/download/preview/streaming system while preserving all existing FileServer functionality.

---

## 1. HANDOFF INSTRUCTION FOR ANOTHER AI

This document is the authoritative implementation baseline for the chunked file transfer work.

If another AI receives this document, it should **continue implementation from this point** and must NOT redesign the architecture unless a concrete technical blocker is found.

The user wants:

- Complete working implementation, not snippets.
- Complete copy-and-replace files whenever an existing file is modified.
- No omitted sections such as imports, package declarations, annotations, helper methods, etc.
- Existing functionality must continue to work.
- Telegram is the **only storage backend** for this feature.
- Do **not** introduce local filesystem storage for chunks/files.
- Main branch must remain stable.
- New development belongs on `feature/chunked-file-transfer`.
- Optional safety branch: `baseline/pre-chunking`.
- Upload must behave like Google Drive:
  - resumable
  - pause/resume
  - cancel
  - retry failed chunks
  - actual byte-level progress
  - upload speed
  - ETA
  - server-authoritative progress
  - recovery after browser/network restart where possible
- Download must support HTTP Range requests and progressive transfer.
- Preview must be inline and must not expose a download mechanism.
- Audio/video streaming must support HTTP Range and seeking.
- Browser loading/progress indicators must be supported.
- Legacy non-chunked files must continue working.

### Important security/product limitation

"Preview/streaming cannot be downloaded" means:

- No `Content-Disposition: attachment`.
- No Telegram file URL exposed to frontend.
- No Telegram file ID exposed to frontend.
- No download endpoint used by preview/stream UI.
- Preview/stream APIs should only provide inline/range data.

However, once bytes are delivered to a browser, a sufficiently capable user can technically capture or reconstruct those bytes. The application cannot make received bytes mathematically impossible to save.

---

# 2. CURRENT REPOSITORY BASELINE

Repository:

`https://github.com/laksheyjasoria/FileServer.git`

Current stack:

- Spring Boot 3.2.4
- Java 17
- PostgreSQL
- Redis
- Flyway
- Telegram storage
- JPA/Hibernate
- Existing JWT/auth/security infrastructure
- Existing chunked/resumable upload infrastructure

Existing upload configuration includes approximately:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2GB

app:
  upload:
    max-file-size: 2147483648
    chunk-size-mb: 50
    resumable: true
```

The existing chunk-size configuration must be replaced/extended with a dynamic chunk-size policy. Do not assume 50 MB is suitable for the new transfer engine.

---

# 3. TELEGRAM CONSTRAINT THAT DRIVES THE DESIGN

The standard Telegram Bot API currently has:

- document upload limit: 50 MB
- `getFile` download limit: 20 MB

Therefore a production implementation using the normal Bot API must ensure that each logical Telegram chunk is **no larger than 20 MB** if that chunk must later be retrieved through the Bot API.

### Decision

Use **adaptive chunk sizing with a hard maximum of 20 MB** for the standard Bot API implementation.

Do NOT simply hard-code:

```text
20 MB for every file
```

Instead calculate the chunk size from the file size, while respecting configured minimum/default/maximum limits.

Suggested initial policy:

```yaml
app:
  upload:
    chunk:
      min-size-mb: 5
      default-size-mb: 20
      max-size-mb: 20
```

Suggested behavior:

| File size | Suggested behavior |
|---|---|
| <= 5 MB | one chunk |
| > 5 MB and <= 20 MB | one chunk |
| > 20 MB | multiple chunks, maximum 20 MB each |

The implementation should be centralized in a `ChunkSizeCalculator` or equivalent service rather than duplicated throughout controllers/services.

### Why not MTProto right now?

MTProto can provide true partial file retrieval using offsets/limits and is technically excellent for range streaming. However, it introduces additional Telegram client/API/session/dependency complexity.

For the current free production-oriented implementation, the baseline decision is:

**Standard Telegram Bot API + adaptive chunks capped at 20 MB.**

If a later requirement demands larger logical Telegram chunks or lower retrieval overhead, MTProto or a Local Bot API Server can be evaluated separately. Do not switch to those technologies silently.

---

# 4. TARGET USER EXPERIENCE

## 4.1 Upload

Example:

```text
movie.mkv

Uploading...

████████████████░░░░░░░░ 68%

340 MB / 500 MB
18.5 MB/s
~9 seconds remaining

[ Pause ] [ Cancel ]
```

The displayed progress must be based on bytes, not simply chunk count.

For example:

```text
uploadedBytes = 340000000
totalBytes    = 500000000

progress = uploadedBytes / totalBytes * 100
```

Chunk count is useful for server state but is not sufficient for user-visible progress.

---

## 4.2 Pause

Pause should stop scheduling new chunk uploads.

Already completed Telegram chunks remain stored.

Database state should remain resumable.

Example:

```text
status = PAUSED
```

Resume continues from missing chunks rather than restarting the file.

---

## 4.3 Resume

The client must ask the server what was actually received.

Example:

```http
GET /chunk-upload/{uploadId}/resume
```

Response should contain enough information to reconstruct state, such as:

```json
{
  "uploadId": "uuid",
  "fileName": "movie.mkv",
  "totalSize": 524288000,
  "chunkSize": 20971520,
  "totalChunks": 25,
  "uploadedChunks": [0,1,2,3,4,5],
  "uploadedBytes": 125829120,
  "status": "UPLOADING"
}
```

The client then uploads missing chunks.

The server must be authoritative.

Do not trust the browser to remember which chunks succeeded.

---

## 4.4 Cancel

Cancel must be explicit and idempotent.

Suggested behavior:

```text
UPLOADING -> CANCELLED
PAUSED    -> CANCELLED
```

Cancellation must not accidentally delete unrelated files.

For a cancelled incomplete upload, cleanup should remove:

- UploadJob
- UploadChunk records
- associated Telegram chunk objects where the storage API supports deletion

If Telegram deletion is not safely available, the system should at minimum mark the upload cancelled and remove all references so abandoned storage can be handled by a controlled cleanup policy.

Do not implement dangerous "delete everything by filename" behavior.

---

## 4.5 Retry

Individual failed chunks should be retryable.

Example:

```text
Chunk 0 ✓
Chunk 1 ✓
Chunk 2 ✗ -> retry
Chunk 3 ✓
Chunk 4 ✓
```

A failed chunk must not invalidate successful chunks.

Use bounded retries and meaningful server/client errors.

---

# 5. DATABASE MODEL

## 5.1 MasterFile

Current `MasterFile` contains fields similar to:

```text
id
userId
name
fileId
size
contentType
parentId
driveType
accessType
createdAt
childrenCount
active
deletedAt
```

Add:

```text
uploadJobId
```

This identifies a file whose physical storage is composed of Telegram chunks.

### Backward compatibility

Use:

```text
fileId != null && uploadJobId == null
    -> legacy file

uploadJobId != null
    -> chunked file
```

Do not break legacy records.

---

## 5.2 UploadJob

Current fields:

```text
id UUID
userId
fileName
totalSize
totalChunks
uploadedChunks
status
createdAt
```

Extend it with fields needed for authoritative progress and resumability:

```text
chunkSize
uploadedBytes
updatedAt
```

Potentially:

```text
contentType
parentId
```

if required to create the final MasterFile correctly.

Do not duplicate data unnecessarily if it already exists in MasterFile.

---

## 5.3 UploadChunk

Current fields:

```text
id UUID
uploadJobId
chunkIndex
telegramFileId
size
```

Recommended additions:

```text
status
createdAt
updatedAt
```

Status can support states such as:

```text
UPLOADING
COMPLETED
FAILED
```

Only add fields that are actually needed by the final implementation.

---

# 6. MASTERFILE ↔ UPLOADJOB ASSOCIATION

The existing chunk upload flow creates an UploadJob but does not currently create/associate a MasterFile.

This must be fixed.

Recommended flow:

```text
POST /chunk-upload/create
        |
        v
Create UploadJob
        |
        v
Create MasterFile
        |
        v
MasterFile.uploadJobId = UploadJob.id
        |
        v
Return upload session information
```

The MasterFile should exist with enough metadata to be displayed/managed by the drive UI.

When all chunks are uploaded:

```text
UploadJob.status = COMPLETED
MasterFile.active = true
```

The exact timing of `active` should match the existing application's semantics.

Do not expose an incomplete upload as a normal completed file.

---

# 7. UPLOAD API TARGET

Existing endpoints:

```http
POST   /chunk-upload/create
POST   /chunk-upload/{uploadId}/{chunkIndex}
GET    /chunk-upload/{uploadId}/resume
GET    /chunk-upload/{uploadId}/status
DELETE /chunk-upload/{uploadId}
```

These endpoints should remain compatible unless a change is genuinely required.

---

## 7.1 Create upload

Request currently contains:

```text
fileName
totalSize
totalChunks
```

The new implementation should preferably let the server calculate authoritative:

```text
chunkSize
totalChunks
```

from:

```text
file size
```

Do not trust a client-provided chunk size if the server policy says otherwise.

Possible request:

```json
{
  "fileName": "movie.mkv",
  "totalSize": 524288000
}
```

The server calculates:

```text
chunkSize
totalChunks
```

If backward compatibility requires accepting `totalChunks`, validate it against the calculated value instead of blindly trusting it.

---

# 8. BYTE-LEVEL PROGRESS

The system must track:

```text
totalBytes
uploadedBytes
```

not only:

```text
totalChunks
uploadedChunks
```

When a chunk completes:

```text
uploadedBytes += chunk.size
uploadedChunks += 1
```

But the update must be safe against duplicate requests.

If the same chunk is submitted twice:

```text
do not increment uploadedBytes twice
do not increment uploadedChunks twice
```

The unique logical key is:

```text
(uploadJobId, chunkIndex)
```

The database should enforce this if practical.

---

# 9. CONCURRENCY AND IDEMPOTENCY

Production upload clients may send requests concurrently.

The implementation must handle:

```text
chunk 1 request
chunk 1 retry
chunk 2 request
chunk 3 request
```

without corrupting counters.

Critical requirement:

```text
A completed chunk must be counted exactly once.
```

Use transaction boundaries and/or database constraints where appropriate.

Do not rely only on:

```java
if (!exists) {
    save();
    uploadedBytes += size;
}
```

if concurrent requests can race.

The final implementation must make the update atomic or otherwise race-safe.

---

# 10. TELEGRAM STORAGE LAYER

Existing storage abstraction:

```java
String upload(MultipartFile file);
String upload(byte[] data, String fileName);
byte[] download(String fileId);
```

The old `byte[] download()` behavior should remain for legacy paths unless there is a strong reason to change it.

Do not replace the entire legacy storage mechanism just to add chunked transfer.

Instead introduce a new capability for range/streaming retrieval.

For example:

```text
TelegramChunkReader
```

or:

```text
TelegramFileReader
```

The exact class name can be chosen during implementation, but its responsibility must be narrow:

> Retrieve bytes belonging to one Telegram-stored chunk without exposing Telegram details to controllers/frontend.

---

# 11. RANGE TRANSFER ARCHITECTURE

Introduce a transfer layer.

Recommended classes:

```text
transfer/
    ByteRange.java
    RangeRequest.java
    FileTransferService.java
    ChunkRangeService.java
    FileTransferMode.java
```

And a Telegram reader:

```text
telegram/
    TelegramFileReader.java
```

The exact package can follow the repository's existing package organization.

---

# 12. LOGICAL FILE RANGE RESOLUTION

A chunked file is logically one file:

```text
File
 ├── Chunk 0
 ├── Chunk 1
 ├── Chunk 2
 └── ...
```

Suppose:

```text
chunkSize = 20 MB
```

A request:

```http
Range: bytes=25000000-35000000
```

means:

```text
Chunk 1:
    local offset = 4,? MB

Chunk 1 + Chunk 2:
    return required bytes
```

The `ChunkRangeService` must translate:

```text
logical file byte range
```

into:

```text
chunk index
chunk-local offset
number of bytes
```

It must support ranges that cross chunk boundaries.

---

# 13. HTTP RANGE BEHAVIOR

For a valid range:

```http
206 Partial Content
Accept-Ranges: bytes
Content-Range: bytes START-END/TOTAL
Content-Length: LENGTH
```

For no range:

```text
stream the complete file progressively
```

Do not build the entire file in a `byte[]`.

For an invalid range:

```http
416 Range Not Satisfiable
```

with an appropriate:

```http
Content-Range: bytes */TOTAL
```

---

# 14. DOWNLOAD API

Existing:

```http
GET /download/{fileId}
```

must remain.

Behavior:

### Legacy file

```text
MasterFile.fileId != null
uploadJobId == null
```

Use existing Telegram download behavior, while improving memory use if safely possible.

### Chunked file

```text
MasterFile.uploadJobId != null
```

Use:

```text
ChunkRangeService
```

and progressive HTTP streaming.

Download response:

```http
Content-Disposition: attachment
```

Download supports:

```text
Range
206
416
Accept-Ranges
```

---

# 15. PREVIEW API

Create:

```http
GET /preview/{fileId}
```

Behavior:

```text
Content-Disposition: inline
Content-Type: MasterFile.contentType
```

Fallback:

```text
application/octet-stream
```

For chunked files:

```text
ChunkRangeService
```

must provide the bytes.

Preview must NOT:

- expose Telegram URL
- expose Telegram file ID
- return attachment disposition
- redirect directly to Telegram
- call the download API from the frontend

---

# 16. STREAM API

Create:

```http
GET /stream/{fileId}
```

Use the same transfer/range infrastructure.

Required headers:

```http
Accept-Ranges: bytes
Content-Type: audio/* or video/*
Content-Length: ...
Content-Range: ...
Content-Disposition: inline
```

The browser/media player should be able to issue:

```http
Range: bytes=...
```

for seeking.

Do not implement a separate storage engine for streaming.

Use:

```text
StreamController
    ->
FileTransferService
    ->
ChunkRangeService
    ->
TelegramFileReader
```

---

# 17. DO NOT DUPLICATE RANGE LOGIC

The following should NOT each implement their own range calculation:

```text
DownloadController
PreviewController
StreamController
```

They should delegate to a common transfer service.

Preferred architecture:

```text
DownloadController
       |
PreviewController
       |
StreamController
       |
       v
FileTransferService
       |
       v
ChunkRangeService
       |
       v
TelegramFileReader
       |
       v
Telegram
```

This prevents subtle differences between download, preview, and streaming.

---

# 18. LOADING UX

The backend should return correct HTTP semantics so the frontend can display loading states.

Upload UI:

```text
Uploading
Progress %
Uploaded / Total
Speed
ETA
Pause
Resume
Cancel
Retry
```

Preview UI:

```text
Loading preview...
```

Streaming UI:

```text
Loading...
Buffering...
```

Download UI:

```text
Preparing download...
Downloading...
```

Do not fake progress.

Upload progress must use actual acknowledged bytes.

---

# 19. SECURITY

Every transfer endpoint must verify ownership/access using the application's existing authorization model.

Do not introduce a shortcut such as:

```text
GET /stream/{id}
```

that bypasses existing permissions.

For shared files, reuse the existing sharing/access logic where appropriate.

Do not expose:

```text
telegramFileId
bot token
Telegram API URL
```

to clients.

---

# 20. ERROR HANDLING

Expected errors:

```text
404 -> file/upload does not exist
403 -> unauthorized
400 -> malformed request
409 -> invalid upload state/conflict
416 -> invalid HTTP range
500 -> unexpected storage/backend error
```

For Telegram failures, do not expose internal Telegram credentials/details.

Return a safe API error.

---

# 21. LEGACY COMPATIBILITY

This is mandatory.

Existing files may have:

```text
MasterFile.fileId
```

and no:

```text
uploadJobId
```

They must continue to work.

Do not migrate every old file into the new chunk system automatically.

The transfer layer should branch:

```text
if uploadJobId != null:
    chunked path

else if fileId != null:
    legacy Telegram path

else:
    invalid/missing storage reference
```

---

# 22. MIGRATION

Create a Flyway migration for:

```text
master_files.upload_job_id
```

and any additional required upload fields.

Do not invent a migration version without first inspecting the actual repository's migration sequence.

Current known migrations include:

```text
V1.0.1__add_user_status.sql
V1.0.3__add_incoming_share_privacy.sql
V1.0.4__add_user_search_indexes.sql
```

The implementation AI must inspect the repository before creating the next version.

Do not add an unnecessary foreign key if it creates deployment-order or lifecycle problems. A nullable logical association is acceptable initially.

---

# 23. RECOMMENDED IMPLEMENTATION PHASES

## Phase 1 — Database + MasterFile association

Implement:

- `MasterFile.uploadJobId`
- Flyway migration
- `UploadJob.chunkSize`
- `UploadJob.uploadedBytes`
- `UploadJob.updatedAt`
- required `UploadChunk` state fields if necessary

Goal:

```text
MasterFile <-> UploadJob <-> UploadChunk
```

works correctly.

---

## Phase 2 — Production resumable upload

Implement:

- server-side chunk-size calculation
- authoritative totalChunks
- byte progress
- idempotent chunk completion
- concurrency-safe counters
- pause/resume state
- cancel
- retry behavior
- accurate status endpoint

Goal:

```text
Upload -> interruption -> resume
```

continues from missing chunks.

---

## Phase 3 — Telegram transfer primitive

Implement:

```text
TelegramFileReader
```

or equivalent.

Goal:

```text
read bytes from one Telegram chunk
```

without exposing Telegram internals to controllers.

Because standard Bot API download is limited to 20 MB, the chunk-size policy must ensure each stored chunk is <=20 MB.

---

## Phase 4 — ChunkRangeService

Implement logical range mapping.

Input:

```text
MasterFile
Range
```

Output:

```text
ordered byte segments from Telegram chunks
```

Must support:

- start/end range
- open-ended range
- suffix range
- cross-chunk ranges
- full-file streaming

---

## Phase 5 — Download

Upgrade:

```http
GET /download/{id}
```

to support:

- legacy files
- chunked files
- Range
- 206
- 416
- progressive streaming
- correct headers
- no full-file byte[] for chunked files

---

## Phase 6 — Preview

Add:

```http
GET /preview/{id}
```

Use:

```text
Content-Disposition: inline
```

and common range engine.

---

## Phase 7 — Live streaming

Add:

```http
GET /stream/{id}
```

Use:

- HTTP Range
- 206
- Accept-Ranges
- inline disposition
- media content type
- seeking
- progressive transfer

Goal:

```text
video/audio starts without waiting for entire file
```

---

# 24. EXPECTED FILES TO CHANGE

Before editing, inspect the repository and confirm exact package paths.

Known existing files likely involved:

```text
src/main/java/com/app/master/entity/MasterFile.java

src/main/java/com/app/orchestrator/UploadOrchestrator.java

src/main/java/com/app/upload/service/ChunkService.java

src/main/java/com/app/upload/controller/ChunkUploadController.java

src/main/java/com/app/upload/dto/CreateUploadRequest.java

src/main/java/com/app/storage/service/TelegramStorageService.java

src/main/java/com/app/telegram/TelegramClient.java

src/main/java/com/app/drive/controller/DownloadController.java

src/main/java/com/app/drive/service/DownloadService.java

src/main/java/com/app/master/repository/MasterFileRepository.java

src/main/java/com/app/upload/repository/UploadChunkRepository.java

src/main/java/com/app/upload/repository/UploadJobRepository.java
```

Potential new files:

```text
src/main/java/com/app/transfer/ByteRange.java

src/main/java/com/app/transfer/RangeRequest.java

src/main/java/com/app/transfer/FileTransferService.java

src/main/java/com/app/transfer/ChunkRangeService.java

src/main/java/com/app/transfer/FileTransferMode.java

src/main/java/com/app/telegram/TelegramFileReader.java

src/main/java/com/app/drive/controller/PreviewController.java

src/main/java/com/app/drive/controller/StreamController.java
```

Potentially:

```text
ChunkSizeCalculator.java
```

and DTOs for upload status/resume.

The exact package structure must follow the actual repository.

---

# 25. TESTING REQUIREMENTS

Do not consider the feature complete just because Maven compiles.

Test at minimum:

## Upload

- small file
- exactly one chunk
- exactly 20 MB
- 20 MB + 1 byte
- large file
- final partial chunk
- duplicate chunk request
- concurrent duplicate chunk request
- interrupted upload
- resume
- pause
- cancel
- retry
- incorrect chunk index
- incorrect chunk size
- unauthorized upload

## Progress

Verify:

```text
uploadedBytes
uploadedChunks
totalBytes
totalChunks
progress
```

remain correct after retries/resume.

## Download

Test:

```text
no Range
Range: bytes=0-999
Range: bytes=1000-1999
Range: bytes=0-
Range: bytes=-1000
cross-chunk range
invalid range
```

Verify:

```text
200 / 206 / 416
Content-Length
Content-Range
Accept-Ranges
Content-Disposition
```

## Preview

Verify:

```text
inline
correct content type
Range support
no Telegram URL
no Telegram file ID
```

## Streaming

Verify:

```text
video/mp4
audio/*
Range
206
seeking
multiple range requests
```

## Backward compatibility

Test an old MasterFile that only has:

```text
fileId
```

and no:

```text
uploadJobId
```

It must still download normally.

---

# 26. PERFORMANCE REQUIREMENTS

Do not load an entire large file into memory.

Bad:

```java
byte[] data = storage.download(...);
```

for a multi-hundred-MB/GB chunked file.

Preferred:

```text
Telegram -> small transfer buffer -> HTTP response
```

Memory should remain approximately bounded regardless of logical file size.

Use buffered streaming.

Avoid creating one huge byte array for:

```text
500 MB
1 GB
2 GB
```

files.

---

# 27. PRODUCTION CONCURRENCY

The system should be safe when:

```text
multiple users upload simultaneously
```

and when:

```text
one user uploads multiple files
```

and when:

```text
one upload has multiple chunks uploading concurrently
```

Do not use global mutable upload state.

Everything must be keyed by:

```text
uploadId
```

and authenticated user.

---

# 28. CLEANUP / ORPHAN HANDLING

Production systems need cleanup for:

```text
abandoned uploads
failed uploads
cancelled uploads
expired sessions
```

A later cleanup job can find stale UploadJobs.

Recommended policy:

```text
UPLOADING/PAUSED older than configured timeout
    -> mark expired
    -> cleanup Telegram chunks
    -> cleanup DB records
```

Do not implement aggressive automatic cleanup in the first migration if it risks deleting valid resumable uploads.

Make expiration configurable.

---

# 29. API CONTRACT SUMMARY

Final target APIs:

### Upload

```http
POST   /chunk-upload/create
POST   /chunk-upload/{uploadId}/{chunkIndex}
GET    /chunk-upload/{uploadId}/resume
GET    /chunk-upload/{uploadId}/status
DELETE /chunk-upload/{uploadId}
```

### Download

```http
GET /download/{fileId}
```

### Preview

```http
GET /preview/{fileId}
```

### Stream

```http
GET /stream/{fileId}
```

---

# 30. TARGET STATE MACHINE

Upload:

```text
                 ┌──────────────┐
                 │   CREATED    │
                 └──────┬───────┘
                        │
                        v
                 ┌──────────────┐
          ┌─────>│  UPLOADING   │<─────┐
          │      └──────┬───────┘      │
          │             │              │
       resume          pause          retry
          │             │              │
          │             v              │
          │      ┌──────────────┐      │
          └──────│    PAUSED    │──────┘
                 └──────┬───────┘
                        │
                     cancel
                        │
                        v
                 ┌──────────────┐
                 │  CANCELLED   │
                 └──────────────┘

UPLOADING
    |
    | all chunks complete
    v
COMPLETED
```

The actual existing `UploadStatus` enum must be inspected before adding/changing states.

---

# 31. IMPORTANT IMPLEMENTATION RULES

1. Do not remove working legacy Telegram functionality.
2. Do not introduce local filesystem storage.
3. Do not expose Telegram URLs/file IDs.
4. Do not trust client progress.
5. Do not count duplicate chunks twice.
6. Do not load large chunked files entirely into memory.
7. Do not duplicate range logic across controllers.
8. Do not bypass authorization.
9. Do not silently change the Telegram retrieval architecture.
10. Do not invent migration versions.
11. Inspect the actual repository before editing.
12. Run tests after each major phase.
13. Prefer complete copy-replace files when giving implementation output.
14. Preserve existing API behavior unless the change is explicitly required.
15. Keep `main` stable and implement on `feature/chunked-file-transfer`.

---

# 32. GIT WORKFLOW

Before implementation:

```bash
git status
git add .
git commit -m "chore: save working state before chunked transfer"
git branch baseline/pre-chunking
git checkout -b feature/chunked-file-transfer
mvn test
```

Then implement phase by phase.

Recommended commits:

```text
feat(upload): associate master files with upload jobs
feat(upload): add adaptive chunk sizing and byte progress
feat(upload): make resumable upload concurrency safe
feat(telegram): add chunk transfer reader
feat(transfer): add logical range resolution
feat(download): add range-aware chunked downloads
feat(preview): add inline chunked preview
feat(stream): add range-based media streaming
test(transfer): add chunked transfer integration tests
```

Do not merge into `main` until the full regression suite passes.

---

# 33. DEFINITION OF DONE

The feature is complete only when all of these are true:

### Upload

- [ ] Dynamic chunk size calculated from file size.
- [ ] Chunk size never exceeds 20 MB under standard Bot API mode.
- [ ] Server calculates/validates total chunks.
- [ ] MasterFile is associated with UploadJob.
- [ ] UploadChunk records map correctly.
- [ ] Real uploaded bytes are tracked.
- [ ] Progress is accurate.
- [ ] Pause works.
- [ ] Resume works.
- [ ] Cancel works.
- [ ] Retry works.
- [ ] Duplicate chunk uploads are idempotent.
- [ ] Concurrent chunk uploads do not corrupt counters.

### Download

- [ ] Legacy files work.
- [ ] Chunked files work.
- [ ] Full download works.
- [ ] Range works.
- [ ] 206 is correct.
- [ ] 416 is correct.
- [ ] Large files are streamed without full-file memory allocation.

### Preview

- [ ] Inline response.
- [ ] Correct MIME type.
- [ ] Range support.
- [ ] Telegram details hidden.
- [ ] No attachment response.

### Streaming

- [ ] Audio/video starts progressively.
- [ ] Range requests work.
- [ ] Seeking works.
- [ ] Inline response.
- [ ] Telegram details hidden.

### Security

- [ ] Ownership/access checks preserved.
- [ ] No Telegram credentials exposed.
- [ ] No Telegram URLs exposed.
- [ ] No storage bypass.

### Operations

- [ ] Stale upload cleanup strategy exists.
- [ ] Logging is useful.
- [ ] Errors are safe and actionable.
- [ ] Tests cover critical paths.
- [ ] `main` remains unaffected until release.

---

# 34. IMPLEMENTATION ORDER FOR THE NEXT AI

When continuing from this document, follow this exact order:

```text
1. Inspect current repository state.
2. Inspect pom.xml/build configuration.
3. Inspect Telegram classes and configuration.
4. Inspect all upload entities/repositories/services/controllers.
5. Inspect MasterFile creation flows outside chunk upload.
6. Inspect current Flyway migration numbering.
7. Inspect security/access checks.
8. Inspect frontend upload/download/preview code.
9. Create implementation branch.
10. Implement Phase 1.
11. Run tests.
12. Implement Phase 2.
13. Run tests.
14. Implement Phase 3.
15. Run tests.
16. Implement Phase 4.
17. Run tests.
18. Implement Phase 5.
19. Run tests.
20. Implement Phase 6.
21. Run tests.
22. Implement Phase 7.
23. Run full regression tests.
24. Review API/security/memory behavior.
25. Only then consider merging to main.
```

Do not skip repository inspection.

---

# 35. FINAL ARCHITECTURE

```text
                         ┌─────────────────────┐
                         │       Browser       │
                         └──────────┬──────────┘
                                    │
             ┌──────────────────────┼──────────────────────┐
             │                      │                      │
           Upload                Download              Preview/Stream
             │                      │                      │
             ▼                      ▼                      ▼
       Chunk Upload API       Range Transfer API       Range Transfer API
             │                      │                      │
             ▼                      └──────────┬───────────┘
         UploadJob                           │
             │                               ▼
             ▼                       FileTransferService
        UploadChunk                         │
             │                               ▼
             └───────────────►      ChunkRangeService
                                            │
                                            ▼
                                      Telegram Reader
                                            │
                                            ▼
                                         Telegram
```

Storage remains:

```text
Telegram only
```

There is no new local file storage layer.

The result should provide:

```text
Google Drive-like upload
        +
real progress
        +
pause/resume
        +
cancel/retry
        +
efficient range downloads
        +
inline preview
        +
seekable live streaming
        +
legacy compatibility
        +
production-grade authorization
```

---

## STATUS

**Architecture decision: COMPLETE**

**Implementation: NOT YET STARTED**

**Next action: Repository inspection followed by Phase 1 implementation.**

Do not treat this document as proof that the code has already been implemented. It is the approved design/implementation baseline from which coding should begin.
