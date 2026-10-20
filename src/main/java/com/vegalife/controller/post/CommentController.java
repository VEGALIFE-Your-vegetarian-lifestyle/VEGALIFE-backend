package com.vegalife.controller.post;

import com.vegalife.dto.request.post.CommentCreateRequest;
import com.vegalife.dto.request.post.CommentListRequest;
import com.vegalife.dto.request.post.CommentUpdateRequest;
import com.vegalife.dto.response.post.CommentResponse;
import com.vegalife.dto.response.post.CommentThreadResponse;
import com.vegalife.service.post.CommentService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

  private final CommentService commentService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<CommentThreadResponse>>> listComments(
      @PathVariable UUID postId, @Valid @ModelAttribute CommentListRequest request) {
    PageResponse<CommentThreadResponse> comments = commentService.getComments(postId, request);
    return ResponseEntity.ok(ApiResponse.success(comments, "Comments retrieved successfully"));
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<CommentResponse>> createComment(
      Authentication authentication,
      @PathVariable UUID postId,
      @Valid @RequestBody CommentCreateRequest request) {
    UUID userId = (UUID) authentication.getPrincipal();
    CommentResponse comment = commentService.createComment(userId, postId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(comment, "Comment created successfully"));
  }

  @PatchMapping("/{commentId}")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
      Authentication authentication,
      @PathVariable UUID postId,
      @PathVariable UUID commentId,
      @Valid @RequestBody CommentUpdateRequest request) {
    UUID userId = (UUID) authentication.getPrincipal();
    CommentResponse comment = commentService.updateComment(userId, postId, commentId, request);
    return ResponseEntity.ok(ApiResponse.success(comment, "Comment updated successfully"));
  }

  @DeleteMapping("/{commentId}")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<Void>> deleteComment(
      Authentication authentication, @PathVariable UUID postId, @PathVariable UUID commentId) {
    UUID userId = (UUID) authentication.getPrincipal();
    commentService.deleteComment(userId, isAdmin(authentication), postId, commentId);
    return ResponseEntity.ok(ApiResponse.success(null, "Comment deleted successfully"));
  }

  @PostMapping("/{commentId}/report")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<Void>> reportComment(
      @PathVariable UUID postId, @PathVariable UUID commentId) {
    commentService.reportComment(postId, commentId);
    return ResponseEntity.ok(ApiResponse.success(null, "Comment reported successfully"));
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
