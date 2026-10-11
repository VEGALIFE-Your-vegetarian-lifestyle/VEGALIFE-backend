package com.vegalife.dto.request.menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One day of a create-menu payload: a date inside the week plus its meals. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuDayRequest {

  @NotNull(message = "date is required")
  private LocalDate date;

  @NotEmpty(message = "each day must have between 1 and 3 meals")
  @Valid
  private List<MealRequest> meals;
}
