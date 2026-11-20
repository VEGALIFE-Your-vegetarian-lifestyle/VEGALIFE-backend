package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Posts that went live, one point per bucket.
 *
 * <p>Counts only posts whose status is {@code published} and which are not soft-deleted. Drafts,
 * posts still in the moderation queue, and rejected posts are excluded, so this series measures
 * what actually reached readers rather than what was submitted.
 *
 * <p>Bucketed by {@code published_at}, not {@code created_at}: a post drafted weeks ago and
 * published today is content that appeared today, and belongs in today's bucket.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedPostsResponse {

  private List<Point> points;

  /** One bucket: how many posts became visible to readers in it. */
  public record Point(Instant bucketStart, long published) {}
}
