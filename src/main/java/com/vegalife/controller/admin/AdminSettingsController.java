package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.SettingsUpdateRequest;
import com.vegalife.dto.response.admin.SettingGroupResponse;
import com.vegalife.service.admin.AdminSettingsService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-configurable platform settings (issue #13). */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH)
public class AdminSettingsController {

  private final AdminSettingsService adminSettingsService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<SettingGroupResponse>>> getSettings() {
    List<SettingGroupResponse> settings = adminSettingsService.getSettings();
    return ResponseEntity.ok(ApiResponse.success(settings, "Settings retrieved successfully"));
  }

  @PatchMapping
  public ResponseEntity<ApiResponse<List<SettingGroupResponse>>> updateSettings(
      @AuthenticationPrincipal UUID adminId, @Valid @RequestBody SettingsUpdateRequest request) {
    List<SettingGroupResponse> settings = adminSettingsService.updateSettings(adminId, request);
    return ResponseEntity.ok(ApiResponse.success(settings, "Settings updated successfully"));
  }
}
