package com.vegalife.dto.response.menu;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One date of a meal plan and the meals scheduled on it, ordered by meal type. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuMealPlanDayResponse {

  private LocalDate date;

  private List<MealDetailResponse> meals;
}
