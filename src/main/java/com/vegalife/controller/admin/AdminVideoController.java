package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.VideoListRequest;
import com.vegalife.dto.response.admin.AdminVideoListResponse;
import com.vegalife.service.admin.AdminVideoService;
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
public class AdminVideoController {

  private final AdminVideoService adminVideoService;

  @GetMapping("/videos")
  public ResponseEntity<ApiResponse<PageResponse<AdminVideoListResponse>>> listVideos(
      @Valid @ModelAttribute VideoListRequest request) {
    PageResponse<AdminVideoListResponse> page = adminVideoService.listVideos(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Videos retrieved successfully"));
  }
}
