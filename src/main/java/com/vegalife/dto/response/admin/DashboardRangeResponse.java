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
public class DashboardRangeResponse {

  private String type;
  private Instant from;
  private Instant to;
}
