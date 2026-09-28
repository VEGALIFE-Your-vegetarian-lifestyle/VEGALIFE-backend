package com.vegalife.service.post;

import com.vegalife.dto.mapper.post.PostMapper;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.request.post.PostListRequest;
import com.vegalife.dto.request.post.PostUpdateRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final CategoryRepository categoryRepository;
  private final MediaRepository mediaRepository;
  private final PostMapper postMapper;

  @Transactional
  public PostListResponse createPost(UUID userId, PostCreateRequest request) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    validateTypeSpecificFields(request);
    Set<Category> categories = resolveCategories(request);
    Media media = resolveMedia(request);

    Post post = postMapper.toEntity(request);
    post.setUser(user);
    post.setViewCount(0);
    post.setCategories(categories);
    if (media != null) {
      post.setMedia(new HashSet<>(Set.of(media)));
    }
    if (post.getContent() == null) {
      post.setContent("");
    }

    // BR-CONTENT-003: draft stays private (created); publish is only reachable once
    // all requirements above are satisfied.
    if (request.isPublish()) {
      post.setStatus(Post.Status.published);
      post.setPublishedAt(Instant.now());
    } else {
      post.setStatus(Post.Status.created);
    }

    return postMapper.toListResponse(postRepository.saveAndFlush(post));
  }

  @Transactional
  public PostListResponse updatePost(UUID userId, UUID postId, PostUpdateRequest request) {
    Post post =
        postRepository
            .findByIdAndUser_IdAndDeletedAtIsNull(postId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    if (request.getTitle() != null) {
      post.setTitle(request.getTitle());
    }
    if (request.getContent() != null) {
      post.setContent(request.getContent());
    }
    if (request.getFeaturedImageUrl() != null) {
      post.setFeaturedImageUrl(request.getFeaturedImageUrl());
    }

    return postMapper.toListResponse(postRepository.saveAndFlush(post));
  }

  @Transactional(readOnly = true)
  public PageResponse<PostListResponse> listUserPosts(UUID userId, PostListRequest request) {
    Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
    Page<Post> posts =
        postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);

    return PageResponse.from(posts.map(postMapper::toListResponse));
  }

  /** BR-CONTENT-002: a blog needs written content, a video needs a file or a link. */
  private void validateTypeSpecificFields(PostCreateRequest request) {
    if (request.getType() == Post.Type.blog) {
      if (isBlank(request.getContent())) {
        throw new ValidationException("Content is required for a blog post");
      }
      if (!isBlank(request.getVideoUrl()) || request.getMediaId() != null) {
        throw new ValidationException("A blog post cannot have a video");
      }
    } else if (isBlank(request.getVideoUrl()) && request.getMediaId() == null) {
      throw new ValidationException("A video post requires a video file or link");
    }
  }

  /** BR-CONTENT-003 / BR-CONTENT-004: publishing needs at least one active category. */
  private Set<Category> resolveCategories(PostCreateRequest request) {
    Set<UUID> ids = request.getCategoryIds() == null ? Set.of() : request.getCategoryIds();
    if (request.isPublish() && ids.isEmpty()) {
      throw new ValidationException("At least one category is required to publish a post");
    }
    if (ids.isEmpty()) {
      return new HashSet<>();
    }
    List<Category> found = categoryRepository.findByIdInAndDeletedAtIsNull(ids);
    if (found.size() != ids.size()) {
      throw new ValidationException("One or more categories do not exist or are inactive");
    }
    return new HashSet<>(found);
  }

  private Media resolveMedia(PostCreateRequest request) {
    if (request.getMediaId() == null) {
      return null;
    }
    Media media =
        mediaRepository.findByIdInAndDeletedAtIsNull(Set.of(request.getMediaId())).stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
    if (media.getStatus() != Media.Status.succeed) {
      throw new ValidationException("Media upload has not completed");
    }
    return media;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
