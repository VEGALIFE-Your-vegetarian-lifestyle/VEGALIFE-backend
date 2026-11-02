package com.vegalife.dto.mapper.media;

import com.vegalife.dto.response.media.MediaResponse;
import com.vegalife.model.post.Media;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MediaMapper {

  @Mapping(target = "mediaId", source = "id")
  MediaResponse toResponse(Media media);
}
