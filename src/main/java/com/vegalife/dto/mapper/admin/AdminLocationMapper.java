package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.mapper.location.OpeningHoursJsonConverter;
import com.vegalife.dto.response.admin.AdminLocationResponse;
import com.vegalife.model.post.Location;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = OpeningHoursJsonConverter.class)
public interface AdminLocationMapper {

  AdminLocationResponse toResponse(Location location);
}
