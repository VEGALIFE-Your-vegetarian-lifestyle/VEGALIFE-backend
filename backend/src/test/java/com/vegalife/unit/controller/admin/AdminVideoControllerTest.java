package com.vegalife.unit.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.admin.AdminVideoController;
import com.vegalife.dto.request.admin.VideoListRequest;
import com.vegalife.dto.response.admin.AdminVideoListResponse;
import com.vegalife.service.admin.AdminVideoService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminVideoControllerTest {

  @Mock private AdminVideoService adminVideoService;

  @InjectMocks private AdminVideoController adminVideoController;

  private VideoListRequest request;
  private PageResponse<AdminVideoListResponse> pageResponse;

  @BeforeEach
  void setUp() {
    request = VideoListRequest.builder().status("succeed").size(10).build();
    AdminVideoListResponse item =
        AdminVideoListResponse.builder()
            .id(UUID.randomUUID())
            .mediaUrl("https://cdn.example.com/cooking.mp4")
            .status("succeed")
            .mimeType("video/mp4")
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .userId(UUID.randomUUID())
            .username("janedoe")
            .email("jane@example.com")
            .posts(List.of())
            .build();
    pageResponse =
        PageResponse.<AdminVideoListResponse>builder()
            .content(List.of(item))
            .page(0)
            .size(10)
            .totalElements(1)
            .totalPages(1)
            .first(true)
            .last(true)
            .build();
  }

  @Test
  void listVideos_shouldReturnSuccessEnvelope() {
    when(adminVideoService.listVideos(request)).thenReturn(pageResponse);

    ResponseEntity<ApiResponse<PageResponse<AdminVideoListResponse>>> response =
        adminVideoController.listVideos(request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    assertThat(response.getBody().getMessage()).isEqualTo("Videos retrieved successfully");
    assertThat(response.getBody().getData()).isEqualTo(pageResponse);

    ArgumentCaptor<VideoListRequest> captor = ArgumentCaptor.forClass(VideoListRequest.class);
    verify(adminVideoService).listVideos(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("succeed");
    assertThat(captor.getValue().getSize()).isEqualTo(10);
  }
}
