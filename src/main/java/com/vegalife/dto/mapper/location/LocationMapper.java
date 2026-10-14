package com.vegalife.dto.mapper.location;

import com.vegalife.dto.response.location.LocationResponse;
import com.vegalife.model.post.Location;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = OpeningHoursJsonConverter.class)
public interface LocationMapper {

  LocationResponse toResponse(Location location);
}
