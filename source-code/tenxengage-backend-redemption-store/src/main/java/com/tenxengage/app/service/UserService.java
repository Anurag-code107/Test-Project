package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.CreateUserRequest;
import com.tenxengage.app.dto.request.UpdateUserRequest;
import com.tenxengage.app.dto.response.UserResponse;
import com.tenxengage.app.entity.User;
import com.tenxengage.app.event.NotificationEvent;
import com.tenxengage.app.event.NotificationEventProducer;
import com.tenxengage.app.exception.BusinessRuleException;
import com.tenxengage.app.exception.ResourceNotFoundException;
import com.tenxengage.app.repository.UserRepository;
import com.tenxengage.app.security.TenantValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantValidator tenantValidator;
    private final NotificationEventProducer notificationEventProducer;
    private final OnboardingService onboardingService;
    private final EmailService emailService;
    private final PermissionService permissionService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TenantValidator tenantValidator,
                       NotificationEventProducer notificationEventProducer,
                       OnboardingService onboardingService,
                       EmailService emailService,
                       PermissionService permissionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantValidator = tenantValidator;
        this.notificationEventProducer = notificationEventProducer;
        this.onboardingService = onboardingService;
        this.emailService = emailService;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Pageable pageable, String search,
                                       UUID partnerCompanyId, Boolean internal) {
        if (tenantValidator.isTenxAdmin()) {
            return userRepository.searchUsers(search, pageable)
                .map(UserResponse::from);
        }
        UUID clientId = tenantValidator.getCurrentClientId();
        if (Boolean.TRUE.equals(internal)) {
            return userRepository.searchInternalByClientId(clientId, search, pageable)
                .map(UserResponse::from);
        }
        if (partnerCompanyId != null) {
            return userRepository.searchByClientIdAndPartnerCompanyId(
                    clientId, partnerCompanyId, search, pageable)
                .map(UserResponse::from);
        }
        return userRepository.searchByClientId(clientId, search, pageable)
            .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = findByIdForCurrentTenant(id);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email already in use: " + request.email());
        }

        UUID clientId = tenantValidator.isTenxAdmin() ? null : tenantValidator.getCurrentClientId();

        // Generate a placeholder password hash -- user sets real password during onboarding
        String placeholderHash = passwordEncoder.encode(UUID.randomUUID().toString());

        User user = User.builder()
            .email(request.email())
            .firstName(request.firstName())
            .lastName(request.lastName())
            .phone(request.phone())
            .phoneCountryIso2(request.phoneCountryIso2())
            .passwordHash(placeholderHash)
            .status(com.tenxengage.app.entity.enums.UserStatus.PENDING_VERIFICATION)
            .clientId(clientId)
            .partnerCompanyId(request.partnerCompanyId())
            .clientRoleId(request.clientRoleId())
            .metadata(request.metadata() != null ? request.metadata() : "{}")
            .build();

        User savedUser = userRepository.save(user);

        // Generate onboarding token and send welcome email
        if (savedUser.getClientId() != null) {
            try {
                String rawToken = onboardingService.generateOnboardingToken(
                        savedUser.getId(), savedUser.getClientId());
                String onboardingUrl = buildOnboardingUrl(rawToken);
                emailService.sendOnboardingEmail(
                        savedUser.getEmail(), savedUser.getFirstName(), onboardingUrl);
                log.info("Onboarding token generated and email sent for user {}", savedUser.getId());
            } catch (Exception e) {
                log.error("Failed to generate onboarding token for user {}: {}",
                        savedUser.getId(), e.getMessage());
            }

            // Notify PARTNER_ADMIN of new team member
            if (savedUser.getPartnerCompanyId() != null) {
                notificationEventProducer.publish(new NotificationEvent(
                    "USER_ADDED_TO_TEAM", savedUser.getClientId(),
                    "New Team Member Added",
                    savedUser.getFirstName() + " " + savedUser.getLastName() + " has joined the team.",
                    "USER", savedUser.getId(),
                    savedUser.getId(),
                    null,
                    Map.of("partnerCompanyId", savedUser.getPartnerCompanyId().toString())));
            }
        }

        return UserResponse.from(savedUser);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findByIdForCurrentTenant(id);

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new BusinessRuleException("Email already in use: " + request.email());
            }
            user.setEmail(request.email());
        }

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.phoneCountryIso2() != null) {
            user.setPhoneCountryIso2(request.phoneCountryIso2());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.clientRoleId() != null) {
            user.setClientRoleId(request.clientRoleId());
            permissionService.evictPermissionCache(id);
        }
        if (request.metadata() != null) {
            user.setMetadata(request.metadata());
        }

        User updatedUser = userRepository.save(user);

        return UserResponse.from(updatedUser);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = findByIdForCurrentTenant(id);
        userRepository.delete(user);
    }

    private User findByIdForCurrentTenant(UUID id) {
        if (tenantValidator.isTenxAdmin()) {
            return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        }
        UUID clientId = tenantValidator.getCurrentClientId();
        return userRepository.findByIdAndClientId(id, clientId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Transactional
    public void resendOnboardingEmail(UUID userId) {
        User user = findByIdForCurrentTenant(userId);
        if (user.getOnboardingCompletedAt() != null) {
            throw new BusinessRuleException("User has already completed onboarding");
        }
        String rawToken = onboardingService.generateOnboardingToken(user.getId(), user.getClientId());
        String onboardingUrl = buildOnboardingUrl(rawToken);
        emailService.sendOnboardingEmail(user.getEmail(), user.getFirstName(), onboardingUrl);
        log.info("Onboarding email resent for user {}", userId);
    }

    private String buildOnboardingUrl(String rawToken) {
        // Frontend onboarding page URL -- the frontend route handles the token
        return "/onboarding?token=" + rawToken;
    }

}
