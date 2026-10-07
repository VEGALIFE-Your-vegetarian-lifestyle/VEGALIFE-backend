package com.vegalife.controller.ai;

import com.vegalife.dto.request.ai.SendMessageRequest;
import com.vegalife.dto.response.ai.SendMessageResponse;
import com.vegalife.service.ai.AiChatService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai/messages")
@RequiredArgsConstructor
public class AiChatController {

  private final AiChatService aiChatService;

  @PostMapping
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<SendMessageResponse>> sendMessage(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody SendMessageRequest request) {

    SendMessageResponse response = aiChatService.send(userId, request);
    return ResponseEntity.ok(ApiResponse.success(response, "Message sent successfully"));
  }

  @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public SseEmitter streamMessage(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody SendMessageRequest request) {

    return aiChatService.stream(userId, request);
  }
}
