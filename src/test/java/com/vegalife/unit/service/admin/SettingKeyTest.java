package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vegalife.service.admin.SettingKey;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingKeyTest {

  @Test
  void fromKeyFindsEveryRegisteredKeyByItsStringName() {
    for (SettingKey settingKey : SettingKey.values()) {
      Optional<SettingKey> found = SettingKey.fromKey(settingKey.getKey());
      assertThat(found).contains(settingKey);
    }
  }

  @Test
  void fromKeyIsEmptyForAnUnknownKey() {
    assertThat(SettingKey.fromKey("not_a_real_key")).isEmpty();
  }

  @Test
  void parseStringListRejectsEmptyAfterSplit() {
    assertThatThrownBy(() -> SettingKey.UPLOAD_ALLOWED_IMAGE_TYPES.parseStringList(" , , "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SettingKey.UPLOAD_ALLOWED_IMAGE_TYPES.parseStringList(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SettingKey.UPLOAD_ALLOWED_IMAGE_TYPES.parseStringList(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseStringListRejectsTypeOutsideTheKnownSet() {
    assertThatThrownBy(
            () -> SettingKey.UPLOAD_ALLOWED_IMAGE_TYPES.parseStringList("application/pdf"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseStringListAcceptsAKnownTypeOutsideTheDefault() {
    assertThat(SettingKey.UPLOAD_ALLOWED_IMAGE_TYPES.parseStringList("image/jpeg, image/gif"))
        .containsExactly("image/jpeg", "image/gif");
  }

  @Test
  void parseIntEnforcesBounds() {
    assertThat(SettingKey.MAX_IMAGE_UPLOAD_MB.parseInt("1")).isEqualTo(1);
    assertThat(SettingKey.MAX_IMAGE_UPLOAD_MB.parseInt("100")).isEqualTo(100);
    assertThatThrownBy(() -> SettingKey.MAX_IMAGE_UPLOAD_MB.parseInt("0"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SettingKey.MAX_IMAGE_UPLOAD_MB.parseInt("101"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SettingKey.MAX_IMAGE_UPLOAD_MB.parseInt("not-a-number"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseDecimalEnforcesBounds() {
    assertThat(SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD.parseDecimal("0")).isEqualTo(0.0);
    assertThat(SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD.parseDecimal("1")).isEqualTo(1.0);
    assertThatThrownBy(() -> SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD.parseDecimal("-0.01"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD.parseDecimal("1.01"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseBooleanAcceptsOnlyTrueOrFalseCaseInsensitive() {
    assertThat(SettingKey.AI_CHAT_ENABLED.parseBoolean("true")).isTrue();
    assertThat(SettingKey.AI_CHAT_ENABLED.parseBoolean("FALSE")).isFalse();
    assertThatThrownBy(() -> SettingKey.AI_CHAT_ENABLED.parseBoolean("yes"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SettingKey.AI_CHAT_ENABLED.parseBoolean(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void everyRegisteredDefaultValuePassesItsOwnValidation() {
    for (SettingKey settingKey : SettingKey.values()) {
      settingKey.validate(settingKey.getDefaultValue());
    }
  }
}
