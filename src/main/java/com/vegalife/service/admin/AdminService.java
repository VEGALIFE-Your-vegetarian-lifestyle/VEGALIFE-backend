package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminCommentMapper;
import com.vegalife.dto.mapper.admin.AdminUserMapper;
import com.vegalife.dto.request.admin.CommentListRequest;
import com.vegalife.dto.request.admin.UserListRequest;
import com.vegalife.dto.response.admin.CommentListResponse;
import com.vegalife.dto.response.admin.UserListResponse;
import com.vegalife.model.post.Comment;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.CommentSpecifications;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.repository.user.UserSpecifications;
import com.vegalife.service.token.JwtTokenService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final JwtTokenService jwtTokenService;
  private final CommentRepository commentRepository;
  private final AdminCommentMapper adminCommentMapper;

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

  @Transactional(readOnly = true)
  public PageResponse<CommentListResponse> listComments(CommentListRequest request) {
    String status = parseCommentStatus(request.getStatus());
    UUID userId = request.getUserId();
    UUID postId = request.getPostId();
    Instant createdFrom = request.getCreatedFrom();
    Instant createdTo = request.getCreatedTo();

    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseCommentSort(request.getSort()));
    Page<Comment> page =
        commentRepository.findAll(
            CommentSpecifications.withFilters(status, userId, postId, createdFrom, createdTo),
            pageable);

    Set<UUID> authorIds =
        page.getContent().stream().map(Comment::getUserId).collect(Collectors.toSet());
    Map<UUID, String> usernames =
        userRepository.findAllById(authorIds).stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));

    Page<CommentListResponse> mapped =
        page.map(
            comment -> {
              CommentListResponse response = adminCommentMapper.toResponse(comment);
              response.setUsername(usernames.get(comment.getUserId()));
              return response;
            });

    log.debug(
        "Listed comments page={} size={} total={}",
        mapped.getNumber(),
        mapped.getSize(),
        mapped.getTotalElements());

    return PageResponse.from(mapped);
  }

  @Transactional
  public UserListResponse suspendUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.getStatus() == User.Status.suspended) {
      throw new DuplicateResourceException("User is already suspended");
    }

    user.setStatus(User.Status.suspended);
    userRepository.save(user);

    int revoked = jwtTokenService.revokeAllUserRefreshTokens(userId);
    log.info("User {} suspended; revoked {} refresh tokens", userId, revoked);

    return adminUserMapper.toResponse(user);
  }

  @Transactional
  public UserListResponse restoreUser(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .filter(u -> u.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    if (user.getStatus() != User.Status.suspended) {
      throw new DuplicateResourceException("User is not suspended");
    }

    user.setStatus(User.Status.activated);
    userRepository.save(user);

    log.info("User {} restored; status set to activated", userId);

    return adminUserMapper.toResponse(user);
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

  private String parseCommentStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    if ("active".equals(status) || "removed".equals(status)) {
      return status;
    }
    throw new ValidationException("Status must be one of: active, removed");
  }

  private Sort parseCommentSort(String sort) {
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2) {
      throw new ValidationException("Sort must be in the form property,asc|desc");
    }
    String property = parts[0].trim();
    if (!"createdAt".equals(property) && !"updatedAt".equals(property)) {
      throw new ValidationException("Sort property must be one of: createdAt, updatedAt");
    }
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("asc")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(direction, property);
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
