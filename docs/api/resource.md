# Resource API

## Purpose
Perform actions against resources (move/delete/etc) in batch.

## Base path
`/resources`

## Endpoints

### POST /resources/action
- Purpose: Execute an action on resource ids.
- Input (JSON body - `ResourceActionRequest`):
  - `action` (enum `ResourceAction`)
  - `ids` (List<String>)
  - `destination` (String, optional)
- Output: void
