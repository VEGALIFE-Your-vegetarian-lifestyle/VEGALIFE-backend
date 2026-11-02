package com.vegalife.dto.response.search;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultItem {

  /** One of {@code post}, {@code video}, {@code recipe}. */
  private String type;

  private UUID id;
  private String title;

  /**
   * ~150-character excerpt around the first match, with the matched text wrapped in {@code <mark>}.
   */
  private String snippet;

  /** Always empty for a recipe result. */
  private Set<UUID> categoryIds;

  private Instant createdAt;
}
