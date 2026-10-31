package com.vegalife.dto.response.ai;

import com.vegalife.enums.ai.AiMessageRole;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One persisted message of a conversation (issue #115). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageResponse {

  private AiMessageRole role;
  private String content;
  private Instant createdAt;
}
