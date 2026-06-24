# Webhook API

## Purpose
Manage and create webhooks for events (register URLs and secrets per user).

## Base path
`/webhooks`

## Endpoints

### POST /webhooks
- Purpose: Create a webhook for the authenticated user.
- Input (JSON body - `CreateWebhookRequest`):
  - `url` (string)
  - `secret` (string)
  - Authenticated user (Authentication)
- Output: `Webhook` entity
  - `id`, `userId`, `url`, `secret`, `enabled`, `retryCount`, `createdAt`
