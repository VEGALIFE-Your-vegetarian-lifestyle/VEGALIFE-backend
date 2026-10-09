package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.RecipeListRequest;
import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.service.admin.AdminRecipeService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminRecipeController {

  private final AdminRecipeService adminRecipeService;

  @GetMapping("/recipes")
  public ResponseEntity<ApiResponse<PageResponse<AdminRecipeListResponse>>> listRecipes(
      @Valid @ModelAttribute RecipeListRequest request) {
    PageResponse<AdminRecipeListResponse> page = adminRecipeService.listRecipes(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Recipes retrieved successfully"));
  }
}
