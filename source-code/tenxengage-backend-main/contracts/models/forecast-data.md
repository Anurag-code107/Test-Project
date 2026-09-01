# Forecast Data Models

Supporting data tables for the AI forecasting engine. These are pre-aggregated from raw data and fed to the prediction model.

## Entity: ForecastAccuracyRecord

Tracks forecast accuracy by comparing predictions to actual outcomes.

| Field                      | Type     | Required | Constraints | Notes                        |
| -------------------------- | -------- | -------- | ----------- | ---------------------------- |
| id                         | UUID     | yes      | primary key |                              |
| clientId                   | UUID     | yes      | foreign key |                              |
| incentiveId                | UUID     | yes      | —           |                              |
| forecastId                 | UUID     | yes      | —           | References IncentiveForecast |
| predictedRoi               | decimal  | no       | —           |                              |
| actualRoi                  | decimal  | no       | —           |                              |
| predictedNetNewDeals       | integer  | no       | —           |                              |
| actualNetNewDeals          | integer  | no       | —           |                              |
| predictedNetNewBookings    | decimal  | no       | —           |                              |
| actualNetNewBookings       | decimal  | no       | —           |                              |
| predictedParticipationRate | decimal  | no       | —           |                              |
| actualParticipationRate    | decimal  | no       | —           |                              |
| predictedBudgetUtilPct     | decimal  | no       | —           |                              |
| actualBudgetUtilPct        | decimal  | no       | —           |                              |
| bookingsErrorPct           | decimal  | no       | —           |                              |
| roiErrorPct                | decimal  | no       | —           |                              |
| participationErrorPct      | decimal  | no       | —           |                              |
| overallAccuracyScore       | decimal  | no       | —           |                              |
| modelVersion               | string   | no       | max 20      |                              |
| evaluatedAt                | datetime | yes      | —           |                              |
| createdAt                  | datetime | yes      | —           |                              |

## Entity: ForecastIncentiveOutcome

Historical incentive outcomes used as training data for predictions.

| Field                         | Type     | Required | Constraints | Notes |
| ----------------------------- | -------- | -------- | ----------- | ----- |
| id                            | UUID     | yes      | primary key |       |
| clientId                      | UUID     | yes      | foreign key |       |
| incentiveId                   | UUID     | yes      | —           |       |
| incentiveType                 | string   | yes      | —           |       |
| name                          | string   | yes      | —           |       |
| startDate                     | date     | yes      | —           |       |
| endDate                       | date     | yes      | —           |       |
| durationDays                  | integer  | no       | —           |       |
| totalBudget                   | decimal  | no       | —           |       |
| actualUtilizationRate         | decimal  | no       | —           |       |
| actualParticipationCount      | integer  | no       | —           |       |
| actualParticipationRate       | decimal  | no       | —           |       |
| actualRevenue                 | decimal  | no       | —           |       |
| actualCost                    | decimal  | no       | —           |       |
| actualRoi                     | decimal  | no       | —           |       |
| productCategories             | string   | no       | TEXT        |       |
| targetLocationValueIds        | jsonb    | no       | —           |       |
| payoutType                    | string   | no       | max 20      |       |
| avgPayoutValue                | decimal  | no       | —           |       |
| partnerTypes                  | string   | no       | TEXT        |       |
| actualLiftPct                 | decimal  | no       | —           |       |
| claimRate                     | decimal  | no       | —           |       |
| avgDaysToClaim                | integer  | no       | —           |       |
| budgetExhaustionPctAtMidpoint | decimal  | no       | —           |       |
| createdAt                     | datetime | yes      | —           |       |

## Entity: ForecastSalesAggregate

Monthly sales aggregates by location and product category.

| Field           | Type     | Required | Constraints | Notes          |
| --------------- | -------- | -------- | ----------- | -------------- |
| id              | UUID     | yes      | primary key |                |
| clientId        | UUID     | yes      | foreign key |                |
| locationValueId | UUID     | no       | —           |                |
| productCategory | string   | no       | max 100     |                |
| yearMonth       | date     | yes      | —           | First of month |
| dealCount       | integer  | yes      | default 0   |                |
| totalRevenue    | decimal  | no       | —           |                |
| avgDealSize     | decimal  | no       | —           |                |
| uniquePartners  | integer  | yes      | default 0   |                |
| createdAt       | datetime | yes      | —           |                |

## Entity: ForecastRegionDistribution

Partner and revenue distribution by location for forecast weighting.

| Field                 | Type     | Required | Constraints   | Notes |
| --------------------- | -------- | -------- | ------------- | ----- |
| id                    | UUID     | yes      | primary key   |       |
| clientId              | UUID     | yes      | foreign key   |       |
| locationValueId       | UUID     | no       | —             |       |
| activePartnerCount    | integer  | yes      | default 0     |       |
| trailing12mRevenue    | decimal  | no       | —             |       |
| trailing12mOrderCount | integer  | no       | —             |       |
| revenueWeight         | decimal  | no       | precision 5,4 |       |
| createdAt             | datetime | yes      | —             |       |

## Entity: ForecastTrainingCorrelation

Correlation between training completion and sales performance.

| Field                    | Type     | Required | Constraints | Notes |
| ------------------------ | -------- | -------- | ----------- | ----- |
| id                       | UUID     | yes      | primary key |       |
| clientId                 | UUID     | yes      | foreign key |       |
| productCategory          | string   | yes      | max 100     |       |
| trainedSellerCount       | integer  | no       | —           |       |
| untrainedSellerCount     | integer  | no       | —           |       |
| trainedAvgDealSize       | decimal  | no       | —           |       |
| untrainedAvgDealSize     | decimal  | no       | —           |       |
| trainedAvgDealCount      | integer  | no       | —           |       |
| untrainedAvgDealCount    | integer  | no       | —           |       |
| dataDrivenLiftPct        | decimal  | no       | —           |       |
| organicTrainingLiftPct   | decimal  | no       | —           |       |
| incentiveTrainingLiftPct | decimal  | no       | —           |       |
| sampleSize               | integer  | no       | —           |       |
| createdAt                | datetime | yes      | —           |       |

## Notes

- All forecast data entities are tenant-scoped via `client_id`
- Data is pre-aggregated by the ForecastAggregationService (triggered manually or on schedule)
- These tables feed compact summaries to the Claude API for intelligent prediction
