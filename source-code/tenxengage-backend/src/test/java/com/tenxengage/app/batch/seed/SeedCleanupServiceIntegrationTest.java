package com.tenxengage.app.batch.seed;

import com.tenxengage.app.AbstractLocalIntegrationTest;
import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.LocationLevel;
import com.tenxengage.app.entity.LocationValue;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.PartnerCompanyLocation;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.UserStatus;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.LocationLevelRepository;
import com.tenxengage.app.repository.LocationValueRepository;
import com.tenxengage.app.repository.PartnerCompanyLocationRepository;
import com.tenxengage.app.repository.PartnerCompanyRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.testdata.ClientFixtures;
import com.tenxengage.app.testdata.PartnerFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-018 regression guard: {@link SeedCleanupService#cleanupAllData(UUID)} must preserve
 * {@code partner_company_locations} rows for baseline partners that are still referenced
 * by login users, in the same way the sibling {@code partner_companies} DELETE already
 * does. Without that guard, every reseed silently wiped TechPartners Inc's single
 * location assignment and broke the LOCATION branch of incentive eligibility for
 * {@code partneradmin@techpartners.com} and {@code seller@techpartners.com}.
 */
@Tag("integration")
@Transactional
class SeedCleanupServiceIntegrationTest extends AbstractLocalIntegrationTest {

    /**
     * Skip Flyway's checksum validation for this test's context so a local DB whose
     * schema history drifted from the current migrations (e.g. after an interleaving
     * commit edited V1/V2/V3) still lets the test assert cleanup behavior. Production
     * config keeps validation ON; this override is scoped to the test classloader.
     */
    @DynamicPropertySource
    static void relaxFlywayValidation(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.validate-on-migrate", () -> "false");
    }

    @Autowired private SeedCleanupService seedCleanupService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private PartnerCompanyRepository partnerCompanyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LocationLevelRepository locationLevelRepository;
    @Autowired private LocationValueRepository locationValueRepository;
    @Autowired private PartnerCompanyLocationRepository partnerCompanyLocationRepository;

    @PersistenceContext private EntityManager entityManager;

    @Test
    void cleanupAllData_preservesLocationAssignmentForUserReferencedPartner() {
        Client client = clientRepository.save(ClientFixtures.activeEnterprise().build());
        UUID clientId = client.getId();

        LocationLevel regionLevel = locationLevelRepository.save(LocationLevel.builder()
                .clientId(clientId).name("Region").depth(0).build());
        LocationValue americas = locationValueRepository.save(LocationValue.builder()
                .clientId(clientId).level(regionLevel).name("AMERICAS").code("AMERICAS").build());

        // Baseline partner: .example.com contact email AND referenced by a seeded login user.
        // Mirrors TechPartners Inc + partneradmin@techpartners.com from V3 baseline.
        PartnerCompany baselinePartner = partnerCompanyRepository.save(PartnerFixtures
                .activeReseller(clientId)
                .metadata("{\"Partner Type\":\"RESELLER\","
                        + "\"Contact Email\":\"info@techpartners.example.com\"}")
                .build());
        userRepository.save(User.builder()
                .email("partneradmin-" + UUID.randomUUID() + "@techpartners.com")
                .firstName("Baseline").lastName("User")
                .passwordHash("$2a$10$placeholder")
                .status(UserStatus.ACTIVE)
                .clientId(clientId)
                .partnerCompanyId(baselinePartner.getId())
                .build());
        PartnerCompanyLocation baselineLocation = partnerCompanyLocationRepository.save(
                PartnerCompanyLocation.builder()
                        .clientId(clientId)
                        .partnerCompany(baselinePartner)
                        .locationValue(americas)
                        .build());

        // Orphan partner: same .example.com email pattern but no user references it.
        // Mirrors a dynamically-seeded partner that should legitimately be cleaned up.
        PartnerCompany orphanPartner = partnerCompanyRepository.save(PartnerFixtures
                .activeReseller(clientId)
                .metadata("{\"Partner Type\":\"RESELLER\","
                        + "\"Contact Email\":\"info@orphan.example.com\"}")
                .build());
        PartnerCompanyLocation orphanLocation = partnerCompanyLocationRepository.save(
                PartnerCompanyLocation.builder()
                        .clientId(clientId)
                        .partnerCompany(orphanPartner)
                        .locationValue(americas)
                        .build());

        // Flush JPA inserts so SeedCleanupService's raw JDBC DELETEs can see the rows
        // we just saved (otherwise they sit in the persistence context and cleanup
        // runs against an empty DB view, trivially passing for the wrong reason).
        entityManager.flush();

        seedCleanupService.cleanupAllData(clientId);

        // Clear the persistence context so subsequent findById calls hit the DB
        // rather than returning stale cached entities that cleanup already deleted.
        entityManager.clear();

        assertThat(partnerCompanyLocationRepository.findById(baselineLocation.getId()))
                .as("Baseline partner is referenced by a login user — its location row "
                        + "must survive cleanup (BUG-018 regression).")
                .isPresent();
        assertThat(partnerCompanyLocationRepository.findById(orphanLocation.getId()))
                .as("Orphan partner is not referenced by any user — its location row "
                        + "should still be cleaned up so dynamic reseeding starts fresh.")
                .isEmpty();
    }
}
