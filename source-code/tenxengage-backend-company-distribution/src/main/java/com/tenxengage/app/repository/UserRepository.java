package com.tenxengage.app.repository;

import com.tenxengage.app.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.clientRole WHERE u.id = :id")
    Optional<User> findByIdWithClientRole(@Param("id") UUID id);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.clientId = :clientId")
    Optional<User> findByIdAndClientId(@Param("id") UUID id, @Param("clientId") UUID clientId);

    boolean existsByEmail(String email);

    @Query("""
        SELECT u FROM User u
        WHERE (:search IS NULL OR :search = ''
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE u.clientId = :clientId
        AND (:search IS NULL OR :search = ''
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<User> searchByClientId(@Param("clientId") UUID clientId,
                                @Param("search") String search,
                                Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE u.clientId = :clientId
        AND u.partnerCompanyId = :partnerCompanyId
        AND (:search IS NULL OR :search = ''
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<User> searchByClientIdAndPartnerCompanyId(
        @Param("clientId") UUID clientId,
        @Param("partnerCompanyId") UUID partnerCompanyId,
        @Param("search") String search,
        Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE u.clientId = :clientId
        AND u.partnerCompanyId IS NULL
        AND (:search IS NULL OR :search = ''
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<User> searchInternalByClientId(@Param("clientId") UUID clientId,
                                         @Param("search") String search,
                                         Pageable pageable);

    @Query("SELECT u.partnerCompanyId, COUNT(u) FROM User u " +
           "WHERE u.clientId = :clientId AND u.status = com.tenxengage.app.entity.enums.UserStatus.ACTIVE " +
           "AND u.partnerCompanyId IN :partnerCompanyIds GROUP BY u.partnerCompanyId")
    List<Object[]> countActiveUsersByPartnerCompanyIds(@Param("clientId") UUID clientId,
                                                       @Param("partnerCompanyIds") List<UUID> partnerCompanyIds);

    /**
     * Find active users by client and clientRole base role names (e.g. "CLIENT_ADMIN", "PARTNER_SELLER").
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.clientRole cr
        WHERE u.clientId = :clientId
        AND cr.baseRoleName IN :baseRoleNames
        AND u.status = com.tenxengage.app.entity.enums.UserStatus.ACTIVE
        """)
    List<User> findByClientIdAndBaseRoleNames(@Param("clientId") UUID clientId,
                                               @Param("baseRoleNames") Set<String> baseRoleNames);

    /**
     * Active PARTNER_SELLERs of one partner company — the only people who can receive a company
     * distribution (design OQ-14). Scoping to the seller role also satisfies OQ-7 structurally: a partner
     * admin can never appear in their own recipient list, because they are not a seller.
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.clientRole cr
        WHERE u.clientId = :clientId
        AND u.partnerCompanyId = :partnerCompanyId
        AND cr.baseRoleName = 'PARTNER_SELLER'
        AND u.status = com.tenxengage.app.entity.enums.UserStatus.ACTIVE
        ORDER BY u.firstName, u.lastName
        """)
    List<User> findActiveSellersOfCompany(@Param("clientId") UUID clientId,
                                          @Param("partnerCompanyId") UUID partnerCompanyId);

    /**
     * Find active users by client and clientRole base role names, with audience filtering for incentives.
     * Uses native SQL because partner_type is now stored in JSONB metadata.
     */
    @Query(nativeQuery = true, value = """
        SELECT DISTINCT u.* FROM users u
        JOIN client_roles cr ON cr.id = u.client_role_id
        LEFT JOIN partner_companies pc ON pc.id = u.partner_company_id
        WHERE u.client_id = :clientId
        AND cr.base_role_name IN (:baseRoleNames)
        AND u.status = 'ACTIVE'
        AND (
            pc.id IS NULL
            OR NOT EXISTS (
                SELECT 1 FROM incentive_audience_rules iar
                WHERE iar.incentive_id = :incentiveId AND iar.rule_type = 'LOCATION'
            )
            OR EXISTS (
                SELECT 1 FROM partner_company_locations pcl
                JOIN incentive_audience_rules iar ON iar.rule_value = CAST(pcl.location_value_id AS text)
                WHERE pcl.partner_company_id = pc.id
                AND iar.incentive_id = :incentiveId AND iar.rule_type = 'LOCATION'
            )
        )
        AND (
            pc.id IS NULL
            OR NOT EXISTS (
                SELECT 1 FROM incentive_audience_rules iar
                WHERE iar.incentive_id = :incentiveId AND iar.rule_type = 'PARTNER_TYPE'
            )
            OR pc.metadata->>'Partner Type' IN (
                SELECT iar.rule_value FROM incentive_audience_rules iar
                WHERE iar.incentive_id = :incentiveId AND iar.rule_type = 'PARTNER_TYPE'
            )
        )
        """)
    List<User> findByClientIdAndBaseRoleNamesWithAudienceFilter(
        @Param("clientId") UUID clientId,
        @Param("baseRoleNames") Set<String> baseRoleNames,
        @Param("incentiveId") UUID incentiveId);

    List<User> findByClientRoleId(UUID clientRoleId);

    List<User> findByClientIdAndPartnerCompanyId(UUID clientId, UUID partnerCompanyId);

    /**
     * Active PARTNER_ADMIN users of a single partner company within a tenant.
     * Used to scope COMPANY-wallet balance-expiry notifications to the owning company's admins.
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN u.clientRole cr
        WHERE u.clientId = :clientId
        AND u.partnerCompanyId = :partnerCompanyId
        AND cr.baseRoleName = 'PARTNER_ADMIN'
        AND u.status = com.tenxengage.app.entity.enums.UserStatus.ACTIVE
        """)
    List<User> findActivePartnerAdminsByCompany(@Param("clientId") UUID clientId,
                                                @Param("partnerCompanyId") UUID partnerCompanyId);
}
