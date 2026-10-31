package com.vegalife.dto.response.post;

import com.vegalife.enums.post.VoteType;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoteResponse {

  UUID postId;
  long likeCount;
  long dislikeCount;
  VoteType userVote;
}
