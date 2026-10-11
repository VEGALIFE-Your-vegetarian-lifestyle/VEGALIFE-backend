package com.vegalife.dto.response.post;

import com.vegalife.model.post.Vote;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoteResponse {

  UUID postId;
  long likeCount;
  long dislikeCount;
  Vote.VoteType userVote;
}
