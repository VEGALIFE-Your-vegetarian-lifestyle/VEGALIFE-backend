package com.vegalife.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

  @NotBlank(message = "Message is required")
  @Size(max = 4000, message = "Message must not exceed 4000 characters")
  private String message;

  /** Omit or null to start a new conversation; otherwise must belong to the caller (BR-AI-003). */
  private UUID conversationId;
}
