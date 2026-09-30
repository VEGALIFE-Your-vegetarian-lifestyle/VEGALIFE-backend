package com.vegalife.unit.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.controller.admin.AdminCategoryController;
import com.vegalife.dto.request.category.CategoryCreateRequest;
import com.vegalife.dto.request.category.CategoryUpdateRequest;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.service.category.CategoryService;
import com.vegalife.shared.dto.ApiResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminCategoryControllerTest {

  @Mock private CategoryService categoryService;

  @InjectMocks private AdminCategoryController adminCategoryController;

  @Test
  void createCategoryReturns201WithMappedBody() {
    CategoryCreateRequest request = CategoryCreateRequest.builder().name("Vegan").build();
    CategoryResponse response =
        CategoryResponse.builder().id(UUID.randomUUID()).name("Vegan").build();
    when(categoryService.createCategory(request)).thenReturn(response);

    ResponseEntity<ApiResponse<CategoryResponse>> result =
        adminCategoryController.createCategory(request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getBody().isSuccess()).isTrue();
    assertThat(result.getBody().getMessage()).isEqualTo("Category created successfully");
    assertThat(result.getBody().getData()).isEqualTo(response);
    verify(categoryService).createCategory(request);
  }

  @Test
  void updateCategoryReturns200WithMappedBody() {
    UUID categoryId = UUID.randomUUID();
    CategoryUpdateRequest request = CategoryUpdateRequest.builder().name("Dessert").build();
    CategoryResponse response = CategoryResponse.builder().id(categoryId).name("Dessert").build();
    when(categoryService.updateCategory(categoryId, request)).thenReturn(response);

    ResponseEntity<ApiResponse<CategoryResponse>> result =
        adminCategoryController.updateCategory(categoryId, request);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().isSuccess()).isTrue();
    assertThat(result.getBody().getMessage()).isEqualTo("Category updated successfully");
    assertThat(result.getBody().getData()).isEqualTo(response);
    verify(categoryService).updateCategory(categoryId, request);
  }
}
