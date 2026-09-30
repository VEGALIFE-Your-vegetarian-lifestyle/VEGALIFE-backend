package com.vegalife.unit.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.post.CategoryController;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.service.category.CategoryService;
import com.vegalife.shared.dto.ApiResponse;
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
    List<CategoryResponse> categories =
        List.of(CategoryResponse.builder().id(UUID.randomUUID()).name("Vegan").build());
    when(categoryService.listCategories()).thenReturn(categories);

    ResponseEntity<ApiResponse<List<CategoryResponse>>> result =
        categoryController.listCategories();

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().isSuccess()).isTrue();
    assertThat(result.getBody().getMessage()).isEqualTo("Categories retrieved successfully");
    assertThat(result.getBody().getData()).isEqualTo(categories);
    verify(categoryService).listCategories();
  }
}
