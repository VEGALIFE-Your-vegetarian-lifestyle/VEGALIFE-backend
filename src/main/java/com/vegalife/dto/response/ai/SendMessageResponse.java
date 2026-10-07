package com.vegalife.dto.response.ai;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageResponse {

  private UUID conversationId;
  private String reply;
  private AiUsageQuotaResponse usage;
}
