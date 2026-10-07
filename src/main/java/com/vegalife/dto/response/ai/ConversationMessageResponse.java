package com.vegalife.dto.response.ai;

import com.vegalife.model.ai.AiMessage;
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

  private AiMessage.Role role;
  private String content;
  private Instant createdAt;
}
