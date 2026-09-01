# DataUpload

Tracks file uploads and connector data pulls for data objects.

## Fields

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| id | UUID | No | Primary key |
| clientId | UUID | No | FK to clients |
| dataObjectId | UUID | No | FK to data_objects |
| fileName | String | No | Name of uploaded file or connector sync label |
| source | DataUploadSource | No | MANUAL or CONNECTOR |
| status | DataUploadStatus | No | PROCESSING, COMPLETED, or FAILED |
| totalRows | int | No | Total rows processed |
| newRows | int | No | New records inserted |
| updatedRows | int | No | Existing records updated |
| skippedRows | int | No | Duplicate records skipped |
| errorMessage | String | Yes | Error details if status is FAILED |
| createdAt | Instant | No | When the upload was created |
| updatedAt | Instant | No | When the upload was last updated |

## Response DTO: DataUploadResponse

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Upload ID |
| fileName | String | File name or sync label |
| source | DataUploadSource | Upload source |
| status | DataUploadStatus | Upload status |
| totalRows | int | Total rows |
| newRows | int | New records |
| updatedRows | int | Updated records |
| skippedRows | int | Skipped duplicates |
| errorMessage | String? | Error message if failed |
| createdAt | Instant | Timestamp |
