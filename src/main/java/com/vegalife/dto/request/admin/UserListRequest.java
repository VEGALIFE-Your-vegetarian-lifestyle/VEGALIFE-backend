package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListRequest {

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  private String sort;

  @Pattern(
      regexp = "^(created|activated|deactivated|suspended)$",
      message = "Status must be one of: created, activated, deactivated, suspended")
  private String status;

  @Pattern(regexp = "^(ADMIN|USER)$", message = "Role must be ADMIN or USER")
  private String role;

  /** Case-insensitive substring matched against {@code displayName} or {@code email}. */
  private String keyword;

  /** {@code true} for verified accounts only, {@code false} for unverified only, blank for both. */
  private Boolean emailVerified;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant createdFrom;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant createdTo;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }

  public String getSort() {
    return sort == null || sort.isBlank() ? "createdAt,desc" : sort;
  }
}
