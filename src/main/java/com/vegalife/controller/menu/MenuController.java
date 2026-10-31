package com.vegalife.controller.menu;

import com.vegalife.dto.request.menu.CreateMenuRequest;
import com.vegalife.dto.request.menu.MenuMealPlanQueryRequest;
import com.vegalife.dto.request.menu.MenuQueryRequest;
import com.vegalife.dto.request.menu.UpdateMenuRequest;
import com.vegalife.dto.request.menu.UpdateMenuStatusRequest;
import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.dto.response.menu.MenuMealPlanResponse;
import com.vegalife.dto.response.menu.MenuSummaryResponse;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.service.menu.MenuService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

  private final MenuService menuService;
  private final MenuQueryFilterResolver filterResolver;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<PageResponse<MenuSummaryResponse>>> listMenus(
      @AuthenticationPrincipal UUID userId, @Valid @ModelAttribute MenuQueryRequest request) {
    MenuQueryFilter filter =
        filterResolver.resolve(request.getFrom(), request.getTo(), request.getStatus());
    PageResponse<MenuSummaryResponse> menus =
        menuService.listMenus(
            userId, filter, request.getPage(), request.getSize(), request.getSort());
    return ResponseEntity.ok(ApiResponse.success(menus, "Menus retrieved successfully"));
  }

  /**
   * BR-MENU-001 / BR-MENU-002: the caller's meal plan over the requested range, each menu carrying
   * its days, meals and dishes. With no bounds this is the current ISO week; with a {@code menuId}
   * it is that one menu's meals, regardless of any range alongside it. This replaces the removed
   * {@code GET /api/menus/{menuId}}.
   */
  @GetMapping("/details")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<List<MenuMealPlanResponse>>> getMealPlans(
      @AuthenticationPrincipal UUID userId,
      @Valid @ModelAttribute MenuMealPlanQueryRequest request) {
    MenuQueryFilter filter = filterResolver.resolveMealPlan(request.getFrom(), request.getTo());
    List<MenuMealPlanResponse> plans =
        menuService.listMealPlans(userId, filter, request.getMenuId());
    return ResponseEntity.ok(ApiResponse.success(plans, "Meal plan retrieved successfully"));
  }

  /** BR-MENU-005: creates a drafted weekly menu owned by the authenticated caller. */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<MenuDetailResponse>> createMenu(
      @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateMenuRequest request) {
    MenuDetailResponse created = menuService.createMenu(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Menu created successfully"));
  }

  /** BR-MENU-006: updates an owned drafted or scheduled menu; cancelled/completed return 409. */
  @PatchMapping("/{menuId}")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<MenuDetailResponse>> updateMenu(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID menuId,
      @Valid @RequestBody UpdateMenuRequest request) {
    MenuDetailResponse updated = menuService.updateMenu(userId, menuId, request);
    return ResponseEntity.ok(ApiResponse.success(updated, "Menu updated successfully"));
  }

  /**
   * BR-MENU-007: transitions an owned menu through the legal table; scheduling enforces the
   * no-overlap rule (BR-MENU-003). Illegal transitions and overlaps return 409.
   */
  @PostMapping("/{menuId}/status")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
  public ResponseEntity<ApiResponse<MenuDetailResponse>> changeMenuStatus(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID menuId,
      @Valid @RequestBody UpdateMenuStatusRequest request) {
    MenuDetailResponse updated = menuService.changeStatus(userId, menuId, request.getStatus());
    return ResponseEntity.ok(ApiResponse.success(updated, "Menu status updated successfully"));
  }
}
