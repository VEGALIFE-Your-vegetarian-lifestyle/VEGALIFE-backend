package com.vegalife.repository.restaurant;

import com.vegalife.model.restaurant.Restaurant;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class RestaurantSpecifications {

  private RestaurantSpecifications() {}

  /**
   * Every non-deleted restaurant, optionally narrowed by minimum rating and maximum price level
   * (issue #9). A restaurant whose {@code rating} or {@code priceLevel} is unset never satisfies a
   * minRating/maxPriceLevel filter, since its value is unknown rather than qualifying.
   */
  public static Specification<Restaurant> activeWithFilters(
      BigDecimal minRating, Integer maxPriceLevel) {
    return activeWithFilters(minRating, maxPriceLevel, null);
  }

  /**
   * Same query, additionally narrowed by a case-insensitive {@code keyword} match against {@code
   * name} or {@code description} (issue #196). A blank/null keyword never narrows the results; the
   * dish-name side of the keyword match cannot be expressed here (no entity to join the
   * restaurant-dish relation from the {@code Dish} side without coupling {@code model.recipe} to
   * {@code model.restaurant} — see {@link RestaurantRepository#findDishNameMatches}) and is unioned
   * in at the service layer instead.
   */
  public static Specification<Restaurant> activeWithFilters(
      BigDecimal minRating, Integer maxPriceLevel, String keyword) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));

      if (minRating != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating));
      }
      if (maxPriceLevel != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("priceLevel"), maxPriceLevel));
      }
      if (keyword != null && !keyword.isBlank()) {
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Non-deleted restaurants whose name or description contains {@code query} (case-insensitive),
   * optionally narrowed by createdAt range (issue #196), for reuse by {@code SearchService}.
   * Mirrors {@code RecipeSpecifications#searchable}: no category filter (a restaurant has no
   * category of its own) and the dish-name side of the match is unioned in afterward at the call
   * site, the same way {@link #activeWithFilters(BigDecimal, Integer, String)} does for the list
   * endpoint.
   */
  public static Specification<Restaurant> searchable(
      String query, Instant createdFrom, Instant createdTo) {
    return (root, cq, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));

      String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
      predicates.add(
          cb.or(
              cb.like(cb.lower(root.get("name")), pattern),
              cb.like(cb.lower(root.get("description")), pattern)));

      if (createdFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
      }
      if (createdTo != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Every non-deleted restaurant whose coordinates fall inside a lat/lng bounding box — a coarse,
   * index-friendly pre-filter over {@code idx_restaurant_coords} (issue #10). The exact radius cut
   * and distance are computed afterward with the haversine formula, since a bounding box is a
   * rectangle, not a circle.
   */
  public static Specification<Restaurant> activeWithinBoundingBox(
      BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {
    return (root, query, cb) ->
        cb.and(
            cb.isNull(root.get("deletedAt")),
            cb.between(root.get("latitude"), minLat, maxLat),
            cb.between(root.get("longitude"), minLng, maxLng));
  }
}
