package com.vegalife.dto.request.post;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

/** Filters for the public post feed ({@code GET /api/posts}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostFeedRequest {

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  /** Case-insensitive whole-phrase match on the title or the plain-text content. */
  private String keyword;

  private UUID categoryId;

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
}
