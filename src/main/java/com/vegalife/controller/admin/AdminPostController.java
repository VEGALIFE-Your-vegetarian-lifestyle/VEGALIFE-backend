package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.PostApprovalRequest;
import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.request.admin.PostRejectionRequest;
import com.vegalife.dto.response.admin.AdminPostDetailResponse;
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

/**
 * Admin moderation of posts (ADR-016).
 *
 * <p>{@code /moderate} is gone. It took a publish/unpublish action, which conflated two questions —
 * "is this post acceptable?" and "is this post visible?" — and could not express the component
 * decisions that now come with them. Approve and reject are separate endpoints because they are not
 * symmetric: approve publishes and approves the components, reject unpublishes and refuses the ones
 * still awaiting review.
 */
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

  @GetMapping("/posts/{postId}")
  public ResponseEntity<ApiResponse<AdminPostDetailResponse>> getPost(@PathVariable UUID postId) {
    AdminPostDetailResponse post = adminPostService.getPostDetail(postId);
    return ResponseEntity.ok(ApiResponse.success(post, "Post retrieved successfully"));
  }

  @PostMapping("/posts/{postId}/approve")
  public ResponseEntity<ApiResponse<AdminPostDetailResponse>> approvePost(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID postId,
      @Valid @RequestBody(required = false) PostApprovalRequest request) {
    AdminPostDetailResponse post = adminPostService.approvePost(adminId, postId, reasonOf(request));
    return ResponseEntity.ok(ApiResponse.success(post, "Post approved successfully"));
  }

  @PostMapping("/posts/{postId}/reject")
  public ResponseEntity<ApiResponse<AdminPostDetailResponse>> rejectPost(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID postId,
      @Valid @RequestBody(required = false) PostRejectionRequest request) {
    AdminPostDetailResponse post = adminPostService.rejectPost(adminId, postId, reasonOf(request));
    return ResponseEntity.ok(ApiResponse.success(post, "Post rejected successfully"));
  }

  /** The reason is optional, so an absent body must not be a 400. */
  private static String reasonOf(PostApprovalRequest request) {
    return request == null ? null : request.getReason();
  }

  private static String reasonOf(PostRejectionRequest request) {
    return request == null ? null : request.getReason();
  }
}
