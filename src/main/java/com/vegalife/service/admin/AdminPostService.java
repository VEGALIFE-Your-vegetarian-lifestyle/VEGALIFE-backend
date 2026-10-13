package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminPostMapper;
import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.request.admin.PostModerationRequest;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Post;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.PostSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
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
public class AdminPostService {

  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of("createdAt", "publishedAt", "updatedAt", "viewCount", "title");

  private static final String SORTABLE_PROPERTIES_MESSAGE =
      "Sort property must be one of: createdAt, publishedAt, updatedAt, viewCount, title";

  private static final String CATEGORY_REQUIRED_TO_PUBLISH =
      "At least one category is required to publish a post";

  private final PostRepository postRepository;
  private final ModerationLogRepository moderationLogRepository;
  private final AdminPostMapper adminPostMapper;

  @Transactional(readOnly = true)
  public PageResponse<AdminPostListResponse> listPosts(PostListRequest request) {
    Post.Status status = parseStatus(request.getStatus());
    Post.Flag flag = parseFlag(request.getFlag());
    UUID userId = request.getUserId();
    UUID categoryId = request.getCategoryId();
    Instant createdFrom = request.getCreatedFrom();
    Instant createdTo = request.getCreatedTo();

    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<Post> spec =
        PostSpecifications.allWithFilters(status, flag, userId, categoryId, createdFrom, createdTo);
    Page<Post> page = postRepository.findAll(spec, pageable);

    log.debug(
        "Listed posts page={} size={} total={}",
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());

    Page<AdminPostListResponse> mapped = page.map(adminPostMapper::toResponse);
    return PageResponse.from(mapped);
  }

  /**
   * Issue #5: an Administrator publishes a filter-withheld post or unpublishes a live one. Only the
   * visibility axis ({@link Post.Status}) changes — the filter verdict ({@link Post.Flag}) is left
   * untouched (ADR-011). A publish requires at least one category (BR-CONTENT-003). A no-op (target
   * status already reached) returns the post unchanged and writes no log entry. Every real change
   * is recorded in {@code moderation_log} with the acting admin and the supplied reason.
   */
  @Transactional
  public AdminPostListResponse moderatePost(
      UUID adminId, UUID postId, PostModerationRequest request) {
    Post post =
        postRepository
            .findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    if (post.getStatus() == Post.Status.hidden) {
      throw new ValidationException(
          "A hidden post is moderated through the visibility endpoint, not publish/unpublish");
    }

    boolean publish = request.isPublish();
    Post.Status target = publish ? Post.Status.published : Post.Status.unpublished;

    if (post.getStatus() == target) {
      return adminPostMapper.toResponse(post);
    }

    if (publish && post.getCategories().isEmpty()) {
      throw new ValidationException(CATEGORY_REQUIRED_TO_PUBLISH);
    }

    post.setStatus(target);
    post.setPublishedAt(publish ? Instant.now() : null);
    Post saved = postRepository.saveAndFlush(post);

    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action(publish ? "PUBLISH_POST" : "UNPUBLISH_POST")
            .targetType("POST")
            .targetId(saved.getId())
            .reason(request.getReason())
            .build());

    log.info(
        "Admin {} {} post {} (reason present={})",
        adminId,
        publish ? "published" : "unpublished",
        saved.getId(),
        request.getReason() != null);

    return adminPostMapper.toResponse(saved);
  }

  private Post.Status parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return Post.Status.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException(
          "Status must be one of: created, processed, published, unpublished, hidden");
    }
  }

  private Post.Flag parseFlag(String flag) {
    if (flag == null || flag.isBlank() || "all".equalsIgnoreCase(flag)) {
      return null;
    }
    try {
      return Post.Flag.valueOf(flag);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException(
          "Flag must be one of: all, PENDING, PASSED, REJECTED, NEEDS_REVIEW");
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
