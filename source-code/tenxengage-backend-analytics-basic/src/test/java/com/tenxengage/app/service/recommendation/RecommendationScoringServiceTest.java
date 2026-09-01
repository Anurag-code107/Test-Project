package com.tenxengage.app.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BUG-022 regression guard: the nightly scoring job was enumerating partner users via
 * the legacy user_roles and roles tables that were dropped in the role-system
 * consolidation. The SQL threw on every invocation, the outer try/catch swallowed it,
 * and recommendation_scores was never populated — so the Home page's tenX Suggestions
 * widget always showed the empty state for Partner Admin / Partner Seller.
 *
 * <p>This test captures the SQL string {@link RecommendationScoringService#loadPartnerUserIds}
 * passes to JdbcTemplate and asserts it references the current schema (client_roles +
 * base_role_name) and does not reference the dropped tables. A unit test against a
 * mocked JdbcTemplate is sufficient for this regression class — the bug is a SQL-against-
 * schema mismatch, and the assertion catches it without standing up Postgres.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationScoringServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RecommendationScoringService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationScoringService(jdbcTemplate, new ObjectMapper());
    }

    @Test
    void loadPartnerUserIds_joinsClientRolesOnClientRoleIdFilteringOnBaseRoleName() {
        UUID clientId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of());

        service.loadPartnerUserIds(clientId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(String.class), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertThat(sql)
                .as("Partner user lookup must JOIN client_roles on users.client_role_id — "
                        + "mirrors UserSeeder.resolvePartnerAdminRoleId. Without this JOIN, "
                        + "the old user_roles/roles tables get referenced and the SQL throws "
                        + "at runtime.")
                .contains("JOIN client_roles")
                .contains("u.client_role_id");

        assertThat(sql)
                .as("Role filter must use client_roles.base_role_name (stable internal token), "
                        + "not the old roles.name column that no longer exists.")
                .contains("base_role_name")
                .contains("'PARTNER_ADMIN'")
                .contains("'PARTNER_SELLER'");
    }

    @Test
    void loadPartnerUserIds_doesNotReferenceDroppedUserRolesOrRolesTables() {
        UUID clientId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of());

        service.loadPartnerUserIds(clientId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(String.class), any(Object[].class));
        String sql = sqlCaptor.getValue();

        assertThat(sql)
                .as("BUG-022: the dropped user_roles table must not appear in the SQL — "
                        + "JDBC throws 'relation user_roles does not exist' if it does, "
                        + "which is exactly the silent-failure path behind the empty "
                        + "tenX Suggestions widget.")
                .doesNotContain("JOIN user_roles")
                .doesNotContain("FROM user_roles")
                .doesNotContain("user_roles.");

        assertThat(sql)
                .as("BUG-022: the dropped roles table must not appear either. "
                        + "Careful: 'roles' substring-matches 'client_roles', so the "
                        + "check is scoped to JOIN/FROM with trailing space/newline.")
                .doesNotContain("JOIN roles ")
                .doesNotContain("JOIN roles\n")
                .doesNotContain("FROM roles ")
                .doesNotContain("FROM roles\n");
    }

    @Test
    void loadPartnerUserIds_filtersToActiveUsersOnly() {
        UUID clientId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of());

        service.loadPartnerUserIds(clientId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(String.class), any(Object[].class));

        assertThat(sqlCaptor.getValue())
                .as("Anonymised / deactivated partner users must stay out of the scoring "
                        + "pool — the status='ACTIVE' filter is load-bearing.")
                .contains("u.status = 'ACTIVE'");
    }
}
