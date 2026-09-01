package com.tenxengage.app.batch.seed;

import com.tenxengage.app.batch.seed.SeedRecords.SellerRef;
import com.tenxengage.app.batch.seed.SeedRecords.UserCreationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.tenxengage.app.batch.seed.SeedConstants.*;

/**
 * Creates users for enrolled partner companies. Roles are resolved against client_roles
 * and assigned via users.client_role_id (no separate user_roles join table).
 */
@Component
public class UserSeeder {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final JdbcTemplate jdbc;

    public UserSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Creates users for enrolled partners. First user per partner gets admin role,
     * remaining get seller role.
     * BUG FIX: uses client_roles + client_role_id instead of dropped roles/user_roles.
     */
    public UserCreationResult createUsers(UUID clientId, List<UUID> partnerIds,
                                          UUID partnerAdminRoleId, UUID partnerSellerRoleId,
                                          Map<UUID, Timestamp> partnerCreationDates,
                                          Random random) {
        List<SellerRef> allSellers = new ArrayList<>();
        Map<UUID, Timestamp> userCreationDates = new HashMap<>();
        List<Object[]> userBatch = new ArrayList<>();
        LocalDate seedEnd = LocalDate.now();
        Timestamp seedEndTs = Timestamp.from(seedEnd.atStartOfDay(ZoneOffset.UTC).toInstant());
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        int userCount = 0;
        for (int p = 0; p < partnerIds.size(); p++) {
            UUID partnerId = partnerIds.get(p);
            Timestamp partnerCreated = partnerCreationDates.getOrDefault(partnerId,
                    Timestamp.from(FiscalQuarterCalculator.getSeedStartDate()
                            .atStartOfDay(ZoneOffset.UTC).toInstant()));
            int numUsers = MIN_USERS_PER_PARTNER
                    + random.nextInt(MAX_USERS_PER_PARTNER - MIN_USERS_PER_PARTNER + 1);
            for (int u = 0; u < numUsers; u++) {
                UUID userId = UUID.randomUUID();
                String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
                String last = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
                String email = first.toLowerCase() + "." + last.toLowerCase()
                        + "." + (userCount + 1) + "@example.com";

                int daysAfterPartner = (u == 0) ? random.nextInt(7) : random.nextInt(91);
                long userEpochMs = partnerCreated.getTime() + (long) daysAfterPartner * 86_400_000L;
                Timestamp userCreatedAt = new Timestamp(Math.min(userEpochMs, seedEndTs.getTime()));
                userCreationDates.put(userId, userCreatedAt);

                String passwordHash = encoder.encode(UUID.randomUUID().toString());

                UUID roleId = (u == 0) ? partnerAdminRoleId : partnerSellerRoleId;
                userBatch.add(new Object[]{
                        userId, email, first, last, passwordHash, "ACTIVE",
                        clientId, partnerId, roleId, userCreatedAt, userCreatedAt
                });
                allSellers.add(new SellerRef(userId, partnerId));
                userCount++;
            }
        }

        batchInsert("INSERT INTO users (id, email, first_name, last_name, password_hash, status, " +
                "client_id, partner_company_id, client_role_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", userBatch);

        log.info("Created {} users for {} partners", userCount, partnerIds.size());
        return new UserCreationResult(allSellers, userCreationDates);
    }

    /**
     * Resolve client_role IDs using the client_roles table.
     * BUG FIX: replaces old SELECT from dropped 'roles' table.
     */
    public UUID resolvePartnerAdminRoleId(UUID clientId) {
        return jdbc.queryForObject(
                "SELECT id FROM client_roles WHERE client_id = ? AND base_role_name = 'PARTNER_ADMIN' AND is_system = true",
                UUID.class, clientId);
    }

    public UUID resolvePartnerSellerRoleId(UUID clientId) {
        return jdbc.queryForObject(
                "SELECT id FROM client_roles WHERE client_id = ? AND base_role_name = 'PARTNER_SELLER' AND is_system = true",
                UUID.class, clientId);
    }

    /**
     * Resolve CLIENT_ADMIN user for created_by FK on incentives.
     * BUG FIX: uses client_roles instead of dropped roles/user_roles tables.
     */
    public UUID resolveAdminUserId(UUID clientId) {
        return jdbc.queryForObject(
                "SELECT u.id FROM users u " +
                "JOIN client_roles cr ON u.client_role_id = cr.id " +
                "WHERE u.client_id = ? AND cr.base_role_name = 'CLIENT_ADMIN' LIMIT 1",
                UUID.class, clientId);
    }

    private void batchInsert(String sql, List<Object[]> batch) {
        for (int i = 0; i < batch.size(); i += BATCH_SIZE) {
            List<Object[]> chunk = batch.subList(i, Math.min(i + BATCH_SIZE, batch.size()));
            jdbc.batchUpdate(sql, chunk);
        }
    }
}
