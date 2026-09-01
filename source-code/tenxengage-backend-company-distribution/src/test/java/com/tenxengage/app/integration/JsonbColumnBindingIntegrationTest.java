package com.tenxengage.app.integration;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.BuilderFieldConfig;
import com.tenxengage.app.entity.BuilderSectionConfig;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.repository.BuilderFieldConfigRepository;
import com.tenxengage.app.repository.BuilderSectionConfigRepository;
import com.tenxengage.app.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-011 regression guard: Hibernate must bind String fields annotated with
 * {@code @Column(columnDefinition = "jsonb")} using {@code @JdbcTypeCode(SqlTypes.JSON)}
 * — otherwise parameters are sent as VARCHAR and Postgres rejects the INSERT with
 * {@code column ... is of type jsonb but expression is of type character varying}.
 *
 * <p>The local Postgres that {@link AbstractLocalIntegrationTest} points at uses a
 * JDBC URL with {@code ?stringtype=unspecified}, which makes the driver stop
 * strict-typing string params and accept them for jsonb columns. That setting would
 * mask this bug. This test overrides the URL without that flag so the strict-typing
 * behavior applies and the test genuinely fails on pre-fix code.
 *
 * <p>Exercises two of the nine affected entities as a representative sample —
 * {@code BuilderFieldConfig.valueSourceConfig} (the direct repro from the bug report)
 * and {@code User.metadata} (a different entity with the same defect). Passing both
 * demonstrates the fix is applied consistently, not cherry-picked to one call site.
 */
@Tag("integration")
@Transactional
@DirtiesContext
class JsonbColumnBindingIntegrationTest extends AbstractLocalIntegrationTest {

    @DynamicPropertySource
    static void strictJdbcUrl(DynamicPropertyRegistry registry) {
        // Drop the ?stringtype=unspecified flag so Postgres applies strict varchar→jsonb
        // rejection, reproducing the production JDBC URL behavior the bug was caught under.
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:5432/tenxengage");
    }

    @Autowired private BuilderFieldConfigRepository fieldConfigRepository;
    @Autowired private BuilderSectionConfigRepository sectionConfigRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void builderFieldConfig_savesWithJsonbValueSourceConfig() {
        BuilderSectionConfig section = sectionConfigRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No seeded BuilderSectionConfig — V3 seed did not run."));

        BuilderFieldConfig field = BuilderFieldConfig.builder()
                .fieldKey("jsonb-regression-" + UUID.randomUUID())
                .displayName("Jsonb Regression")
                .fieldType("TEXT_BOX")
                .helperText("regression guard for BUG-011")
                .valueSourceConfig("{\"options\":[\"a\",\"b\"]}")
                .sectionConfig(section)
                .build();

        BuilderFieldConfig saved = fieldConfigRepository.saveAndFlush(field);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getValueSourceConfig()).contains("\"options\"");
    }

    @Test
    void user_savesWithJsonbMetadata() {
        User user = userRepository.findByEmail("clientadmin@acme.com")
                .orElseThrow(() -> new AssertionError(
                        "Seeded Client Admin user is missing — V3 seed did not run."));

        user.setMetadata("{\"preferredLocale\":\"en-US\",\"jsonbRegression\":true}");
        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getMetadata()).contains("\"jsonbRegression\":true");
    }
}
