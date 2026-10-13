package com.vegalife.dto.request.category;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Partial update: null/omitted fields are left unchanged. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryUpdateRequest {

  @Pattern(regexp = "(?s).*\\S.*", message = "Name must not be blank")
  @Size(max = 100, message = "Name must be at most 100 characters")
  private String name;

  private String description;

  @AssertTrue(message = "At least one field must be provided")
  public boolean isUpdateRequestNotEmpty() {
    return name != null || description != null;
  }
}
