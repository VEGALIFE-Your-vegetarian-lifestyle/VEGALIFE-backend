package com.vegalife.unit.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.admin.AdminRecipeController;
import com.vegalife.dto.request.admin.RecipeListRequest;
import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.service.admin.AdminRecipeService;
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
class AdminRecipeControllerTest {

  @Mock private AdminRecipeService adminRecipeService;

  @InjectMocks private AdminRecipeController adminRecipeController;

  private RecipeListRequest request;
  private PageResponse<AdminRecipeListResponse> pageResponse;

  @BeforeEach
  void setUp() {
    request = RecipeListRequest.builder().size(10).build();
    AdminRecipeListResponse item =
        AdminRecipeListResponse.builder()
            .id(UUID.randomUUID())
            .name("Vegan chili")
            .difficulty("MEDIUM")
            .status("ACTIVE")
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .userId(UUID.randomUUID())
            .username("janedoe")
            .email("jane@example.com")
            .build();
    pageResponse =
        PageResponse.<AdminRecipeListResponse>builder()
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
  void listRecipes_shouldReturnSuccessEnvelope() {
    when(adminRecipeService.listRecipes(request)).thenReturn(pageResponse);

    ResponseEntity<ApiResponse<PageResponse<AdminRecipeListResponse>>> response =
        adminRecipeController.listRecipes(request);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().isSuccess()).isTrue();
    assertThat(response.getBody().getMessage()).isEqualTo("Recipes retrieved successfully");
    assertThat(response.getBody().getData()).isEqualTo(pageResponse);

    ArgumentCaptor<RecipeListRequest> captor = ArgumentCaptor.forClass(RecipeListRequest.class);
    verify(adminRecipeService).listRecipes(captor.capture());
    assertThat(captor.getValue().getSize()).isEqualTo(10);
  }
}
