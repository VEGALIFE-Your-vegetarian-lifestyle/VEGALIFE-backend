package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lifetime content totals. In-range creates belong to the charts endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardContentMetrics {

  private long totalPosts;
  private long totalRecipes;
  private long totalComments;
}
