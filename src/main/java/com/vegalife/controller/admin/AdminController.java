package com.vegalife.controller.admin;

import com.vegalife.dto.request.admin.UserListRequest;
import com.vegalife.dto.response.admin.UserListResponse;
import com.vegalife.service.admin.AdminService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminService adminService;

  @GetMapping("/users")
  public ResponseEntity<ApiResponse<PageResponse<UserListResponse>>> listUsers(
      @Valid @ModelAttribute UserListRequest request) {
    PageResponse<UserListResponse> page = adminService.listUsers(request);
    return ResponseEntity.ok(ApiResponse.success(page, "Users retrieved successfully"));
  }

  @PostMapping("/users/{userId}/suspend")
  public ResponseEntity<ApiResponse<UserListResponse>> suspendUser(@PathVariable UUID userId) {
    UserListResponse user = adminService.suspendUser(userId);
    return ResponseEntity.ok(ApiResponse.success(user, "User suspended successfully"));
  }
}
