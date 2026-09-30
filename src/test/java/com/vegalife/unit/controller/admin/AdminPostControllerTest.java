package com.vegalife.unit.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.admin.AdminPostController;
import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.service.admin.AdminPostService;
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
class AdminPostControllerTest {

  @Mock private AdminPostService adminPostService;

  @InjectMocks private AdminPostController adminPostController;

  private PostListRequest request;
  private PageResponse<AdminPostListResponse> pageResponse;

  @BeforeEach
  void setUp() {
    request = PostListRequest.builder().status("published").size(10).build();
    AdminPostListResponse item =
        AdminPostListResponse.builder()
            .id(UUID.randomUUID())
            .title("Vegan chili")
            .type("blog")
            .status("published")
            .flag("PASSED")
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .userId(UUID.randomUUID())
            .username("janedoe")
            .email("jane@example.com")
            .build();
    pageResponse =
        PageResponse.<AdminPostListResponse>builder()
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
  void listPosts_shouldReturnSuccessEnvelope() {
    when(adminPostService.listPosts(request)).thenReturn(pageResponse);

    ResponseEntity<ApiResponse<PageResponse<AdminPostListResponse>>> response =
        adminPostController.listPosts(request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    assertThat(response.getBody().getMessage()).isEqualTo("Posts retrieved successfully");
    assertThat(response.getBody().getData()).isEqualTo(pageResponse);

    ArgumentCaptor<PostListRequest> captor = ArgumentCaptor.forClass(PostListRequest.class);
    verify(adminPostService).listPosts(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("published");
    assertThat(captor.getValue().getSize()).isEqualTo(10);
  }
}
