package com.vegalife.unit.service.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.service.email.EmailPayload;
import org.junit.jupiter.api.Test;

/**
 * ADR-019 renamed the payload field but rows already sitting on the outbound queue still carry the
 * old {@code username} key, so both spellings must deserialize.
 */
class EmailPayloadTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesLegacyUsernameKey() throws Exception {
    String legacy =
        "{\"type\":\"EMAIL_VERIFICATION\",\"username\":\"john.doe\",\"otp\":\"123456\","
            + "\"expiryMinutes\":10}";

    EmailPayload payload = objectMapper.readValue(legacy, EmailPayload.class);

    assertThat(payload.displayName()).isEqualTo("john.doe");
    assertThat(payload.otp()).isEqualTo("123456");
  }

  @Test
  void deserializesCurrentDisplayNameKey() throws Exception {
    String current =
        "{\"type\":\"EMAIL_VERIFICATION\",\"displayName\":\"john.doe\",\"otp\":\"123456\","
            + "\"expiryMinutes\":10}";

    EmailPayload payload = objectMapper.readValue(current, EmailPayload.class);

    assertThat(payload.displayName()).isEqualTo("john.doe");
  }

  @Test
  void serializesAsDisplayName() throws Exception {
    EmailPayload payload =
        new EmailPayload(EmailPayload.Type.PASSWORD_RESET, "john.doe", "123456", 10);

    String json = objectMapper.writeValueAsString(payload);

    assertThat(json).contains("\"displayName\":\"john.doe\"").doesNotContain("\"username\"");
  }
}
