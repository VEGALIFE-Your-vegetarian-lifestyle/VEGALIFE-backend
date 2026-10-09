package com.vegalife.controller.post;

import com.vegalife.dto.request.post.CommentCreateRequest;
import com.vegalife.dto.response.post.CommentResponse;
import com.vegalife.service.post.CommentService;
import com.vegalife.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

  @PostMapping
  public ResponseEntity<ApiResponse<CommentResponse>> createComment(
      Authentication authentication,
      @PathVariable UUID postId,
      @Valid @RequestBody CommentCreateRequest request) {
    UUID userId = (UUID) authentication.getPrincipal();
    CommentResponse comment = commentService.createComment(userId, postId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(comment, "Comment created successfully"));
  }
}
