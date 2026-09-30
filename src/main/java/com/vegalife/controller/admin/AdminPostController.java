package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.service.admin.AdminPostService;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPostController {

  private final AdminPostService adminPostService;

  @GetMapping("/posts")
  public ResponseEntity<ApiResponse<PageResponse<AdminPostListResponse>>> listPosts(
      @Valid @ModelAttribute PostListRequest request) {
    PageResponse<AdminPostListResponse> page = adminPostService.listPosts(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Posts retrieved successfully"));
  }
}
