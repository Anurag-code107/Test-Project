package com.tenxengage.app.repository;

import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.enums.WalletType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RewardWalletRepository extends JpaRepository<RewardWallet, UUID> {

    List<RewardWallet> findByClientIdAndUserIdAndWalletType(UUID clientId, UUID userId, WalletType walletType);

    Optional<RewardWallet> findByClientIdAndUserIdAndCurrencyIdAndWalletType(
            UUID clientId, UUID userId, String currencyId, WalletType walletType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM RewardWallet w WHERE w.clientId = :clientId AND w.userId = :userId " +
           "AND w.currencyId = :currencyId AND w.walletType = :walletType")
    Optional<RewardWallet> findForUpdate(
            @Param("clientId") UUID clientId,
            @Param("userId") UUID userId,
            @Param("currencyId") String currencyId,
            @Param("walletType") WalletType walletType);

    List<RewardWallet> findByClientIdAndPartnerCompanyIdAndWalletType(
            UUID clientId, UUID partnerCompanyId, WalletType walletType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM RewardWallet w WHERE w.clientId = :clientId AND w.partnerCompanyId = :partnerCompanyId " +
           "AND w.currencyId = :currencyId AND w.walletType = :walletType")
    Optional<RewardWallet> findForUpdateByCompany(
            @Param("clientId") UUID clientId,
            @Param("partnerCompanyId") UUID partnerCompanyId,
            @Param("currencyId") String currencyId,
            @Param("walletType") WalletType walletType);

    List<RewardWallet> findByClientIdAndUserId(UUID clientId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM RewardWallet w WHERE w.id = :id")
    Optional<RewardWallet> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value =
        "INSERT INTO reward_wallets " +
        "  (id, client_id, user_id, currency_id, wallet_type, " +
        "   available_balance, reserved_balance, version, created_at, updated_at) " +
        "VALUES (gen_random_uuid(), :clientId, :userId, :currencyId, 'INDIVIDUAL', 0, 0, 0, NOW(), NOW()) " +
        "ON CONFLICT DO NOTHING")
    void ensureIndividualWalletExists(
            @Param("clientId") UUID clientId,
            @Param("userId") UUID userId,
            @Param("currencyId") String currencyId);
}
