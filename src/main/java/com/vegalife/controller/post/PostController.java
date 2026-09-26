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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

  private final PostService postService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> listUserPosts(
      @AuthenticationPrincipal UUID userId, @Valid @ModelAttribute PostListRequest request) {
    PageResponse<PostListResponse> posts = postService.listUserPosts(userId, request);
    return ResponseEntity.ok(ApiResponse.success(posts, "Posts retrieved successfully"));
  }
}
