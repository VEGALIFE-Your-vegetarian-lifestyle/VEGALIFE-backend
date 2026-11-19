package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Acquisition against conversion: new accounts per bucket (bars) and members who paid for the first
 * time per bucket (line), on one shared axis because both count people.
 *
 * <p>No conversion ratio is derived here. The two counts cover different populations — a member who
 * registered months ago and pays today is a first-time payer today but not a new user today — so
 * dividing one by the other would not be a conversion rate and could exceed 1. The trustworthy
 * conversion figure is the lifetime {@code money.payerConversionRate} on the summary.
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
   * One bucket. {@code newPayingUsers} counts distinct members whose first succeeded payment landed
   * in this bucket, so a repeat buyer appears once, where they converted.
   */
  public record Point(Instant bucketStart, long newUsers, long newPayingUsers) {}
}
