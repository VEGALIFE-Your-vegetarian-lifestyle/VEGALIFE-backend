package com.vegalife.dto.response.menu;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuDayResponse {

  private LocalDate date;
  private List<MealResponse> meals;
}
