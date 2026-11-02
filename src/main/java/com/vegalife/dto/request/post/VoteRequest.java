package com.vegalife.dto.request.post;

import com.vegalife.enums.post.VoteType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteRequest {

  @NotNull(message = "voteType is required")
  private VoteType voteType;
}
