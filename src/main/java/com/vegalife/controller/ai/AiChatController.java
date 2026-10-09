package com.vegalife.controller.ai;

import com.vegalife.dto.request.ai.SendMessageRequest;
import com.vegalife.dto.response.ai.ConversationDetailResponse;
import com.vegalife.dto.response.ai.ConversationSummaryResponse;
import com.vegalife.dto.response.ai.SendMessageResponse;
import com.vegalife.service.ai.AiChatService;
import com.vegalife.service.ai.AiConversationService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AiChatController {

  private final AiChatService aiChatService;
  private final AiConversationService aiConversationService;

  @PostMapping(path = "/messages")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<SendMessageResponse>> sendMessage(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody SendMessageRequest request) {

    SendMessageResponse response = aiChatService.send(userId, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Message sent successfully"));
  }

  @PostMapping(path = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public SseEmitter streamMessage(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody SendMessageRequest request) {

    return aiChatService.stream(userId, request);
  }

  @GetMapping(path = "/conversations")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<List<ConversationSummaryResponse>>> listConversations(
      @AuthenticationPrincipal UUID userId) {

    List<ConversationSummaryResponse> conversations =
        aiConversationService.listConversations(userId);
    return ResponseEntity.ok(ApiResponse.success(conversations, "Conversations retrieved"));
  }

  @GetMapping(path = "/conversations/{id}")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<ConversationDetailResponse>> getConversation(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {

    ConversationDetailResponse conversation = aiConversationService.getConversation(userId, id);
    return ResponseEntity.ok(ApiResponse.success(conversation, "Conversation retrieved"));
  }
}
