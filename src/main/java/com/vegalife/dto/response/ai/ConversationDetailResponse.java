package com.vegalife.dto.response.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A conversation and its full message history, oldest first (issue #115). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailResponse {

  private UUID id;
  private String title;
  private Instant updatedAt;
  private List<ConversationMessageResponse> messages;
}
