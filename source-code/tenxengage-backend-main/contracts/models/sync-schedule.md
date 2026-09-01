# SyncSchedule

Configurable auto-sync schedule per data object for connector data pulls.

## Fields

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| id | UUID | No | Primary key |
| clientId | UUID | No | FK to clients |
| dataObjectId | UUID | No | FK to data_objects (unique per client) |
| enabled | boolean | No | Whether auto-sync is active |
| cadence | SyncCadence | No | MANUAL, HOURLY, DAILY, WEEKLY, or MONTHLY |
| lastRunAt | Instant | Yes | Last successful sync time |
| nextRunAt | Instant | Yes | Next scheduled sync time |
| createdAt | Instant | No | When the schedule was created |
| updatedAt | Instant | No | When the schedule was last updated |

## Response DTO: SyncScheduleResponse

| Field | Type | Description |
|-------|------|-------------|
| id | UUID? | Schedule ID (null if no schedule exists yet) |
| dataObjectId | UUID | Data object ID |
| enabled | boolean | Whether sync is enabled |
| cadence | SyncCadence | Sync frequency |
| lastRunAt | Instant? | Last sync time |
| nextRunAt | Instant? | Next scheduled sync time |

## Request DTO: UpdateSyncScheduleRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| enabled | boolean | Yes | Enable/disable auto-sync |
| cadence | SyncCadence | Yes | Sync frequency |
