package com.tenxengage.app.service.redemption;

import com.tenxengage.app.entity.RewardWallet;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.WalletType;
import com.tenxengage.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletNotificationRecipientResolverTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private WalletNotificationRecipientResolver resolver;

    private static final UUID CLIENT_ID = UUID.randomUUID();

    private RewardWallet wallet(WalletType type, UUID userId, UUID partnerCompanyId) {
        return RewardWallet.builder()
                .clientId(CLIENT_ID)
                .walletType(type)
                .userId(userId)
                .partnerCompanyId(partnerCompanyId)
                .build();
    }

    @Test
    void individualWallet_returnsOwnerOnly() {
        UUID ownerId = UUID.randomUUID();
        List<UUID> result = resolver.resolve(wallet(WalletType.INDIVIDUAL, ownerId, null));
        assertThat(result).containsExactly(ownerId);
        verifyNoInteractions(userRepository);
    }

    @Test
    void companyWallet_returnsCompanyAdminIds() {
        UUID companyId = UUID.randomUUID();
        UUID admin1 = UUID.randomUUID();
        UUID admin2 = UUID.randomUUID();
        User u1 = mock(User.class);
        User u2 = mock(User.class);
        when(u1.getId()).thenReturn(admin1);
        when(u2.getId()).thenReturn(admin2);
        when(userRepository.findActivePartnerAdminsByCompany(CLIENT_ID, companyId))
                .thenReturn(List.of(u1, u2));

        List<UUID> result = resolver.resolve(wallet(WalletType.COMPANY, null, companyId));

        assertThat(result).containsExactly(admin1, admin2);
    }

    @Test
    void companyWallet_noAdmins_returnsEmpty() {
        UUID companyId = UUID.randomUUID();
        when(userRepository.findActivePartnerAdminsByCompany(CLIENT_ID, companyId))
                .thenReturn(List.of());
        assertThat(resolver.resolve(wallet(WalletType.COMPANY, null, companyId))).isEmpty();
    }

    @Test
    void individualWallet_nullOwner_returnsEmpty() {
        assertThat(resolver.resolve(wallet(WalletType.INDIVIDUAL, null, null))).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void companyWallet_nullCompany_returnsEmpty() {
        assertThat(resolver.resolve(wallet(WalletType.COMPANY, null, null))).isEmpty();
        verifyNoInteractions(userRepository);
    }
}
