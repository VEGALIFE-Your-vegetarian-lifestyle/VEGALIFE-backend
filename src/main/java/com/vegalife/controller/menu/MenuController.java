package com.vegalife.controller.menu;

import com.vegalife.dto.request.menu.MenuQueryRequest;
import com.vegalife.dto.response.menu.MenuDetailResponse;
import com.vegalife.dto.response.menu.MenuSummaryResponse;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.service.menu.MenuService;
import com.vegalife.shared.config.OpenApiConfig;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<PageResponse<MenuSummaryResponse>>> listMenus(
      @AuthenticationPrincipal UUID userId, @Valid @ModelAttribute MenuQueryRequest request) {
    MenuQueryFilter filter =
        filterResolver.resolve(
            request.getPeriod(),
            request.getDate(),
            request.getMonth(),
            request.getFrom(),
            request.getTo(),
            request.getStatus());
    PageResponse<MenuSummaryResponse> menus =
        menuService.listMenus(
            userId, filter, request.getPage(), request.getSize(), request.getSort());
    return ResponseEntity.ok(ApiResponse.success(menus, "Menus retrieved successfully"));
  }

  @GetMapping("/{menuId}")
  @PreAuthorize("isAuthenticated()")
  @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
  public ResponseEntity<ApiResponse<MenuDetailResponse>> getMenu(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID menuId) {
    MenuDetailResponse menu = menuService.getMenu(userId, menuId);
    return ResponseEntity.ok(ApiResponse.success(menu, "Menu retrieved successfully"));
  }
}
