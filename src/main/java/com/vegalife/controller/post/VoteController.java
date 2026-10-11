package com.vegalife.controller.post;

import com.vegalife.dto.request.post.VoteRequest;
import com.vegalife.dto.response.post.VoteResponse;
import com.vegalife.service.post.VoteService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/votes")
@RequiredArgsConstructor
public class VoteController {

  private final VoteService voteService;

  @GetMapping
  public ResponseEntity<ApiResponse<VoteResponse>> getVotes(
      Authentication authentication, @PathVariable UUID postId) {
    VoteResponse votes = voteService.getVotes(postId, viewerIdOrNull(authentication));
    return ResponseEntity.ok(ApiResponse.success(votes, "Votes retrieved successfully"));
  }

  @PutMapping
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<VoteResponse>> castVote(
      Authentication authentication,
      @PathVariable UUID postId,
      @Valid @RequestBody VoteRequest request) {
    UUID userId = (UUID) authentication.getPrincipal();
    VoteResponse votes = voteService.castVote(userId, postId, request.getVoteType());
    return ResponseEntity.ok(ApiResponse.success(votes, "Vote recorded successfully"));
  }

  private UUID viewerIdOrNull(Authentication authentication) {
    boolean anonymous =
        authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken;
    return anonymous ? null : (UUID) authentication.getPrincipal();
  }
}
