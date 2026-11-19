package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Acquisition against conversion: new accounts per bucket (bars) and new paying accounts per bucket
 * (line), on one shared axis because both count people.
 *
 * <p>Allowances never expire and there is no renewal, so revenue has exactly two levers —
 * acquisition and conversion. This is the only series that plots them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthResponse {

  private List<Point> points;

  /**
   * One bucket. {@code conversionRate} is {@code newPayingUsers / newUsers}, zero when nobody
   * signed up; it is noisy per bucket, so a client should smooth it before drawing a trend.
   */
  public record Point(
      Instant bucketStart, long newUsers, long newPayingUsers, double conversionRate) {}
}
