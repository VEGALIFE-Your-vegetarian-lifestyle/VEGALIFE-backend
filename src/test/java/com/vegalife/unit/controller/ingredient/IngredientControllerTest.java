package com.vegalife.unit.controller.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.ingredient.IngredientController;
import com.vegalife.dto.request.ingredient.IngredientListRequest;
import com.vegalife.dto.response.ingredient.IngredientResponse;
import com.vegalife.service.ingredient.IngredientService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class IngredientControllerTest {

  @Mock private IngredientService ingredientService;

  @InjectMocks private IngredientController ingredientController;

  @Test
  void listIngredientsReturns200WithMappedBody() {
    IngredientListRequest request = IngredientListRequest.builder().name("tof").build();
    PageResponse<IngredientResponse> page =
        PageResponse.<IngredientResponse>builder()
            .content(
                List.of(
                    IngredientResponse.builder()
                        .id(UUID.randomUUID())
                        .name("tofu")
                        .createdAt(Instant.parse("2026-10-01T09:00:00Z"))
                        .build()))
            .page(0)
            .size(20)
            .totalElements(1)
            .totalPages(1)
            .first(true)
            .last(true)
            .build();
    when(ingredientService.listIngredients(request)).thenReturn(page);

    ResponseEntity<ApiResponse<PageResponse<IngredientResponse>>> result =
        ingredientController.listIngredients(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().isSuccess()).isTrue();
    assertThat(result.getBody().getMessage()).isEqualTo("Ingredients retrieved successfully");
    assertThat(result.getBody().getData()).isEqualTo(page);
    verify(ingredientService).listIngredients(request);
  }

  @Test
  void ingredientResponseDeclaresOnlyIdNameAndCreatedAt() {
    List<String> fields =
        java.util.Arrays.stream(IngredientResponse.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName)
            .toList();

    assertThat(fields).containsExactlyInAnyOrder("id", "name", "createdAt");
  }
}
