package com.vegalife.dto.response.menu;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuDetailResponse {

  private UUID id;
  private LocalDate startDate;
  private LocalDate endDate;
  private String status;
  private String notes;
  private String dietaryPreferences;
  private Instant createdAt;
  private Instant updatedAt;
  private List<MenuDayResponse> days;
}
