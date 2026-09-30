package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminVideoMapper;
import com.vegalife.dto.request.admin.VideoListRequest;
import com.vegalife.dto.response.admin.AdminVideoListResponse;
import com.vegalife.dto.response.admin.AdminVideoPostResponse;
import com.vegalife.model.post.Media;
import com.vegalife.repository.post.MediaPostRow;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.MediaSpecifications;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminVideoService {

  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of("createdAt", "updatedAt", "durationSeconds", "fileSizeBytes");

  private static final String SORTABLE_PROPERTIES_MESSAGE =
      "Sort property must be one of: createdAt, updatedAt, durationSeconds, fileSizeBytes";

  private final MediaRepository mediaRepository;
  private final PostRepository postRepository;
  private final AdminVideoMapper adminVideoMapper;

  @Transactional(readOnly = true)
  public PageResponse<AdminVideoListResponse> listVideos(VideoListRequest request) {
    Media.Status status = parseStatus(request.getStatus());
    UUID userId = request.getUserId();
    Instant createdFrom = request.getCreatedFrom();
    Instant createdTo = request.getCreatedTo();

    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<Media> spec =
        MediaSpecifications.allVideosWithFilters(status, userId, createdFrom, createdTo);
    Page<Media> page = mediaRepository.findAll(spec, pageable);

    Map<UUID, List<AdminVideoPostResponse>> postsByMediaId = loadPostsByMediaId(page.getContent());

    Page<AdminVideoListResponse> mapped =
        page.map(
            media -> {
              AdminVideoListResponse response = adminVideoMapper.toResponse(media);
              response.setPosts(postsByMediaId.getOrDefault(media.getId(), List.of()));
              return response;
            });

    log.debug(
        "Listed videos page={} size={} total={}",
        mapped.getNumber(),
        mapped.getSize(),
        mapped.getTotalElements());

    return PageResponse.from(mapped);
  }

  /** One query for the whole page: (mediaId, post) pairs grouped back onto each video. */
  private Map<UUID, List<AdminVideoPostResponse>> loadPostsByMediaId(List<Media> media) {
    if (media.isEmpty()) {
      return Map.of();
    }
    List<UUID> mediaIds = media.stream().map(Media::getId).toList();
    return postRepository.findPostRowsByMediaIdsIn(mediaIds).stream()
        .collect(
            Collectors.groupingBy(
                MediaPostRow::mediaId,
                Collectors.mapping(
                    row -> adminVideoMapper.toPostResponse(row.post()), Collectors.toList())));
  }

  private Media.Status parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return Media.Status.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("Status must be one of: uploading, succeed, failed");
    }
  }

  private Sort parseSort(String sort) {
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2) {
      throw new ValidationException("Sort must be in the form property,asc|desc");
    }
    String property = parts[0].trim();
    if (property.isBlank()) {
      throw new ValidationException("Sort property must not be blank");
    }
    if (!SORTABLE_PROPERTIES.contains(property)) {
      throw new ValidationException(SORTABLE_PROPERTIES_MESSAGE);
    }
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("asc")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(direction, property);
  }
}
