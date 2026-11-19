package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Revenue that arrived, one point per bucket. Draw as a line or area — the values are a continuous
 * quantity over time.
 *
 * <p>{@code transactions} is carried alongside the amount so a hover can distinguish one large
 * payment from several ordinary ones without a second series.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTrendResponse {

  private List<Point> points;

  /** One bucket: total succeeded revenue and how many payments produced it. */
  public record Point(Instant bucketStart, long amount, long transactions) {}
}
