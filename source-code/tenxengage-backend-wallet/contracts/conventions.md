# Shared Conventions

Both backend and frontend teams MUST follow these conventions. Any deviation requires updating this document first.

## URL Conventions

- Base path: `/api/v1/`
- Style: Clean REST — use HTTP methods, not verb suffixes
  - `GET /api/v1/users` — list (paginated)
  - `POST /api/v1/users` — create
  - `GET /api/v1/users/{id}` — get by ID
  - `PUT /api/v1/users/{id}` — full update
  - `PATCH /api/v1/users/{id}` — partial update
  - `DELETE /api/v1/users/{id}` — delete
- URL segments: kebab-case (`/api/v1/user-profiles`, not `/api/v1/userProfiles`)
- Resource names: plural (`/users`, not `/user`)

## JSON Conventions

- Field names: camelCase (`firstName`, not `first_name`)
- IDs: UUID v4 strings (`"550e8400-e29b-41d4-a716-446655440000"`)
- Dates: ISO 8601 with timezone (`"2026-03-02T14:30:00Z"`)
- Nulls: omit null fields from responses (don't send `"field": null`)
- Booleans: use `true`/`false` (not `0`/`1` or `"yes"`/`"no"`)
- Money: use string or BigDecimal representation to avoid floating point issues (`"amount": "1234.56"`)

## Pagination

### Request Parameters

| Param         | Type    | Default   | Description              |
| ------------- | ------- | --------- | ------------------------ |
| page          | integer | 0         | Zero-based page index    |
| pageSize      | integer | 20        | Items per page (max 100) |
| sortBy        | string  | createdAt | Field to sort by         |
| sortDirection | string  | DESC      | ASC or DESC              |
| search        | string  | —         | Optional search term     |

### Response Shape

```json
{
  "data": [],
  "page": 0,
  "pageSize": 20,
  "totalElements": 142,
  "totalPages": 8,
  "hasNext": true,
  "hasPrevious": false
}
```

## Error Responses

All 4xx and 5xx responses use this shape:

```json
{
  "errorCode": "NOT_FOUND",
  "errorMessage": "User with id '...' not found",
  "status": 404,
  "timestamp": "2026-03-02T14:30:00Z",
  "path": "/api/v1/users/123"
}
```

### Validation Errors (400)

```json
{
  "errorCode": "VALIDATION_FAILED",
  "errorMessage": "Request validation failed",
  "status": 400,
  "timestamp": "2026-03-02T14:30:00Z",
  "path": "/api/v1/users",
  "details": {
    "email": "must be a valid email address",
    "firstName": "must not be blank"
  }
}
```

## Authentication & Authorization

- Header: `Authorization: Bearer <jwt-token>`
- Multi-tenant header: `X-Client-Subdomain: <subdomain>`
- Token type: JWT (HS256)
- Token expiry: access token = 1 hour, refresh token = 7 days (httpOnly cookie)

### Permission-Based Access Control

All protected endpoints use `@RequiresPermission` annotations (not role-based checks).

Permission key format: `action.<resource>.<operation>`

Examples:

- `action.incentive.view`, `action.incentive.create`, `action.incentive.edit`
- `action.users.view`, `action.users.create`, `action.users.delete`
- `action.tenx.clients.view` (platform-level, TENX_ADMIN only)

### 5-Layer Permission Hierarchy

Effective permissions are resolved top-down. Each layer can grant or revoke:

1. **Platform defaults** — Permission catalog defines the base set (124 permissions)
2. **Tenant grants** (ClientPermissionGrant) — TENX_ADMIN enables/disables per client
3. **Role permissions** (ClientRolePermission) — Per-tenant role grants
4. **Company overrides** (CompanyPermissionOverride) — Per-partner-company overrides
5. **User overrides** (UserPermissionOverride) — Per-individual-user overrides

A permission is effective only if granted at every applicable layer. Lower layers can further restrict but not expand beyond what the layer above allows.

## HTTP Status Codes

| Code | Usage                                      |
| ---- | ------------------------------------------ |
| 200  | Successful GET, PUT, PATCH, DELETE         |
| 201  | Successful POST (resource created)         |
| 204  | Successful DELETE (no content)             |
| 400  | Validation error                           |
| 401  | Missing or invalid authentication          |
| 403  | Authenticated but insufficient permissions |
| 404  | Resource not found                         |
| 409  | Conflict (duplicate resource)              |
| 422  | Business rule violation                    |
| 500  | Internal server error                      |
