package com.tenxengage.app.service;

import com.tenxengage.app.dto.response.ProfileFieldResponse;
import com.tenxengage.app.entity.DataObject;
import com.tenxengage.app.entity.DataObjectField;
import com.tenxengage.app.entity.PartnerCompany;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.entity.enums.FieldDataType;
import com.tenxengage.app.entity.enums.PartnerCompanyStatus;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.DataObjectRepository;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileFieldServiceTest {

    @Mock private DataObjectRepository dataObjectRepository;
    @Mock private UserRepository userRepository;
    @Mock private TenantValidator tenantValidator;

    @InjectMocks private ProfileFieldService service;

    private UUID clientId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // getProfileFields — internal user (no partnerCompanyId)
    // -------------------------------------------------------------------------

    @Test
    void getProfileFields_internalUser_returnsStaticFields() {
        User user = buildInternalUser(userId, clientId, "Alice", "Smith", "alice@example.com");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(user));

        List<ProfileFieldResponse> fields = service.getProfileFields();

        assertThat(fields).hasSize(3);
        assertThat(fields.stream().map(ProfileFieldResponse::fieldName))
                .containsExactly("First Name", "Last Name", "Email");
    }

    @Test
    void getProfileFields_userNotFound_throwsResourceNotFoundException() {
        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findByIdAndClientId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfileFields())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // getProfileFields — external user (has partnerCompanyId, no data object configured)
    // -------------------------------------------------------------------------

    @Test
    void getProfileFields_externalUser_noDataObject_returnsFallbackFields() {
        UUID partnerCompanyId = UUID.randomUUID();
        User user = buildExternalUser(userId, clientId, partnerCompanyId, "Bob", "Jones", "bob@example.com");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(user));
        when(dataObjectRepository.findByClientIdAndName(clientId, "Partner User Data"))
                .thenReturn(Optional.empty());

        List<ProfileFieldResponse> fields = service.getProfileFields();

        assertThat(fields).hasSize(4);
        assertThat(fields.stream().map(ProfileFieldResponse::fieldName))
                .containsExactly("Partner Name", "First Name", "Last Name", "Email");
    }

    // -------------------------------------------------------------------------
    // updateProfileFields — internal user
    // -------------------------------------------------------------------------

    @Test
    void updateProfileFields_internalUser_updatesFirstAndLastName() {
        User user = buildInternalUser(userId, clientId, "Old", "Name", "old@example.com");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        service.updateProfileFields(Map.of("First Name", "New", "Last Name", "Value"));

        assertThat(user.getFirstName()).isEqualTo("New");
        assertThat(user.getLastName()).isEqualTo("Value");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileFields_internalUser_nonEditableField_throwsAccessDeniedException() {
        User user = buildInternalUser(userId, clientId, "Alice", "Smith", "alice@example.com");

        when(tenantValidator.getCurrentClientId()).thenReturn(clientId);
        when(tenantValidator.getCurrentUserId()).thenReturn(userId);
        when(userRepository.findByIdAndClientId(userId, clientId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                service.updateProfileFields(Map.of("Email", "hacker@example.com")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildInternalUser(UUID id, UUID clientId, String firstName,
                                    String lastName, String email) {
        User user = User.builder()
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .passwordHash("hash")
                .clientId(clientId)
                .metadata("{}")
                .build();
        user.setId(id);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private User buildExternalUser(UUID id, UUID clientId, UUID partnerCompanyId,
                                    String firstName, String lastName, String email) {
        PartnerCompany pc = PartnerCompany.builder()
                .name("Partner Corp")
                .clientId(clientId)
                .status(PartnerCompanyStatus.ACTIVE)
                .metadata("{}")
                .build();
        pc.setId(partnerCompanyId);
        pc.setCreatedAt(Instant.now());
        pc.setUpdatedAt(Instant.now());

        User user = User.builder()
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .passwordHash("hash")
                .clientId(clientId)
                .partnerCompanyId(partnerCompanyId)
                .partnerCompany(pc)
                .metadata("{}")
                .build();
        user.setId(id);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}
