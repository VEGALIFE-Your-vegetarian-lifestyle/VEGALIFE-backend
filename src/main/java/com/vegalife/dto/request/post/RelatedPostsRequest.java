package com.vegalife.dto.request.post;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Issue #7: configurable result count, default 10. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedPostsRequest {

  @Min(value = 1, message = "limit must be between 1 and 50")
  @Max(value = 50, message = "limit must be between 1 and 50")
  private Integer limit;

  public int getLimit() {
    return limit == null ? 10 : limit;
  }
}
