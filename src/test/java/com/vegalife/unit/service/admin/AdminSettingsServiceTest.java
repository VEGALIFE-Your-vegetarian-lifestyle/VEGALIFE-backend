package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.admin.SettingsUpdateRequest;
import com.vegalife.dto.response.admin.SettingGroupResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.admin.PlatformSetting;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.admin.PlatformSettingRepository;
import com.vegalife.service.admin.AdminSettingsService;
import com.vegalife.shared.exception.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {

  @Mock private PlatformSettingRepository platformSettingRepository;

  @Mock private ModerationLogRepository moderationLogRepository;

  @InjectMocks private AdminSettingsService adminSettingsService;

  @Test
  void getSettingsGroupsItemsByCategory() {
    when(platformSettingRepository.findAllByOrderByCategoryAscKeyAsc())
        .thenReturn(
            List.of(
                PlatformSetting.builder()
                    .id(UUID.randomUUID())
                    .category("ai")
                    .key("ai_chat_enabled")
                    .value("true")
                    .build(),
                PlatformSetting.builder()
                    .id(UUID.randomUUID())
                    .category("media")
                    .key("max_image_upload_mb")
                    .value("5")
                    .build()));

    List<SettingGroupResponse> groups = adminSettingsService.getSettings();

    assertThat(groups).hasSize(2);
    assertThat(groups.get(0).getCategory()).isEqualTo("ai");
    assertThat(groups.get(0).getItems()).extracting("key").containsExactly("ai_chat_enabled");
  }

  @Test
  void updateSettingsAppliesChangeRecordsModerationLogAndSetsUpdatedBy() {
    UUID adminId = UUID.randomUUID();
    UUID settingId = UUID.randomUUID();
    PlatformSetting setting =
        PlatformSetting.builder()
            .id(settingId)
            .category("media")
            .key("max_image_upload_mb")
            .value("5")
            .build();
    when(platformSettingRepository.findByKey("max_image_upload_mb"))
        .thenReturn(Optional.of(setting));
    when(platformSettingRepository.saveAndFlush(any(PlatformSetting.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(platformSettingRepository.findAllByOrderByCategoryAscKeyAsc())
        .thenReturn(List.of(setting));

    adminSettingsService.updateSettings(
        adminId,
        SettingsUpdateRequest.builder().settings(Map.of("max_image_upload_mb", "10")).build());

    ArgumentCaptor<PlatformSetting> settingCaptor = ArgumentCaptor.forClass(PlatformSetting.class);
    verify(platformSettingRepository).saveAndFlush(settingCaptor.capture());
    assertThat(settingCaptor.getValue().getValue()).isEqualTo("10");
    assertThat(settingCaptor.getValue().getUpdatedBy()).isEqualTo(adminId);

    ArgumentCaptor<ModerationLog> logCaptor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository).save(logCaptor.capture());
    assertThat(logCaptor.getValue().getActorId()).isEqualTo(adminId);
    assertThat(logCaptor.getValue().getAction()).isEqualTo("UPDATE_SETTING");
    assertThat(logCaptor.getValue().getTargetId()).isEqualTo(settingId);
  }

  @Test
  void updateSettingsRejectsUnknownKey() {
    assertThatThrownBy(
            () ->
                adminSettingsService.updateSettings(
                    UUID.randomUUID(),
                    SettingsUpdateRequest.builder().settings(Map.of("bogus_key", "x")).build()))
        .isInstanceOf(ValidationException.class);

    verify(platformSettingRepository, never()).saveAndFlush(any());
    verify(moderationLogRepository, never()).save(any());
  }

  @Test
  void updateSettingsRejectsValueOutOfBounds() {
    assertThatThrownBy(
            () ->
                adminSettingsService.updateSettings(
                    UUID.randomUUID(),
                    SettingsUpdateRequest.builder()
                        .settings(Map.of("max_image_upload_mb", "0"))
                        .build()))
        .isInstanceOf(ValidationException.class);

    verify(platformSettingRepository, never()).saveAndFlush(any());
    verify(moderationLogRepository, never()).save(any());
  }

  @Test
  void updateSettingsRejectsRejectThresholdNotBelowAcceptThreshold() {
    PlatformSetting rejectSetting =
        PlatformSetting.builder()
            .id(UUID.randomUUID())
            .category("content")
            .key("content_filter_reject_threshold")
            .value("0.45")
            .build();
    when(platformSettingRepository.findByKey("content_filter_reject_threshold"))
        .thenReturn(Optional.of(rejectSetting));

    assertThatThrownBy(
            () ->
                adminSettingsService.updateSettings(
                    UUID.randomUUID(),
                    SettingsUpdateRequest.builder()
                        .settings(Map.of("content_filter_reject_threshold", "0.80"))
                        .build()))
        .isInstanceOf(ValidationException.class);

    verify(platformSettingRepository, never()).saveAndFlush(any());
    verify(moderationLogRepository, never()).save(any());
  }
}
