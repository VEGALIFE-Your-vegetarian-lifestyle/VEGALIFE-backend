package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.AdminDishIngredientCreateRequest;
import com.vegalife.dto.request.admin.AdminDishIngredientUpdateRequest;
import com.vegalife.dto.response.admin.AdminDishIngredientResponse;
import com.vegalife.service.admin.AdminDishIngredientService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin curation of a dish's ingredient list (BR-DISH-002).
 *
 * <p>Nested under the dish because a {@code dish_ingredient} row has no meaning outside its dish,
 * and the path already identifies the parent the caller is curating.
 */
@RestController
@RequestMapping("/api/admin/dishes/{dishId}/ingredients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
public class AdminDishIngredientController {

  private final AdminDishIngredientService adminDishIngredientService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminDishIngredientResponse>>> listDishIngredients(
      @PathVariable UUID dishId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            adminDishIngredientService.listDishIngredients(dishId),
            "Dish ingredients retrieved successfully"));
  }

  /** 201 for a new entry, 200 when a previously withdrawn entry is reinstated. */
  @PostMapping
  public ResponseEntity<ApiResponse<AdminDishIngredientResponse>> addDishIngredient(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID dishId,
      @Valid @RequestBody AdminDishIngredientCreateRequest request) {
    AdminDishIngredientService.AddResult result =
        adminDishIngredientService.addDishIngredient(adminId, dishId, request);

    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    String message =
        result.created()
            ? "Dish ingredient added successfully"
            : "Dish ingredient restored successfully";

    return ResponseEntity.status(status).body(ApiResponse.success(result.response(), message));
  }

  @PatchMapping("/{ingredientId}")
  public ResponseEntity<ApiResponse<AdminDishIngredientResponse>> updateDishIngredient(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID dishId,
      @PathVariable UUID ingredientId,
      @Valid @RequestBody AdminDishIngredientUpdateRequest request) {
    AdminDishIngredientResponse entry =
        adminDishIngredientService.updateDishIngredient(adminId, dishId, ingredientId, request);
    return ResponseEntity.ok(ApiResponse.success(entry, "Dish ingredient updated successfully"));
  }

  @DeleteMapping("/{ingredientId}")
  public ResponseEntity<ApiResponse<Void>> removeDishIngredient(
      @AuthenticationPrincipal UUID adminId,
      @PathVariable UUID dishId,
      @PathVariable UUID ingredientId) {
    adminDishIngredientService.removeDishIngredient(adminId, dishId, ingredientId);
    return ResponseEntity.ok(ApiResponse.success(null, "Dish ingredient removed successfully"));
  }
}
