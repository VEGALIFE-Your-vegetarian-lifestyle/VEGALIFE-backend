package com.vegalife.controller.post;

import com.vegalife.dto.request.category.CategoryListRequest;
import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.service.category.CategoryService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> listCategories(
      @Valid @ModelAttribute CategoryListRequest request) {
    PageResponse<CategoryResponse> categories = categoryService.listCategories(request);
    return ResponseEntity.ok(ApiResponse.success(categories, "Categories retrieved successfully"));
  }
}
