package com.vegalife.service.location;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.admin.AdminLocationMapper;
import com.vegalife.dto.mapper.location.LocationMapper;
import com.vegalife.dto.request.location.LocationCreateRequest;
import com.vegalife.dto.request.location.LocationListRequest;
import com.vegalife.dto.request.location.LocationNearbyRequest;
import com.vegalife.dto.request.location.LocationUpdateRequest;
import com.vegalife.dto.response.admin.AdminLocationResponse;
import com.vegalife.dto.response.location.LocationResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Location;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.LocationRepository;
import com.vegalife.repository.post.LocationSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommend-locations list (issue #9): active locations filtered by rating/type/price/open-now,
 * sorted by rating descending (unrated last), then creation time and id ascending for stable
 * paging. Open-now, pagination, and the tie-break sort stay a single code path by loading every
 * active match for the structured filters up front and ordering/paging it in memory: a location
 * with no opening hours can only be excluded after parsing its document, which the database cannot
 * do, and "unrated last" needs a {@code Comparator} since {@code
 * JpaSpecificationExecutor.findAll(Specification, Sort)} builds its order through the Criteria API,
 * which does not support {@code Sort.Order.nullsLast()} (throws {@code
 * UnsupportedOperationException}).
 */
@Service
@RequiredArgsConstructor
public class LocationService {

  /** 1 degree of latitude is ~111.32 km everywhere; used to size the bounding box (issue #10). */
  private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;

  /** Avoids a division blow-up in the longitude delta for a search centre near the poles. */
  private static final double MIN_COS_LATITUDE = 1e-6;

  private static final Comparator<Location> RESULT_ORDER =
      Comparator.comparing(Location::getRating, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Location::getCreatedAt)
          .thenComparing(Location::getId);

  private final LocationRepository locationRepository;
  private final LocationMapper locationMapper;
  private final AdminLocationMapper adminLocationMapper;
  private final ModerationLogRepository moderationLogRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public PageResponse<LocationResponse> listLocations(LocationListRequest request) {
    Location.PlaceType placeType = parsePlaceType(request.getPlaceType());
    Specification<Location> spec =
        LocationSpecifications.activeWithFilters(
            request.getMinRating(), placeType, request.getMaxPriceLevel());

    List<Location> matches = new ArrayList<>(locationRepository.findAll(spec));
    matches.sort(RESULT_ORDER);
    List<Location> filtered = request.isOpenNow() ? filterOpenNow(matches) : matches;

    Page<Location> page = paginate(filtered, request.getPage(), request.getSize());
    return PageResponse.from(page.map(locationMapper::toResponse));
  }

  private List<Location> filterOpenNow(List<Location> locations) {
    LocalDateTime now = LocalDateTime.now();
    return locations.stream()
        .filter(
            location ->
                OpeningHoursEvaluator.isOpenNow(location.getOpeningHours(), objectMapper, now))
        .toList();
  }

  private Page<Location> paginate(List<Location> locations, int page, int size) {
    int fromIndex = Math.min(page * size, locations.size());
    int toIndex = Math.min(fromIndex + size, locations.size());
    List<Location> pageContent = locations.subList(fromIndex, toIndex);
    return new PageImpl<>(pageContent, PageRequest.of(page, size), locations.size());
  }

  private Location.PlaceType parsePlaceType(String placeType) {
    return placeType == null || placeType.isBlank() ? null : Location.PlaceType.valueOf(placeType);
  }

  /**
   * Every active location within {@code radius} metres of (lat, lng), nearest first, with no
   * pagination (issue #10). A bounding-box {@link Specification} does the coarse, index-friendly
   * pre-filter over {@code idx_location_coords}; haversine then applies the exact radius cut and
   * computes the distance each row is sorted and returned with.
   */
  @Transactional(readOnly = true)
  public List<LocationResponse> listNearby(LocationNearbyRequest request) {
    BigDecimal lat = request.getLat();
    BigDecimal lng = request.getLng();
    double radiusMeters = request.getRadius().doubleValue();

    BoundingBox box = boundingBox(lat, lng, radiusMeters);
    Specification<Location> spec =
        LocationSpecifications.activeWithinBoundingBox(
            box.minLat(), box.maxLat(), box.minLng(), box.maxLng());
    List<Location> candidates = locationRepository.findAll(spec);

    List<LocationWithDistance> withinRadius = new ArrayList<>();
    for (Location candidate : candidates) {
      double distance =
          HaversineDistanceCalculator.distanceInMeters(
              lat, lng, candidate.getLatitude(), candidate.getLongitude());
      if (distance <= radiusMeters) {
        withinRadius.add(new LocationWithDistance(candidate, distance));
      }
    }
    withinRadius.sort(Comparator.comparingDouble(LocationWithDistance::distance));

    return withinRadius.stream()
        .map(item -> locationMapper.toResponse(item.location(), item.distance()))
        .toList();
  }

  /**
   * Converts a radius in metres to a lat/lng box: 1 degree of longitude shrinks with {@code
   * cos(latitude)}, so the box narrows east-west away from the equator.
   */
  private BoundingBox boundingBox(BigDecimal lat, BigDecimal lng, double radiusMeters) {
    double deltaLatDeg = radiusMeters / METERS_PER_DEGREE_LATITUDE;
    double cosLat = Math.max(Math.cos(Math.toRadians(lat.doubleValue())), MIN_COS_LATITUDE);
    double deltaLngDeg = radiusMeters / (METERS_PER_DEGREE_LATITUDE * cosLat);

    BigDecimal deltaLat = BigDecimal.valueOf(deltaLatDeg);
    BigDecimal deltaLng = BigDecimal.valueOf(deltaLngDeg);
    return new BoundingBox(
        lat.subtract(deltaLat), lat.add(deltaLat), lng.subtract(deltaLng), lng.add(deltaLng));
  }

  private record LocationWithDistance(Location location, double distance) {}

  private record BoundingBox(
      BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {}

  /** BR-ADMIN-003: only an Administrator may create, edit, or retire a location (issue #144). */
  @Transactional
  public AdminLocationResponse createLocation(UUID adminId, LocationCreateRequest request) {
    Location location =
        Location.builder()
            .name(request.getName().trim())
            .address(request.getAddress().trim())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .phoneNumber(normalize(request.getPhoneNumber()))
            .placeType(Location.PlaceType.valueOf(request.getPlaceType()))
            .openingHours(serializeOpeningHours(request.getOpeningHours()))
            .websiteUrl(normalize(request.getWebsiteUrl()))
            .rating(request.getRating())
            .priceLevel(request.getPriceLevel())
            .imageUrls(request.getImageUrls())
            .description(normalize(request.getDescription()))
            .build();

    Location saved = locationRepository.saveAndFlush(location);
    recordModeration(adminId, "CREATE_LOCATION", saved.getId());
    return adminLocationMapper.toResponse(saved);
  }

  /** Partial update: only supplied fields change; a blank text field clears it (issue #144). */
  @Transactional
  public AdminLocationResponse updateLocation(
      UUID adminId, UUID locationId, LocationUpdateRequest request) {
    Location location = findActiveLocation(locationId);

    if (request.getName() != null) {
      location.setName(request.getName().trim());
    }
    if (request.getAddress() != null) {
      location.setAddress(request.getAddress().trim());
    }
    if (request.getLatitude() != null) {
      location.setLatitude(request.getLatitude());
    }
    if (request.getLongitude() != null) {
      location.setLongitude(request.getLongitude());
    }
    if (request.getPhoneNumber() != null) {
      location.setPhoneNumber(normalize(request.getPhoneNumber()));
    }
    if (request.getPlaceType() != null) {
      location.setPlaceType(Location.PlaceType.valueOf(request.getPlaceType()));
    }
    if (request.getOpeningHours() != null) {
      location.setOpeningHours(serializeOpeningHours(request.getOpeningHours()));
    }
    if (request.getWebsiteUrl() != null) {
      location.setWebsiteUrl(normalize(request.getWebsiteUrl()));
    }
    if (request.getRating() != null) {
      location.setRating(request.getRating());
    }
    if (request.getPriceLevel() != null) {
      location.setPriceLevel(request.getPriceLevel());
    }
    if (request.getImageUrls() != null) {
      location.setImageUrls(request.getImageUrls());
    }
    if (request.getDescription() != null) {
      location.setDescription(normalize(request.getDescription()));
    }

    Location saved = locationRepository.saveAndFlush(location);
    recordModeration(adminId, "UPDATE_LOCATION", saved.getId());
    return adminLocationMapper.toResponse(saved);
  }

  /** Soft delete: {@code deletedAt} is set, the row is kept (issue #144). */
  @Transactional
  public void deleteLocation(UUID adminId, UUID locationId) {
    Location location = findActiveLocation(locationId);
    location.setDeletedAt(Instant.now());
    Location saved = locationRepository.saveAndFlush(location);
    recordModeration(adminId, "DELETE_LOCATION", saved.getId());
  }

  private Location findActiveLocation(UUID locationId) {
    return locationRepository
        .findById(locationId)
        .filter(l -> l.getDeletedAt() == null)
        .orElseThrow(() -> new ResourceNotFoundException("Location not found"));
  }

  /** BR-ADMIN-002: every admin create/update/delete is recorded in the moderation log. */
  private void recordModeration(UUID adminId, String action, UUID targetId) {
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action(action)
            .targetType("LOCATION")
            .targetId(targetId)
            .build());
  }

  private String serializeOpeningHours(JsonNode openingHours) {
    if (openingHours == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(openingHours);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize opening hours", e);
    }
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
