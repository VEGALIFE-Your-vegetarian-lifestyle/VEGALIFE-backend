package com.vegalife.dto.response.admin;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingItemResponse {

  private String key;
  private String value;
  private Instant updatedAt;
}
