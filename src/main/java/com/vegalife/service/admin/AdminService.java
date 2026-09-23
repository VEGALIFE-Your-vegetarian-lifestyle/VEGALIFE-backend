package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminUserMapper;
import com.vegalife.dto.request.admin.UserListRequest;
import com.vegalife.dto.response.admin.UserListResponse;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.repository.user.UserSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

  private final UserRepository userRepository;
  private final AdminUserMapper adminUserMapper;

  @Transactional(readOnly = true)
  public PageResponse<UserListResponse> listUsers(UserListRequest request) {
    User.Status status = parseStatus(request.getStatus());
    User.Role role = parseRole(request.getRole());
    Instant createdFrom = request.getCreatedFrom();
    Instant createdTo = request.getCreatedTo();

    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<User> spec =
        UserSpecifications.activeWithFilters(status, role, createdFrom, createdTo);
    Page<User> page = userRepository.findAll(spec, pageable);

    log.debug(
        "Listed users page={} size={} total={}",
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());

    Page<UserListResponse> mapped = page.map(adminUserMapper::toResponse);
    return PageResponse.from(mapped);
  }

  private User.Status parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return User.Status.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException(
          "Status must be one of: created, activated, deactivated, suspended");
    }
  }

  private User.Role parseRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    try {
      return User.Role.valueOf(role);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("Role must be ADMIN or USER");
    }
  }

  private Sort parseSort(String sort) {
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2) {
      throw new ValidationException("Sort must be in the form property,asc|desc");
    }
    String property = parts[0].trim();
    if (property.isBlank()) {
      throw new ValidationException("Sort property must not be blank");
    }
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("asc")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(direction, property);
  }
}
