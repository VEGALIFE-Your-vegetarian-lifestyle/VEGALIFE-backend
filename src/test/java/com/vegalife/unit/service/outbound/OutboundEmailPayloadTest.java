package com.vegalife.unit.service.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.service.outbound.OutboundEmailPayload;
import org.junit.jupiter.api.Test;

/**
 * ADR-019 renamed the payload field but rows already sitting on the outbound queue still carry the
 * old {@code username} key, so both spellings must deserialize.
 */
class OutboundEmailPayloadTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void deserializesLegacyUsernameKey() throws Exception {
    String legacy =
        "{\"type\":\"EMAIL_VERIFICATION\",\"username\":\"john.doe\",\"otp\":\"123456\","
            + "\"expiryMinutes\":10}";

    OutboundEmailPayload payload = objectMapper.readValue(legacy, OutboundEmailPayload.class);

    assertThat(payload.displayName()).isEqualTo("john.doe");
    assertThat(payload.otp()).isEqualTo("123456");
  }

  @Test
  void deserializesCurrentDisplayNameKey() throws Exception {
    String current =
        "{\"type\":\"EMAIL_VERIFICATION\",\"displayName\":\"john.doe\",\"otp\":\"123456\","
            + "\"expiryMinutes\":10}";

    OutboundEmailPayload payload = objectMapper.readValue(current, OutboundEmailPayload.class);

    assertThat(payload.displayName()).isEqualTo("john.doe");
  }

  @Test
  void serializesAsDisplayName() throws Exception {
    OutboundEmailPayload payload =
        new OutboundEmailPayload(
            OutboundEmailPayload.Type.PASSWORD_RESET, "john.doe", "123456", 10);

    String json = objectMapper.writeValueAsString(payload);

    assertThat(json).contains("\"displayName\":\"john.doe\"").doesNotContain("\"username\"");
  }
}
