package com.vegalife.controller.admin;

import com.vegalife.dto.request.category.CategoryCreateRequest;
import com.vegalife.dto.request.category.CategoryUpdateRequest;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.service.category.CategoryService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminCategoryController {

  private final CategoryService categoryService;

  @PostMapping
  public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
      @Valid @RequestBody CategoryCreateRequest request) {
    CategoryResponse category = categoryService.createCategory(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(category, "Category created successfully"));
  }

  @PatchMapping("/{categoryId}")
  public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
      @PathVariable UUID categoryId, @Valid @RequestBody CategoryUpdateRequest request) {
    CategoryResponse category = categoryService.updateCategory(categoryId, request);
    return ResponseEntity.ok(ApiResponse.success(category, "Category updated successfully"));
  }

  @DeleteMapping("/{categoryId}")
  public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID categoryId) {
    categoryService.deleteCategory(categoryId);
    return ResponseEntity.ok(ApiResponse.success(null, "Category deleted successfully"));
  }
}
