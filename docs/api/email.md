# Email API

## Purpose
Send emails using the system mailer.

## Base path
`/email`

## Endpoints

### POST /email/send
- Purpose: Send an email message.
- Input (JSON body - `EmailRequest`):
  - `to` (List<String>, required)
  - `cc` (List<String>, optional)
  - `bcc` (List<String>, optional)
  - `subject` (string, required)
  - `body` (string, required)
  - `html` (boolean)
- Output: `ApiResponse<String>` — message: "Email sent successfully"
