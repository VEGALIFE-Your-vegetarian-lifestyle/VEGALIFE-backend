package com.vegalife.service.location;

import java.math.BigDecimal;

/** Great-circle distance between two coordinates, in metres (issue #10). No PostGIS/Redis GEO. */
public final class HaversineDistanceCalculator {

  private static final double EARTH_RADIUS_METERS = 6_371_000.0;

  private HaversineDistanceCalculator() {}

  public static double distanceInMeters(
      BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
    double lat1Rad = Math.toRadians(lat1.doubleValue());
    double lat2Rad = Math.toRadians(lat2.doubleValue());
    double deltaLatRad = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
    double deltaLngRad = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

    double a =
        Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2)
            + Math.cos(lat1Rad)
                * Math.cos(lat2Rad)
                * Math.sin(deltaLngRad / 2)
                * Math.sin(deltaLngRad / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return EARTH_RADIUS_METERS * c;
  }
}
