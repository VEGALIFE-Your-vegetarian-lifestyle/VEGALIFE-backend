package com.vegalife.unit.service.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.vegalife.service.location.HaversineDistanceCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HaversineDistanceCalculatorTest {

  @Test
  void distanceInMeters_isZeroForTheSamePoint() {
    BigDecimal lat = new BigDecimal("10.776889");
    BigDecimal lng = new BigDecimal("106.700806");

    double distance = HaversineDistanceCalculator.distanceInMeters(lat, lng, lat, lng);

    assertThat(distance).isZero();
  }

  @Test
  void distanceInMeters_matchesKnownDistanceBetweenTwoLandmarks() {
    // Ben Thanh Market (10.772465, 106.698055) to Independence Palace (10.777529, 106.695528):
    // a well-known ~620m walk.
    BigDecimal benThanhLat = new BigDecimal("10.772465");
    BigDecimal benThanhLng = new BigDecimal("106.698055");
    BigDecimal palaceLat = new BigDecimal("10.777529");
    BigDecimal palaceLng = new BigDecimal("106.695528");

    double distance =
        HaversineDistanceCalculator.distanceInMeters(
            benThanhLat, benThanhLng, palaceLat, palaceLng);

    assertThat(distance).isCloseTo(620.0, offset(50.0));
  }

  @Test
  void distanceInMeters_isSymmetric() {
    BigDecimal lat1 = new BigDecimal("10.772465");
    BigDecimal lng1 = new BigDecimal("106.698055");
    BigDecimal lat2 = new BigDecimal("10.777529");
    BigDecimal lng2 = new BigDecimal("106.695528");

    double forward = HaversineDistanceCalculator.distanceInMeters(lat1, lng1, lat2, lng2);
    double backward = HaversineDistanceCalculator.distanceInMeters(lat2, lng2, lat1, lng1);

    assertThat(forward).isEqualTo(backward);
  }

  @Test
  void distanceInMeters_oneDegreeLatitudeIsAboutOneHundredElevenKilometers() {
    BigDecimal lat1 = BigDecimal.ZERO;
    BigDecimal lng = BigDecimal.ZERO;
    BigDecimal lat2 = BigDecimal.ONE;

    double distance = HaversineDistanceCalculator.distanceInMeters(lat1, lng, lat2, lng);

    assertThat(distance).isCloseTo(111_195.0, offset(100.0));
  }
}
