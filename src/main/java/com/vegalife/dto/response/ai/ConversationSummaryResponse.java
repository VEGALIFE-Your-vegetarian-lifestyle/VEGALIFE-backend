package com.vegalife.dto.response.ai;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of the caller's conversation list (issue #115). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryResponse {

  private UUID id;
  private String title;
  private Instant updatedAt;
}
