package com.tenxengage.app.dto.response;

import com.tenxengage.app.entity.Incentive;
import com.tenxengage.app.entity.enums.IncentiveStatus;
import com.tenxengage.app.entity.enums.IncentiveType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit coverage for the {@link IncentiveDetailResponse#from} factory.
 * Guards the wiring of audience-tier passthrough fields that are persisted
 * on the entity but easy to forget on the DTO. BUG-046: an earlier revision
 * of this DTO and {@code IncentiveService.createIncentive}/{@code updateIncentive}
 * lacked any wiring for {@code customFieldValues} — values entered by the
 * user via Builder Config-defined audience fields were silently dropped on
 * save and never surfaced on read. This test fails if either the record
 * field or the {@code from()} mapping for {@code customFieldValues}
 * regresses.
 */
class IncentiveDetailResponseTest {

    @Test
    void from_carriesCustomFieldValuesFromEntityToDto() {
        String json = "{\"campaignSegment\":\"Q4 Push\",\"region\":\"AMER\"}";
        Incentive incentive = baseIncentive();
        incentive.setCustomFieldValues(json);

        IncentiveDetailResponse response = IncentiveDetailResponse.from(
                incentive, "Test User", null, null);

        assertThat(response.customFieldValues()).isEqualTo(json);
    }

    @Test
    void from_returnsNullCustomFieldValuesWhenEntityHasNone() {
        Incentive incentive = baseIncentive();
        // customFieldValues left null — typical for tenants without configured fields

        IncentiveDetailResponse response = IncentiveDetailResponse.from(
                incentive, "Test User", null, null);

        assertThat(response.customFieldValues()).isNull();
    }

    @Test
    void from_carriesPeerAudienceFieldsAlongsideCustomFieldValues() {
        Incentive incentive = baseIncentive();
        incentive.setCountriesText("US, CA");
        incentive.setSpecificPartners("Acme Corp, Globex");
        incentive.setCustomFieldValues("{\"campaignSegment\":\"Q4 Push\"}");

        IncentiveDetailResponse response = IncentiveDetailResponse.from(
                incentive, "Test User", null, null);

        assertThat(response.countriesText()).isEqualTo("US, CA");
        assertThat(response.specificPartners()).isEqualTo("Acme Corp, Globex");
        assertThat(response.customFieldValues()).isEqualTo("{\"campaignSegment\":\"Q4 Push\"}");
    }

    private static Incentive baseIncentive() {
        Incentive incentive = Incentive.builder()
                .name("Test Incentive")
                .description("desc")
                .incentiveType(IncentiveType.SALES)
                .status(IncentiveStatus.DRAFT)
                .clientId(UUID.randomUUID())
                .createdBy(UUID.randomUUID())
                .startDate(Instant.now())
                .endDate(Instant.now().plusSeconds(86400))
                .build();
        incentive.setId(UUID.randomUUID());
        return incentive;
    }
}
