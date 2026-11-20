package com.vegalife.repository.restaurant;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.restaurant.Restaurant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RestaurantRepository
    extends JpaRepository<Restaurant, UUID>, JpaSpecificationExecutor<Restaurant> {

  /**
   * A restaurant is visible on the public detail endpoint only while not soft-deleted (issue #195).
   */
  Optional<Restaurant> findByIdAndDeletedAtIsNull(UUID id);

  /**
   * Every (restaurantId, dishName) pair across ALL restaurants whose attached, approved,
   * non-deleted dish name contains {@code keyword} (case-insensitive) — issue #196. One query
   * regardless of how many restaurants exist: the keyword/description match on {@code
   * RestaurantSpecifications} and this dish-name match are unioned together in Java at the service
   * layer, since {@code restaurant_dish} is a plain join table with no entity of its own to root a
   * subquery on from the {@code Dish} side (and an inverse {@code @ManyToMany} on {@link
   * com.vegalife.model.recipe.Dish} would wrongly couple {@code model.recipe} to {@code
   * model.restaurant}). The {@code JOIN r.dishes d} correlates each match to its own restaurant, so
   * a dish attached only to a different restaurant can never produce a false match here.
   */
  @Query(
      """
      SELECT r.id AS restaurantId, d.name AS dishName
      FROM Restaurant r JOIN r.dishes d
      WHERE r.deletedAt IS NULL
        AND d.deletedAt IS NULL
        AND d.moderationStatus = :status
        AND LOWER(d.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
      """)
  List<RestaurantDishMatch> findDishNameMatches(
      @Param("keyword") String keyword, @Param("status") ModerationStatus status);

  /** Convenience overload: only an approved, non-deleted dish name is ever eligible to match. */
  default List<RestaurantDishMatch> findDishNameMatches(String keyword) {
    return findDishNameMatches(keyword, ModerationStatus.APPROVED);
  }

  /** Projection row for {@link #findDishNameMatches}. */
  interface RestaurantDishMatch {
    UUID getRestaurantId();

    String getDishName();
  }
}
