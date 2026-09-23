package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.admin.AdminUserMapper;
import com.vegalife.dto.request.admin.UserListRequest;
import com.vegalife.dto.response.admin.UserListResponse;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.admin.AdminService;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private AdminUserMapper adminUserMapper;

  @Mock private JwtTokenService jwtTokenService;

  @InjectMocks private AdminService adminService;

  private User user;
  private UserListResponse userResponse;
  private Pageable expectedPageable;

  @BeforeEach
  void setUp() {
    user =
        User.builder()
            .id(UUID.randomUUID())
            .username("jane")
            .email("jane@example.com")
            .passwordHash("$2a$10$secret")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    userResponse =
        UserListResponse.builder()
            .id(user.getId())
            .username("jane")
            .email("jane@example.com")
            .role("USER")
            .status("activated")
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .build();
    expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void listUsers_withDefaults_usesDefaultPaginationAndSort() {
    Page<User> page = new PageImpl<>(List.of(user), expectedPageable, 1);
    when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminUserMapper.toResponse(user)).thenReturn(userResponse);

    PageResponse<UserListResponse> result = adminService.listUsers(new UserListRequest());

    assertThat(result.getContent()).containsExactly(userResponse);
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(20);
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();

    verify(userRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listUsers_withFilters_parsesEnumsAndDateRange() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-12-31T23:59:59Z");
    UserListRequest request =
        UserListRequest.builder()
            .page(1)
            .size(10)
            .sort("createdAt,asc")
            .status("activated")
            .role("USER")
            .createdFrom(from)
            .createdTo(to)
            .build();
    Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.ASC, "createdAt"));
    Page<User> page = new PageImpl<>(List.of(user), pageable, 1);
    when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(adminUserMapper.toResponse(user)).thenReturn(userResponse);

    PageResponse<UserListResponse> result = adminService.listUsers(request);

    assertThat(result.getContent()).containsExactly(userResponse);
    assertThat(result.getPage()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(10);
    verify(userRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  void listUsers_whenCreatedFromAfterCreatedTo_throwsValidationException() {
    UserListRequest request =
        UserListRequest.builder()
            .createdFrom(Instant.parse("2026-12-01T00:00:00Z"))
            .createdTo(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    assertThatThrownBy(() -> adminService.listUsers(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("createdFrom must be before createdTo");
  }

  @Test
  void listUsers_withInvalidStatus_throwsValidationException() {
    UserListRequest request = UserListRequest.builder().status("bogus").build();

    assertThatThrownBy(() -> adminService.listUsers(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Status must be one of: created, activated, deactivated, suspended");
  }

  @Test
  void listUsers_withInvalidRole_throwsValidationException() {
    UserListRequest request = UserListRequest.builder().role("SUPERADMIN").build();

    assertThatThrownBy(() -> adminService.listUsers(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Role must be ADMIN or USER");
  }

  @Test
  void listUsers_withMalformedSort_throwsValidationException() {
    UserListRequest request = UserListRequest.builder().sort("createdAt,asc,extra").build();

    assertThatThrownBy(() -> adminService.listUsers(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Sort must be in the form property,asc|desc");
  }

  @Test
  void listUsers_withEmptyPage_returnsEmptyContent() {
    Page<User> page = Page.empty(expectedPageable);
    when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    PageResponse<UserListResponse> result = adminService.listUsers(new UserListRequest());

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();
  }

  @Test
  void suspendUser_success_setsStatusAndRevokesRefreshTokens() {
    when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);
    when(adminUserMapper.toResponse(user)).thenReturn(userResponse);
    when(jwtTokenService.revokeAllUserRefreshTokens(user.getId())).thenReturn(2);

    UserListResponse result = adminService.suspendUser(user.getId());

    assertThat(result).isEqualTo(userResponse);
    assertThat(user.getStatus()).isEqualTo(User.Status.suspended);
    verify(userRepository).save(user);
    verify(jwtTokenService).revokeAllUserRefreshTokens(user.getId());
  }

  @Test
  void suspendUser_missingUser_throwsResourceNotFound() {
    UUID missingId = UUID.randomUUID();
    when(userRepository.findById(missingId)).thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> adminService.suspendUser(missingId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verify(jwtTokenService, never()).revokeAllUserRefreshTokens(any());
  }

  @Test
  void suspendUser_softDeletedUser_throwsResourceNotFound() {
    user.setDeletedAt(Instant.now());
    when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));

    assertThatThrownBy(() -> adminService.suspendUser(user.getId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verify(jwtTokenService, never()).revokeAllUserRefreshTokens(any());
  }

  @Test
  void suspendUser_alreadySuspended_throwsDuplicateResource() {
    user.setStatus(User.Status.suspended);
    when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));

    assertThatThrownBy(() -> adminService.suspendUser(user.getId()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("User is already suspended");

    verify(userRepository, never()).save(any());
    verify(jwtTokenService, never()).revokeAllUserRefreshTokens(any());
  }

  @Test
  void restoreUser_success_setsStatusToActivated() {
    user.setStatus(User.Status.suspended);
    UserListResponse activatedResponse =
        UserListResponse.builder()
            .id(user.getId())
            .username("jane")
            .email("jane@example.com")
            .role("USER")
            .status("activated")
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .build();
    when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);
    when(adminUserMapper.toResponse(user)).thenReturn(activatedResponse);

    UserListResponse result = adminService.restoreUser(user.getId());

    assertThat(result).isEqualTo(activatedResponse);
    assertThat(user.getStatus()).isEqualTo(User.Status.activated);
    verify(userRepository).save(user);
  }

  @Test
  void restoreUser_missingUser_throwsResourceNotFound() {
    UUID missingId = UUID.randomUUID();
    when(userRepository.findById(missingId)).thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> adminService.restoreUser(missingId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verify(userRepository, never()).save(any());
  }

  @Test
  void restoreUser_softDeletedUser_throwsResourceNotFound() {
    user.setStatus(User.Status.suspended);
    user.setDeletedAt(Instant.now());
    when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));

    assertThatThrownBy(() -> adminService.restoreUser(user.getId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verify(userRepository, never()).save(any());
  }

  @Test
  void restoreUser_notSuspended_throwsDuplicateResource() {
    user.setStatus(User.Status.activated);
    when(userRepository.findById(user.getId())).thenReturn(java.util.Optional.of(user));

    assertThatThrownBy(() -> adminService.restoreUser(user.getId()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("User is not suspended");

    verify(userRepository, never()).save(any());
  }
}
