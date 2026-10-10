package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.request.admin.PostModerationRequest;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.service.admin.AdminPostService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminPostController {

  private final AdminPostService adminPostService;

  @GetMapping("/posts")
  public ResponseEntity<ApiResponse<PageResponse<AdminPostListResponse>>> listPosts(
      @Valid @ModelAttribute PostListRequest request) {
    PageResponse<AdminPostListResponse> page = adminPostService.listPosts(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Posts retrieved successfully"));
  }

  @PostMapping("/posts/{postId}/moderate")
  public ResponseEntity<ApiResponse<AdminPostListResponse>> moderatePost(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID postId,
      @Valid @RequestBody PostModerationRequest request) {
    AdminPostListResponse post = adminPostService.moderatePost(adminId, postId, request);
    String message =
        request.isPublish() ? "Post published successfully" : "Post unpublished successfully";
    return ResponseEntity.ok(ApiResponse.success(post, message));
  }
}
