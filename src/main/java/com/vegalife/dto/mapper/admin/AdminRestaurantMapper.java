package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.mapper.restaurant.OpeningHoursJsonConverter;
import com.vegalife.dto.response.admin.AdminRestaurantResponse;
import com.vegalife.model.restaurant.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = OpeningHoursJsonConverter.class)
public interface AdminRestaurantMapper {

  /**
   * {@code dishes} is intentionally left unmapped here (issue #195) — the service sets it
   * afterwards via its own filter/sort over the restaurant's {@code dishes} collection.
   */
  @Mapping(target = "dishes", ignore = true)
  AdminRestaurantResponse toResponse(Restaurant restaurant);
}
