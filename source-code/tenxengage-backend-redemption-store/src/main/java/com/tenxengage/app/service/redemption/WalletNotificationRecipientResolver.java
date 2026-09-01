package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Resolves the explicit recipient user IDs for a wallet's balance-expiry notification.
 *
 * <p>Returns an empty list when no recipient can be resolved. Callers MUST NOT publish a
 * directed notification event with an empty target list: {@code NotificationDispatcher} treats
 * an empty {@code targetUserIds} as a tenant-wide role broadcast.
 */
@Component
public class WalletNotificationRecipientResolver {

    private final UserRepository userRepository;

    public WalletNotificationRecipientResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UUID> resolve(RewardWallet wallet) {
        if (wallet.getWalletType() == WalletType.INDIVIDUAL && wallet.getUserId() != null) {
            return List.of(wallet.getUserId());
        }
        if (wallet.getWalletType() == WalletType.COMPANY && wallet.getPartnerCompanyId() != null) {
            return userRepository
                    .findActivePartnerAdminsByCompany(wallet.getClientId(), wallet.getPartnerCompanyId())
                    .stream()
                    .map(User::getId)
                    .toList();
        }
        return List.of();
    }
}
