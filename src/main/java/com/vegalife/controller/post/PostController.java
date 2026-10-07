package com.vegalife.controller.post;

import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.request.post.PostListRequest;
import com.vegalife.dto.request.post.PostUpdateRequest;
import com.vegalife.dto.request.post.PostVisibilityRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.service.post.PostService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class PostController {

  private final PostService postService;

  @PostMapping
  public ResponseEntity<ApiResponse<PostListResponse>> createPost(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody PostCreateRequest request) {
    PostListResponse post = postService.createPost(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(post, "Post created successfully"));
  }

  @GetMapping("/{postId}")
  public ResponseEntity<ApiResponse<PostListResponse>> getPost(@PathVariable UUID postId) {
    PostListResponse post = postService.getPost(postId);
    return ResponseEntity.ok(ApiResponse.success(post, "Post retrieved successfully"));
  }

  @PatchMapping("/{postId}")
  public ResponseEntity<ApiResponse<PostListResponse>> updatePost(
      @AuthenticationPrincipal UUID userId,
      Authentication authentication,
      @PathVariable UUID postId,
      @Valid @RequestBody PostUpdateRequest request) {
    PostListResponse post =
        postService.updatePost(userId, isAdmin(authentication), postId, request);
    return ResponseEntity.ok(ApiResponse.success(post, "Post updated successfully"));
  }

  @PatchMapping("/{postId}/visibility")
  public ResponseEntity<ApiResponse<PostListResponse>> updateVisibility(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID postId,
      @Valid @RequestBody PostVisibilityRequest request) {
    PostListResponse post = postService.updateVisibility(adminId, postId, request.getHidden());
    String message = request.getHidden() ? "Post hidden successfully" : "Post is visible again";
    return ResponseEntity.ok(ApiResponse.success(post, message));
  }

  @DeleteMapping("/{postId}")
  public ResponseEntity<ApiResponse<Void>> deletePost(
      @AuthenticationPrincipal UUID userId,
      Authentication authentication,
      @PathVariable UUID postId) {
    postService.deletePost(userId, isAdmin(authentication), postId);
    return ResponseEntity.ok(ApiResponse.success(null, "Post deleted successfully"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> listUserPosts(
      @AuthenticationPrincipal UUID userId, @Valid @ModelAttribute PostListRequest request) {
    PageResponse<PostListResponse> posts = postService.listUserPosts(userId, request);
    return ResponseEntity.ok(ApiResponse.success(posts, "Posts retrieved successfully"));
  }

  @GetMapping("/feed")
  public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> listFeed(
      @Valid @ModelAttribute PostListRequest request) {
    PageResponse<PostListResponse> posts = postService.listFeed(request);
    return ResponseEntity.ok(ApiResponse.success(posts, "Posts retrieved successfully"));
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
