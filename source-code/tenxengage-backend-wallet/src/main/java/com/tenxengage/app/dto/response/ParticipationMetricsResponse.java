package com.tenxengage.app.dto.response;

public record ParticipationMetricsResponse(
    boolean partnerFiltered,
    // Global view (when partnerFiltered = false)
    MetricResponse partnerCompaniesEnrolled,
    MetricResponse partnerUsersEnrolled,
    MetricResponse companiesEarningRewards,
    // Partner view (when partnerFiltered = true)
    MetricResponse partnerEnrolledUsers,
    MetricResponse usersEarningRewards,
    MetricResponse userClaimsMade
) {

    public static ParticipationMetricsResponse global(
            MetricResponse partnerCompaniesEnrolled,
            MetricResponse partnerUsersEnrolled,
            MetricResponse companiesEarningRewards) {
        return new ParticipationMetricsResponse(
            false,
            partnerCompaniesEnrolled,
            partnerUsersEnrolled,
            companiesEarningRewards,
            null, null, null
        );
    }

    public static ParticipationMetricsResponse forPartner(
            MetricResponse partnerEnrolledUsers,
            MetricResponse usersEarningRewards,
            MetricResponse userClaimsMade) {
        return new ParticipationMetricsResponse(
            true,
            null, null, null,
            partnerEnrolledUsers,
            usersEarningRewards,
            userClaimsMade
        );
    }
}
