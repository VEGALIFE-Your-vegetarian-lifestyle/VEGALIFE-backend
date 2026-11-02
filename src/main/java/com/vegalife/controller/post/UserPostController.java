package com.vegalife.controller.post;

import com.vegalife.dto.request.post.PostListRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.service.post.PostService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public listing of another member's posts; the viewer may be a guest. */
@RestController
@RequestMapping("/api/users/{userId}/posts")
@RequiredArgsConstructor
public class UserPostController {

  private final PostService postService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> listPostsOfUser(
      Authentication authentication,
      @PathVariable UUID userId,
      @Valid @ModelAttribute PostListRequest request) {
    UUID viewerId =
        authentication != null && authentication.getPrincipal() instanceof UUID id ? id : null;
    boolean isAdmin =
        authentication != null
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    PageResponse<PostListResponse> posts =
        postService.listPostsOfUser(viewerId, isAdmin, userId, request);
    return ResponseEntity.ok(ApiResponse.success(posts, "Posts retrieved successfully"));
  }
}
