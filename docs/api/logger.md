# Logger API

## Purpose
Manage loggers (create/update/delete) and accept public log/error events.

## Base path
`/logger`

## Endpoints

### POST /logger/create
- Purpose: Create a named logger (master key required).
- Input: query param `name` (string)
- Output: `String` — created logger id

### PUT /logger/{id}
- Purpose: Update logger flags (master key required).
- Input: path var `id`, query params `info` (boolean), `warn` (boolean)
- Output: void

### DELETE /logger/{id}
- Purpose: Delete a logger (master key required).
- Input: path var `id`
- Output: void

### POST /logger/log
- Purpose: Public API to submit a log entry.
- Input: query params `loggerId`, `level`, `message`
- Output: void

### POST /logger/error
- Purpose: Public API to submit an error entry.
- Input: query params `loggerId`, `message`
- Output: void
