package com.vegalife.unit.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.post.CategoryController;
import com.vegalife.dto.request.category.CategoryListRequest;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.service.category.CategoryService;
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
class CategoryControllerTest {

  @Mock private CategoryService categoryService;

  @InjectMocks private CategoryController categoryController;

  @Test
  void listCategoriesReturns200WithMappedBody() {
    CategoryListRequest request = CategoryListRequest.builder().name("veg").build();
    PageResponse<CategoryResponse> page =
        PageResponse.<CategoryResponse>builder()
            .content(
                List.of(CategoryResponse.builder().id(UUID.randomUUID()).name("Vegan").build()))
            .page(0)
            .size(20)
            .totalElements(1)
            .totalPages(1)
            .first(true)
            .last(true)
            .build();
    when(categoryService.listCategories(request)).thenReturn(page);

    ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> result =
        categoryController.listCategories(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().isSuccess()).isTrue();
    assertThat(result.getBody().getMessage()).isEqualTo("Categories retrieved successfully");
    assertThat(result.getBody().getData()).isEqualTo(page);
    verify(categoryService).listCategories(request);
  }
}
