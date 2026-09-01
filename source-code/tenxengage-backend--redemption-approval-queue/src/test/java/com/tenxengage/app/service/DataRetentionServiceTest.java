package com.tenxengage.app.service;

import com.tenxengage.app.entity.RetentionPolicy;
import com.tenxengage.app.entity.RetentionPolicyBound;
import com.tenxengage.app.entity.enums.DataCategory;
import com.tenxengage.app.entity.enums.RetentionActionType;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.repository.RetentionPolicyBoundRepository;
import com.tenxengage.app.repository.RetentionPolicyRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock
    private RetentionPolicyRepository retentionPolicyRepository;

    @Mock
    private RetentionPolicyBoundRepository retentionPolicyBoundRepository;

    @InjectMocks
    private DataRetentionService dataRetentionService;

    private UUID clientId;
    private RetentionPolicy systemDefault;
    private RetentionPolicy clientOverride;
    private RetentionPolicyBound bound;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();

        systemDefault = new RetentionPolicy();
        systemDefault.setId(UUID.randomUUID());
        systemDefault.setClientId(null);
        systemDefault.setDataCategory(DataCategory.INACTIVE_USERS);
        systemDefault.setRetentionDays(365);
        systemDefault.setActionType(RetentionActionType.ANONYMIZE);

        clientOverride = new RetentionPolicy();
        clientOverride.setId(UUID.randomUUID());
        clientOverride.setClientId(clientId);
        clientOverride.setDataCategory(DataCategory.INACTIVE_USERS);
        clientOverride.setRetentionDays(180);
        clientOverride.setActionType(RetentionActionType.ANONYMIZE);

        bound = new RetentionPolicyBound();
        bound.setId(UUID.randomUUID());
        bound.setDataCategory(DataCategory.INACTIVE_USERS);
        bound.setMinDays(90);
        bound.setMaxDays(730);
    }

    @Test
    void getRetentionPolicies_mergesClientWithDefaults() {
        when(retentionPolicyRepository.findByClientIdIsNull()).thenReturn(List.of(systemDefault));
        when(retentionPolicyRepository.findByClientId(clientId)).thenReturn(List.of(clientOverride));

        List<RetentionPolicy> result = dataRetentionService.getRetentionPolicies(clientId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRetentionDays()).isEqualTo(180);
        assertThat(result.get(0).getClientId()).isEqualTo(clientId);
    }

    @Test
    void getRetentionPolicies_returnsDefaultsWhenNoClientOverrides() {
        when(retentionPolicyRepository.findByClientIdIsNull()).thenReturn(List.of(systemDefault));
        when(retentionPolicyRepository.findByClientId(clientId)).thenReturn(List.of());

        List<RetentionPolicy> result = dataRetentionService.getRetentionPolicies(clientId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRetentionDays()).isEqualTo(365);
        assertThat(result.get(0).getClientId()).isNull();
    }

    @Test
    void updateRetentionPolicy_validatesAgainstBounds() {
        when(retentionPolicyBoundRepository.findByDataCategory(DataCategory.INACTIVE_USERS))
                .thenReturn(Optional.of(bound));
        when(retentionPolicyRepository.findByClientIdAndDataCategory(clientId, DataCategory.INACTIVE_USERS))
                .thenReturn(Optional.of(clientOverride));
        when(retentionPolicyRepository.save(any(RetentionPolicy.class))).thenReturn(clientOverride);

        RetentionPolicy result = dataRetentionService.updateRetentionPolicy(clientId, "INACTIVE_USERS", 200);

        verify(retentionPolicyRepository).save(any(RetentionPolicy.class));
        assertThat(result).isNotNull();
    }

    @Test
    void updateRetentionPolicy_rejectsOutOfBoundsValue() {
        when(retentionPolicyBoundRepository.findByDataCategory(DataCategory.INACTIVE_USERS))
                .thenReturn(Optional.of(bound));

        assertThatThrownBy(() -> dataRetentionService.updateRetentionPolicy(clientId, "INACTIVE_USERS", 50))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be between 90 and 730");
    }
}
