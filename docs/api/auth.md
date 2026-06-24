# Auth API

## Purpose
Authentication and password management (register, login, forgot/reset password).

## Base path
`/auth`

## Endpoints

### POST /auth/register
- Purpose: Register a new user.
- Input (JSON body - `RegisterRequest`):
  - `email` (string, valid email, required)
  - `password` (string, min 6 chars, required)
  - `name` (string, required)
- Output: `ApiResponse<String>` — success wrapper containing a String (user id or token as returned by orchestrator).

### POST /auth/login
- Purpose: Authenticate and receive a session token.
- Input (JSON body - `LoginRequest`):
  - `email` (string)
  - `password` (string)
- Output: `ApiResponse<String>` — success wrapper containing authentication token (string).

### POST /auth/forgot-password
- Purpose: Send password reset link to email.
- Input (form/query param): `email` (string)
- Output: `ApiResponse<String>` — message: "Reset email sent"

### POST /auth/reset-password
- Purpose: Reset password using reset token.
- Input (form/query params):
  - `token` (string)
  - `password` (string)
- Output: `ApiResponse<String>` — message: "Password updated"

### GET /auth/me
- Purpose: Return the current authenticated user's details (validate token).
- Input: `Authorization: Bearer <token>` header. Authentication is resolved by Spring Security.
- Output: `ApiResponse<User>` — `User` fields returned (note: `password` is excluded):
  - `id` (long)
  - `email` (string)
  - `name` (string)
  - `role` (string)
  - `enabled` (boolean)
  - `createdAt` (datetime)
