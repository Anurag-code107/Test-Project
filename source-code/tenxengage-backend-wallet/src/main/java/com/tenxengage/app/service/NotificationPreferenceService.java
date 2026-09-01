package com.tenxengage.app.service;

import com.tenxengage.app.dto.request.BulkUpdateUserPreferencesRequest;
import com.tenxengage.app.dto.request.UpdateUserNotificationPreferenceRequest;
import com.tenxengage.app.dto.request.UpdateUserNotificationSettingRequest;
import com.tenxengage.app.dto.response.UserNotificationPreferenceResponse;
import com.tenxengage.app.dto.response.UserNotificationSettingResponse;
import com.tenxengage.app.entity.UserNotificationPreference;
import com.tenxengage.app.entity.UserNotificationSetting;
import com.tenxengage.app.repository.NotificationTypeRepository;
import com.tenxengage.app.repository.UserNotificationPreferenceRepository;
import com.tenxengage.app.repository.UserNotificationSettingRepository;
import com.tenxengage.app.security.TenantValidator;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPreferenceService.class);

    private final UserNotificationSettingRepository settingRepository;
    private final UserNotificationPreferenceRepository preferenceRepository;
    private final NotificationTypeRepository notificationTypeRepository;
    private final TenantValidator tenantValidator;

    public NotificationPreferenceService(UserNotificationSettingRepository settingRepository,
                                          UserNotificationPreferenceRepository preferenceRepository,
                                          NotificationTypeRepository notificationTypeRepository,
                                          TenantValidator tenantValidator) {
        this.settingRepository = settingRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationTypeRepository = notificationTypeRepository;
        this.tenantValidator = tenantValidator;
    }

    @Transactional(readOnly = true)
    public UserNotificationSettingResponse getGlobalSetting() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        UserNotificationSetting setting = settingRepository.findByClientIdAndUserId(clientId, userId)
            .orElse(null);
        if (setting == null) {
            return new UserNotificationSettingResponse(null, userId, true);
        }
        return UserNotificationSettingResponse.from(setting);
    }

    @Transactional
    public UserNotificationSettingResponse updateGlobalSetting(UpdateUserNotificationSettingRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        UserNotificationSetting setting = settingRepository.findByClientIdAndUserId(clientId, userId)
            .orElse(null);

        if (setting == null) {
            setting = UserNotificationSetting.builder()
                .clientId(clientId)
                .userId(userId)
                .notificationsEnabled(request.notificationsEnabled())
                .build();
        } else {
            setting.setNotificationsEnabled(request.notificationsEnabled());
        }

        setting = settingRepository.save(setting);
        log.info("Updated global notification setting: user={}, enabled={}", userId, request.notificationsEnabled());
        return UserNotificationSettingResponse.from(setting);
    }

    @Transactional(readOnly = true)
    public List<UserNotificationPreferenceResponse> getPreferences() {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();
        return preferenceRepository.findByClientIdAndUserId(clientId, userId).stream()
            .map(UserNotificationPreferenceResponse::from)
            .toList();
    }

    @Transactional
    public UserNotificationPreferenceResponse updatePreference(UpdateUserNotificationPreferenceRequest request) {
        UUID clientId = tenantValidator.getCurrentClientId();
        UUID userId = tenantValidator.getCurrentUserId();

        if (!notificationTypeRepository.existsById(request.notificationTypeId())) {
            throw new EntityNotFoundException("Notification type not found: " + request.notificationTypeId());
        }

        UserNotificationPreference pref = preferenceRepository
            .findByClientIdAndUserIdAndNotificationTypeId(clientId, userId, request.notificationTypeId())
            .orElse(null);

        if (pref == null) {
            pref = UserNotificationPreference.builder()
                .clientId(clientId)
                .userId(userId)
                .notificationTypeId(request.notificationTypeId())
                .optedOut(request.optedOut())
                .build();
        } else {
            pref.setOptedOut(request.optedOut());
        }

        pref = preferenceRepository.save(pref);
        log.info("Updated notification preference: user={}, type={}, optedOut={}",
            userId, request.notificationTypeId(), request.optedOut());
        return UserNotificationPreferenceResponse.from(pref);
    }

    @Transactional
    public List<UserNotificationPreferenceResponse> bulkUpdatePreferences(BulkUpdateUserPreferencesRequest request) {
        List<UserNotificationPreferenceResponse> results = new ArrayList<>();
        for (UpdateUserNotificationPreferenceRequest pref : request.preferences()) {
            results.add(updatePreference(pref));
        }
        return results;
    }
}
