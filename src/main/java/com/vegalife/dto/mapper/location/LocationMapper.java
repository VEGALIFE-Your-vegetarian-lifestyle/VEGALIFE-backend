package com.vegalife.dto.mapper.location;

import com.vegalife.dto.response.location.LocationResponse;
import com.vegalife.model.post.Location;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = OpeningHoursJsonConverter.class)
public interface LocationMapper {

  LocationResponse toResponse(Location location);

  /** Issue #10: same row shape as {@link #toResponse(Location)} plus a computed distance. */
  @Mapping(target = "distance", source = "distance")
  LocationResponse toResponse(Location location, Double distance);
}
