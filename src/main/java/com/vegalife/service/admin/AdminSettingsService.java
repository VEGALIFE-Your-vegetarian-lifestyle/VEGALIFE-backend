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
    Map<String, String> changes = request.getSettings();
    Map<String, PlatformSetting> targets = new LinkedHashMap<>();
    for (Map.Entry<String, String> change : changes.entrySet()) {
      SettingKey settingKey =
          SettingKey.fromKey(change.getKey())
              .orElseThrow(
                  () -> new ValidationException("Unknown setting key: " + change.getKey()));
      try {
        settingKey.validate(change.getValue());
      } catch (IllegalArgumentException ex) {
        throw new ValidationException(ex.getMessage());
      }
      PlatformSetting setting =
          platformSettingRepository
              .findByKey(change.getKey())
              .orElseThrow(
                  () -> new ValidationException("Unknown setting key: " + change.getKey()));
      targets.put(change.getKey(), setting);
    }

    validateContentFilterThresholds(changes, targets);

    for (Map.Entry<String, PlatformSetting> entry : targets.entrySet()) {
      PlatformSetting setting = entry.getValue();
      String oldValue = setting.getValue();
      String newValue = changes.get(entry.getKey());
      setting.setValue(newValue);
      setting.setUpdatedBy(adminId);
      platformSettingRepository.saveAndFlush(setting);

      moderationLogRepository.save(
          ModerationLog.builder()
              .actorId(adminId)
              .action("UPDATE_SETTING")
              .targetType("SETTING")
              .targetId(setting.getId())
              .reason(setting.getKey() + ": '" + oldValue + "' -> '" + newValue + "'")
              .build());
    }
    return getSettings();
  }

  /**
   * {@code EmbeddingRelevanceScorer} requires {@code reject < accept}; only re-checked when a write
   * touches either threshold, resolving the other side to its current (or about-to-be-written)
   * value so two single-key PATCHes that individually pass bounds can't together leave a
   * contradictory pair stored.
   */
  private void validateContentFilterThresholds(
      Map<String, String> changes, Map<String, PlatformSetting> targets) {
    String acceptKey = SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD.getKey();
    String rejectKey = SettingKey.CONTENT_FILTER_REJECT_THRESHOLD.getKey();
    if (!changes.containsKey(acceptKey) && !changes.containsKey(rejectKey)) {
      return;
    }
    double accept =
        SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD.parseDecimal(
            resolveValue(SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD, changes, targets));
    double reject =
        SettingKey.CONTENT_FILTER_REJECT_THRESHOLD.parseDecimal(
            resolveValue(SettingKey.CONTENT_FILTER_REJECT_THRESHOLD, changes, targets));
    if (!(reject < accept)) {
      throw new ValidationException(
          rejectKey
              + " must be less than "
              + acceptKey
              + " (got reject="
              + reject
              + ", accept="
              + accept
              + ")");
    }
  }

  private String resolveValue(
      SettingKey settingKey, Map<String, String> changes, Map<String, PlatformSetting> targets) {
    String key = settingKey.getKey();
    if (changes.containsKey(key)) {
      return changes.get(key);
    }
    PlatformSetting existing = targets.get(key);
    if (existing != null) {
      return existing.getValue();
    }
    return platformSettingRepository
        .findByKey(key)
        .map(PlatformSetting::getValue)
        .orElse(settingKey.getDefaultValue());
  }
}
