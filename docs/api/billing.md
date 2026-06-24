# Billing API

## Purpose
Manage plans and assign subscriptions to users.

## Base path
`/billing`

## Endpoints

### POST /billing/plan
- Purpose: Create a billing plan.
- Input (JSON body - `CreatePlanRequest`):
  - `name` (string)
  - `storageLimitBytes` (long)
  - `maxUploadSizeBytes` (long)
  - `dailyUploadLimit` (int)
  - `apiRequestLimit` (int)
  - `price` (decimal)
- Output: `Plan` entity
  - `id`, `name`, `storageLimitBytes`, `maxUploadSizeBytes`, `dailyUploadLimit`, `apiRequestLimit`, `price`, `active`

### POST /billing/assign
- Purpose: Assign a plan to a user.
- Input (JSON body - `AssignPlanRequest`):
  - `userId` (string)
  - `planId` (string)
  - `validityDays` (int)
- Output: `Subscription` entity
  - `id`, `userId`, `plan` (Plan), `startDate`, `expiryDate`, `active`
