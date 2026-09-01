# TaggingJob

Tracks eligibility tagging jobs that analyze sales POs against incentive rules.

## Fields

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| id | UUID | No | Primary key |
| clientId | UUID | No | FK to clients |
| status | TaggingJobStatus | No | RUNNING, COMPLETED, or FAILED |
| posAnalyzed | int | No | Number of POs analyzed |
| eligibleDeals | int | No | Number of deals found eligible |
| incentivesMatched | int | No | Number of incentives matched |
| errorMessage | String | Yes | Error details if status is FAILED |
| createdAt | Instant | No | When the job was created |
| updatedAt | Instant | No | When the job was last updated |

## Response DTO: TaggingJobResponse

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Job ID |
| status | TaggingJobStatus | Job status |
| posAnalyzed | int | POs analyzed |
| eligibleDeals | int | Eligible deals |
| incentivesMatched | int | Incentives matched |
| errorMessage | String? | Error message if failed |
| createdAt | Instant | Timestamp |
