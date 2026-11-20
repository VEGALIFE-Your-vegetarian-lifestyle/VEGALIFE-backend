package com.vegalife.dto.mapper.restaurant;

import com.vegalife.dto.response.restaurant.RestaurantResponse;
import com.vegalife.model.restaurant.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = OpeningHoursJsonConverter.class)
public interface RestaurantMapper {

  /**
   * {@code dishes} is intentionally left unmapped here (issue #195): list/nearby callers never
   * populate it, and the detail endpoint sets it afterwards via the service's own filter/sort.
   */
  @Mapping(target = "dishes", ignore = true)
  RestaurantResponse toResponse(Restaurant restaurant);

  /** Issue #10: same row shape as {@link #toResponse(Restaurant)} plus a computed distance. */
  @Mapping(target = "distance", source = "distance")
  @Mapping(target = "dishes", ignore = true)
  RestaurantResponse toResponse(Restaurant restaurant, Double distance);
}
