package com.vegalife.service.admin;

import com.vegalife.dto.request.admin.SettingsUpdateRequest;
import com.vegalife.dto.response.admin.SettingGroupResponse;
import com.vegalife.dto.response.admin.SettingItemResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.admin.PlatformSetting;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.admin.PlatformSettingRepository;
import com.vegalife.shared.exception.ValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-configurable platform settings, grouped by category (issue #13). Every change is recorded
 * in {@code moderation_log} (who, what, when) — BR-ADMIN-002's audit convention, reused here rather
 * than inventing a parallel audit trail.
 */
@Service
@RequiredArgsConstructor
public class AdminSettingsService {

  private final PlatformSettingRepository platformSettingRepository;
  private final ModerationLogRepository moderationLogRepository;

  @Transactional(readOnly = true)
  public List<SettingGroupResponse> getSettings() {
    Map<String, List<SettingItemResponse>> byCategory = new LinkedHashMap<>();
    for (PlatformSetting setting : platformSettingRepository.findAllByOrderByCategoryAscKeyAsc()) {
      byCategory
          .computeIfAbsent(setting.getCategory(), c -> new ArrayList<>())
          .add(
              SettingItemResponse.builder()
                  .key(setting.getKey())
                  .value(setting.getValue())
                  .updatedAt(setting.getUpdatedAt())
                  .build());
    }
    return byCategory.entrySet().stream()
        .map(
            entry ->
                SettingGroupResponse.builder()
                    .category(entry.getKey())
                    .items(entry.getValue())
                    .build())
        .toList();
  }

  @Transactional
  public List<SettingGroupResponse> updateSettings(UUID adminId, SettingsUpdateRequest request) {
    for (Map.Entry<String, String> change : request.getSettings().entrySet()) {
      PlatformSetting setting =
          platformSettingRepository
              .findByKey(change.getKey())
              .orElseThrow(
                  () -> new ValidationException("Unknown setting key: " + change.getKey()));

      String oldValue = setting.getValue();
      setting.setValue(change.getValue());
      platformSettingRepository.saveAndFlush(setting);

      moderationLogRepository.save(
          ModerationLog.builder()
              .actorId(adminId)
              .action("UPDATE_SETTING")
              .targetType("SETTING")
              .targetId(setting.getId())
              .reason(setting.getKey() + ": '" + oldValue + "' -> '" + change.getValue() + "'")
              .build());
    }
    return getSettings();
  }
}
