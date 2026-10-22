package com.vegalife.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardContentMetrics {

  private long totalPosts;
  private long newPosts;
  private long totalRecipes;
  private long newRecipes;
  private long totalComments;
  private long newComments;
}
