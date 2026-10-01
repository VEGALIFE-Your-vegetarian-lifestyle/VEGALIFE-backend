package com.vegalife.unit.controller.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.recipe.DishController;
import com.vegalife.dto.request.recipe.DishListRequest;
import com.vegalife.dto.response.recipe.DishResponse;
import com.vegalife.service.recipe.DishService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
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
class DishControllerTest {

  @Mock private DishService dishService;

  @InjectMocks private DishController dishController;

  @Test
  void listDishesReturns200WithMappedBody() {
    DishListRequest request = DishListRequest.builder().name("pho").build();
    PageResponse<DishResponse> page =
        PageResponse.<DishResponse>builder()
            .content(List.of(DishResponse.builder().id(UUID.randomUUID()).name("pho chay").build()))
            .page(0)
            .size(20)
            .totalElements(1)
            .totalPages(1)
            .first(true)
            .last(true)
            .build();
    when(dishService.listDishes(request)).thenReturn(page);

    ResponseEntity<ApiResponse<PageResponse<DishResponse>>> result =
        dishController.listDishes(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().isSuccess()).isTrue();
    assertThat(result.getBody().getMessage()).isEqualTo("Dishes retrieved successfully");
    assertThat(result.getBody().getData()).isEqualTo(page);
    verify(dishService).listDishes(request);
  }
}
