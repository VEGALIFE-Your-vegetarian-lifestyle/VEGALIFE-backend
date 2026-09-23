package com.vegalife.unit.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.admin.AdminController;
import com.vegalife.dto.request.admin.UserListRequest;
import com.vegalife.dto.response.admin.UserListResponse;
import com.vegalife.service.admin.AdminService;
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
class AdminControllerTest {

  @Mock private AdminService adminService;

  @InjectMocks private AdminController adminController;

  private UserListRequest request;
  private PageResponse<UserListResponse> pageResponse;

  @BeforeEach
  void setUp() {
    request = UserListRequest.builder().status("activated").role("USER").build();
    UserListResponse item =
        UserListResponse.builder()
            .id(UUID.randomUUID())
            .email("jane@example.com")
            .username("jane")
            .role("USER")
            .status("activated")
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .build();
    pageResponse =
        PageResponse.<UserListResponse>builder()
            .content(List.of(item))
            .page(0)
            .size(20)
            .totalElements(1)
            .totalPages(1)
            .first(true)
            .last(true)
            .build();
  }

  @Test
  void listUsers_shouldReturnSuccessEnvelope() {
    when(adminService.listUsers(request)).thenReturn(pageResponse);

    ResponseEntity<ApiResponse<PageResponse<UserListResponse>>> response =
        adminController.listUsers(request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    assertThat(response.getBody().getMessage()).isEqualTo("Users retrieved successfully");
    assertThat(response.getBody().getData()).isEqualTo(pageResponse);

    ArgumentCaptor<UserListRequest> captor = ArgumentCaptor.forClass(UserListRequest.class);
    verify(adminService).listUsers(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("activated");
    assertThat(captor.getValue().getRole()).isEqualTo("USER");
  }
}
