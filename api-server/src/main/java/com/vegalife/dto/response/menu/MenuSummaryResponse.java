package com.vegalife.dto.response.menu;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuSummaryResponse {

  private UUID id;
  private LocalDate startDate;
  private LocalDate endDate;
  private String status;
  private String notes;
  private Instant createdAt;
  private Instant updatedAt;
}
