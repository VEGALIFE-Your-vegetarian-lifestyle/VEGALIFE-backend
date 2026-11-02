package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Partial update of one or more settings by key (issue #13). Unknown keys are rejected. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsUpdateRequest {

  @NotEmpty(message = "At least one setting must be provided")
  private Map<String, String> settings;
}
