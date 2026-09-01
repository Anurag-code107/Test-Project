package com.tenxengage.app.service;

import com.tenxengage.app.entity.Client;
import com.tenxengage.app.entity.ClientFeatureOverride;
import com.tenxengage.app.entity.FeatureFlag;
import com.tenxengage.app.entity.enums.ClientStatus;
import com.tenxengage.app.entity.enums.SubscriptionTier;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.ClientFeatureOverrideRepository;
import com.tenxengage.app.repository.ClientRepository;
import com.tenxengage.app.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository featureFlagRepository;
    @Mock
    private ClientFeatureOverrideRepository overrideRepository;
    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private FeatureFlagService featureFlagService;

    private UUID clientId;
    private Client enterpriseClient;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        enterpriseClient = Client.builder()
                .name("Test Client")
                .subdomain("test")
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.ENTERPRISE)
                .build();
        enterpriseClient.setId(clientId);
    }

    @Test
    void getEnabledFeatures_returnsForEnterpriseTier() {
        FeatureFlag flag = FeatureFlag.builder()
                .featureKey("AI_CHAT")
                .starterEnabled(false)
                .professionalEnabled(false)
                .enterpriseEnabled(true)
                .build();
        flag.setId(UUID.randomUUID());

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(enterpriseClient));
        when(featureFlagRepository.findAll()).thenReturn(List.of(flag));
        when(overrideRepository.findByClientId(clientId)).thenReturn(List.of());

        List<String> features = featureFlagService.getEnabledFeatures(clientId);

        assertThat(features).contains("AI_CHAT");
    }

    @Test
    void getEnabledFeatures_starterDoesNotGetEnterpriseFeatures() {
        Client starterClient = Client.builder()
                .name("Starter").subdomain("starter")
                .status(ClientStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.STARTER)
                .build();
        starterClient.setId(clientId);

        FeatureFlag flag = FeatureFlag.builder()
                .featureKey("AI_CHAT")
                .starterEnabled(false)
                .professionalEnabled(false)
                .enterpriseEnabled(true)
                .build();
        flag.setId(UUID.randomUUID());

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(starterClient));
        when(featureFlagRepository.findAll()).thenReturn(List.of(flag));
        when(overrideRepository.findByClientId(clientId)).thenReturn(List.of());

        List<String> features = featureFlagService.getEnabledFeatures(clientId);

        assertThat(features).doesNotContain("AI_CHAT");
    }

    @Test
    void getEnabledFeatures_overrideEnablesFeature() {
        FeatureFlag flag = FeatureFlag.builder()
                .featureKey("BETA_FEATURE")
                .starterEnabled(false)
                .professionalEnabled(false)
                .enterpriseEnabled(false)
                .build();
        flag.setId(UUID.randomUUID());

        ClientFeatureOverride override = ClientFeatureOverride.builder()
                .clientId(clientId)
                .featureFlagId(flag.getId())
                .enabled(true)
                .build();

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(enterpriseClient));
        when(featureFlagRepository.findAll()).thenReturn(List.of(flag));
        when(overrideRepository.findByClientId(clientId)).thenReturn(List.of(override));

        List<String> features = featureFlagService.getEnabledFeatures(clientId);

        assertThat(features).contains("BETA_FEATURE");
    }

    @Test
    void getEnabledFeatures_overrideDisablesFeature() {
        FeatureFlag flag = FeatureFlag.builder()
                .featureKey("AI_CHAT")
                .starterEnabled(true)
                .professionalEnabled(true)
                .enterpriseEnabled(true)
                .build();
        flag.setId(UUID.randomUUID());

        ClientFeatureOverride override = ClientFeatureOverride.builder()
                .clientId(clientId)
                .featureFlagId(flag.getId())
                .enabled(false)
                .build();

        when(clientRepository.findById(clientId)).thenReturn(Optional.of(enterpriseClient));
        when(featureFlagRepository.findAll()).thenReturn(List.of(flag));
        when(overrideRepository.findByClientId(clientId)).thenReturn(List.of(override));

        List<String> features = featureFlagService.getEnabledFeatures(clientId);

        assertThat(features).doesNotContain("AI_CHAT");
    }

    @Test
    void getEnabledFeatures_returnsEmptyForNullClientId() {
        List<String> features = featureFlagService.getEnabledFeatures(null);

        assertThat(features).isEmpty();
    }

    @Test
    void getEnabledFeatures_returnsEmptyWhenClientNotFound() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        List<String> features = featureFlagService.getEnabledFeatures(clientId);

        assertThat(features).isEmpty();
    }

    @Test
    void createFeatureFlag_rejectsDuplicateKey() {
        FeatureFlag existing = FeatureFlag.builder().featureKey("EXISTING").build();
        when(featureFlagRepository.findByFeatureKey("EXISTING")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> featureFlagService.createFeatureFlag(
                new com.tenxengage.app.dto.request.CreateFeatureFlagRequest(
                        "EXISTING", "Existing feature", false, false, false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }
}
