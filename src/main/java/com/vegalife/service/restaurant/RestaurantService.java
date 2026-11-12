package com.vegalife.service.restaurant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.dto.mapper.admin.AdminRestaurantMapper;
import com.vegalife.dto.mapper.restaurant.RestaurantMapper;
import com.vegalife.dto.request.restaurant.RestaurantCreateRequest;
import com.vegalife.dto.request.restaurant.RestaurantListRequest;
import com.vegalife.dto.request.restaurant.RestaurantNearbyRequest;
import com.vegalife.dto.request.restaurant.RestaurantUpdateRequest;
import com.vegalife.dto.response.admin.AdminRestaurantResponse;
import com.vegalife.dto.response.restaurant.RestaurantDishResponse;
import com.vegalife.dto.response.restaurant.RestaurantResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.restaurant.Restaurant;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.restaurant.RestaurantRepository;
import com.vegalife.repository.restaurant.RestaurantRepository.RestaurantDishMatch;
import com.vegalife.repository.restaurant.RestaurantSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommend-restaurants list (issue #9): active restaurants filtered by rating/price/open-now,
 * sorted by rating descending (unrated last), then creation time and id ascending for stable
 * paging. Open-now, pagination, and the tie-break sort stay a single code path by loading every
 * active match for the structured filters up front and ordering/paging it in memory: a restaurant
 * with no opening hours can only be excluded after parsing its document, which the database cannot
 * do, and "unrated last" needs a {@code Comparator} since {@code
 * JpaSpecificationExecutor.findAll(Specification, Sort)} builds its order through the Criteria API,
 * which does not support {@code Sort.Order.nullsLast()} (throws {@code
 * UnsupportedOperationException}).
 */
@Service
@RequiredArgsConstructor
public class RestaurantService {

  /** 1 degree of latitude is ~111.32 km everywhere; used to size the bounding box (issue #10). */
  private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;

  /** Avoids a division blow-up in the longitude delta for a search centre near the poles. */
  private static final double MIN_COS_LATITUDE = 1e-6;

  private static final Comparator<Restaurant> RESULT_ORDER =
      Comparator.comparing(Restaurant::getRating, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Restaurant::getCreatedAt)
          .thenComparing(Restaurant::getId);

  private final RestaurantRepository restaurantRepository;
  private final RestaurantMapper restaurantMapper;
  private final AdminRestaurantMapper adminRestaurantMapper;
  private final ModerationLogRepository moderationLogRepository;
  private final DishRepository dishRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public PageResponse<RestaurantResponse> listRestaurants(RestaurantListRequest request) {
    String keyword = normalizeKeyword(request.getKeyword());
    List<Restaurant> matches =
        keyword == null
            ? loadByStructuralFilters(request.getMinRating(), request.getMaxPriceLevel())
            : loadByKeyword(keyword, request.getMinRating(), request.getMaxPriceLevel());

    matches.sort(RESULT_ORDER);
    List<Restaurant> filtered = request.isOpenNow() ? filterOpenNow(matches) : matches;

    Page<Restaurant> page = paginate(filtered, request.getPage(), request.getSize());
    return PageResponse.from(page.map(restaurantMapper::toResponse));
  }

  private String normalizeKeyword(String keyword) {
    return keyword == null || keyword.isBlank() ? null : keyword.trim();
  }

  private List<Restaurant> loadByStructuralFilters(BigDecimal minRating, Integer maxPriceLevel) {
    Specification<Restaurant> spec =
        RestaurantSpecifications.activeWithFilters(minRating, maxPriceLevel);
    return new ArrayList<>(restaurantRepository.findAll(spec));
  }

  /**
   * Candidate restaurants for a keyword filter (issue #196): the name/description ILIKE match
   * (already AND-ed with minRating/maxPriceLevel by the {@link Specification}) unioned with every
   * restaurant whose attached, approved dish name matches the same keyword — each dish-matched-only
   * restaurant is still required to pass minRating/maxPriceLevel in Java, so the union never
   * bypasses those filters. Deduplicated by id via the map's insertion order.
   */
  private List<Restaurant> loadByKeyword(
      String keyword, BigDecimal minRating, Integer maxPriceLevel) {
    Specification<Restaurant> spec =
        RestaurantSpecifications.activeWithFilters(minRating, maxPriceLevel, keyword);
    Map<UUID, Restaurant> candidates = new LinkedHashMap<>();
    for (Restaurant restaurant : restaurantRepository.findAll(spec)) {
      candidates.put(restaurant.getId(), restaurant);
    }

    List<UUID> dishMatchedIds =
        restaurantRepository.findDishNameMatches(keyword).stream()
            .map(RestaurantDishMatch::getRestaurantId)
            .distinct()
            .filter(id -> !candidates.containsKey(id))
            .toList();
    if (!dishMatchedIds.isEmpty()) {
      for (Restaurant restaurant : restaurantRepository.findAllById(dishMatchedIds)) {
        if (restaurant.getDeletedAt() == null
            && passesStructuralFilters(restaurant, minRating, maxPriceLevel)) {
          candidates.put(restaurant.getId(), restaurant);
        }
      }
    }
    return new ArrayList<>(candidates.values());
  }

  private boolean passesStructuralFilters(
      Restaurant restaurant, BigDecimal minRating, Integer maxPriceLevel) {
    if (minRating != null
        && (restaurant.getRating() == null || restaurant.getRating().compareTo(minRating) < 0)) {
      return false;
    }
    return maxPriceLevel == null
        || (restaurant.getPriceLevel() != null && restaurant.getPriceLevel() <= maxPriceLevel);
  }

  private List<Restaurant> filterOpenNow(List<Restaurant> restaurants) {
    LocalDateTime now = LocalDateTime.now();
    return restaurants.stream()
        .filter(
            restaurant ->
                OpeningHoursEvaluator.isOpenNow(restaurant.getOpeningHours(), objectMapper, now))
        .toList();
  }

  private Page<Restaurant> paginate(List<Restaurant> restaurants, int page, int size) {
    int fromIndex = Math.min(page * size, restaurants.size());
    int toIndex = Math.min(fromIndex + size, restaurants.size());
    List<Restaurant> pageContent = restaurants.subList(fromIndex, toIndex);
    return new PageImpl<>(pageContent, PageRequest.of(page, size), restaurants.size());
  }

  /**
   * Every active restaurant within {@code radius} metres of (lat, lng), nearest first, with no
   * pagination (issue #10). A bounding-box {@link Specification} does the coarse, index-friendly
   * pre-filter over {@code idx_restaurant_coords}; haversine then applies the exact radius cut and
   * computes the distance each row is sorted and returned with.
   */
  @Transactional(readOnly = true)
  public List<RestaurantResponse> listNearby(RestaurantNearbyRequest request) {
    BigDecimal lat = request.getLat();
    BigDecimal lng = request.getLng();
    double radiusMeters = request.getRadius().doubleValue();

    BoundingBox box = boundingBox(lat, lng, radiusMeters);
    Specification<Restaurant> spec =
        RestaurantSpecifications.activeWithinBoundingBox(
            box.minLat(), box.maxLat(), box.minLng(), box.maxLng());
    List<Restaurant> candidates = restaurantRepository.findAll(spec);

    List<RestaurantWithDistance> withinRadius = new ArrayList<>();
    for (Restaurant candidate : candidates) {
      double distance =
          HaversineDistanceCalculator.distanceInMeters(
              lat, lng, candidate.getLatitude(), candidate.getLongitude());
      if (distance <= radiusMeters) {
        withinRadius.add(new RestaurantWithDistance(candidate, distance));
      }
    }
    withinRadius.sort(Comparator.comparingDouble(RestaurantWithDistance::distance));

    return withinRadius.stream()
        .map(item -> restaurantMapper.toResponse(item.restaurant(), item.distance()))
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

  private record RestaurantWithDistance(Restaurant restaurant, double distance) {}

  private record BoundingBox(
      BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {}

  /** BR-ADMIN-003: only an Administrator may create, edit, or retire a restaurant (issue #144). */
  @Transactional
  public AdminRestaurantResponse createRestaurant(UUID adminId, RestaurantCreateRequest request) {
    // Resolved before the entity is built/saved (issue #195): an unknown/unapproved dish id must
    // fail the whole request without writing anything.
    Set<Dish> dishes =
        request.getDishIds() != null ? resolveDishes(request.getDishIds()) : new HashSet<>();

    Restaurant restaurant =
        Restaurant.builder()
            .name(request.getName().trim())
            .address(request.getAddress().trim())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .phoneNumber(normalize(request.getPhoneNumber()))
            .openingHours(serializeOpeningHours(request.getOpeningHours()))
            .websiteUrl(normalize(request.getWebsiteUrl()))
            .rating(request.getRating())
            .priceLevel(request.getPriceLevel())
            .imageUrls(request.getImageUrls())
            .description(normalize(request.getDescription()))
            .dishes(dishes)
            .build();

    Restaurant saved = restaurantRepository.saveAndFlush(restaurant);
    recordModeration(adminId, "CREATE_RESTAURANT", saved.getId());
    AdminRestaurantResponse response = adminRestaurantMapper.toResponse(saved);
    response.setDishes(toDishResponses(saved.getDishes()));
    return response;
  }

  /** Partial update: only supplied fields change; a blank text field clears it (issue #144). */
  @Transactional
  public AdminRestaurantResponse updateRestaurant(
      UUID adminId, UUID restaurantId, RestaurantUpdateRequest request) {
    Restaurant restaurant = findActiveRestaurant(restaurantId);

    if (request.getName() != null) {
      restaurant.setName(request.getName().trim());
    }
    if (request.getAddress() != null) {
      restaurant.setAddress(request.getAddress().trim());
    }
    if (request.getLatitude() != null) {
      restaurant.setLatitude(request.getLatitude());
    }
    if (request.getLongitude() != null) {
      restaurant.setLongitude(request.getLongitude());
    }
    if (request.getPhoneNumber() != null) {
      restaurant.setPhoneNumber(normalize(request.getPhoneNumber()));
    }
    if (request.getOpeningHours() != null) {
      restaurant.setOpeningHours(serializeOpeningHours(request.getOpeningHours()));
    }
    if (request.getWebsiteUrl() != null) {
      restaurant.setWebsiteUrl(normalize(request.getWebsiteUrl()));
    }
    if (request.getRating() != null) {
      restaurant.setRating(request.getRating());
    }
    if (request.getPriceLevel() != null) {
      restaurant.setPriceLevel(request.getPriceLevel());
    }
    if (request.getImageUrls() != null) {
      restaurant.setImageUrls(request.getImageUrls());
    }
    if (request.getDescription() != null) {
      restaurant.setDescription(normalize(request.getDescription()));
    }
    if (request.getDishIds() != null) {
      // Replace semantics (issue #195): [] clears, non-empty replaces; resolved before the save
      // below so an invalid id leaves the existing dishes untouched.
      restaurant.setDishes(
          request.getDishIds().isEmpty() ? new HashSet<>() : resolveDishes(request.getDishIds()));
    }

    Restaurant saved = restaurantRepository.saveAndFlush(restaurant);
    recordModeration(adminId, "UPDATE_RESTAURANT", saved.getId());
    AdminRestaurantResponse response = adminRestaurantMapper.toResponse(saved);
    response.setDishes(toDishResponses(saved.getDishes()));
    return response;
  }

  /** Public detail read (issue #195): active restaurant only, with its curated dishes nested. */
  @Transactional(readOnly = true)
  public RestaurantResponse getRestaurant(UUID restaurantId) {
    Restaurant restaurant =
        restaurantRepository
            .findByIdAndDeletedAtIsNull(restaurantId)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
    RestaurantResponse response = restaurantMapper.toResponse(restaurant);
    response.setDishes(toDishResponses(restaurant.getDishes()));
    return response;
  }

  /** Soft delete: {@code deletedAt} is set, the row is kept (issue #144). */
  @Transactional
  public void deleteRestaurant(UUID adminId, UUID restaurantId) {
    Restaurant restaurant = findActiveRestaurant(restaurantId);
    restaurant.setDeletedAt(Instant.now());
    Restaurant saved = restaurantRepository.saveAndFlush(restaurant);
    recordModeration(adminId, "DELETE_RESTAURANT", saved.getId());
  }

  private Restaurant findActiveRestaurant(UUID restaurantId) {
    return restaurantRepository
        .findById(restaurantId)
        .filter(r -> r.getDeletedAt() == null)
        .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
  }

  /** BR-ADMIN-002: every admin create/update/delete is recorded in the moderation log. */
  private void recordModeration(UUID adminId, String action, UUID targetId) {
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action(action)
            .targetType("RESTAURANT")
            .targetId(targetId)
            .build());
  }

  /**
   * Resolves and validates dish ids to attach (issue #195): duplicates are collapsed first, then
   * every id must resolve to an approved, non-deleted {@link Dish} or the whole request is refused
   * — nothing about this method writes anything, so a caller can run it before any save and be sure
   * a 404 here leaves the database untouched.
   */
  private Set<Dish> resolveDishes(List<UUID> dishIds) {
    if (dishIds == null || dishIds.isEmpty()) {
      return new HashSet<>();
    }
    Set<UUID> distinctIds = new HashSet<>(dishIds);
    List<Dish> found =
        dishRepository.findByIdInAndDeletedAtIsNullAndModerationStatus(
            distinctIds, ModerationStatus.APPROVED);
    if (found.size() != distinctIds.size()) {
      throw new ResourceNotFoundException("One or more dishes are not found or are not approved.");
    }
    return new HashSet<>(found);
  }

  /**
   * Maps a restaurant's attached dishes to the nested response shape (issue #195): soft-deleted
   * dishes are filtered out at read time (even though attach-time validation already required
   * APPROVED, a dish can be soft-deleted afterwards without touching the join row), and the result
   * is ordered by name.
   */
  private List<RestaurantDishResponse> toDishResponses(Set<Dish> dishes) {
    if (dishes == null) {
      return List.of();
    }
    return dishes.stream()
        .filter(dish -> dish.getDeletedAt() == null)
        .sorted(Comparator.comparing(Dish::getName))
        .map(
            dish ->
                RestaurantDishResponse.builder()
                    .id(dish.getId())
                    .name(dish.getName())
                    .description(dish.getDescription())
                    .imageUrl(dish.getImageUrl())
                    .cuisineType(dish.getCuisineType())
                    .build())
        .toList();
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
