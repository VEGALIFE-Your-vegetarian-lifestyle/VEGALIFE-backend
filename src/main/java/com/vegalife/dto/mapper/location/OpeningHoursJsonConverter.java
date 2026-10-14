package com.vegalife.dto.mapper.location;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Parses the {@code location.opening_hours} raw {@code jsonb} column into a response node. */
@Component
@RequiredArgsConstructor
public class OpeningHoursJsonConverter {

  private final ObjectMapper objectMapper;

  public JsonNode toJsonNode(String openingHoursJson) {
    if (openingHoursJson == null || openingHoursJson.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(openingHoursJson);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
