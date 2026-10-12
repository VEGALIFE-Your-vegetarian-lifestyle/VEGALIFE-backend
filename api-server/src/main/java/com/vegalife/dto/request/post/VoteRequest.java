package com.vegalife.dto.request.post;

import com.vegalife.model.post.Vote;
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
  private Vote.VoteType voteType;
}
