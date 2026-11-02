package com.vegalife.dto.request.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

  @NotBlank(message = "q is required")
  private String q;

  /** post/video select from the post table; video is a post with video/* media attached. */
  @Pattern(regexp = "^(post|video|recipe)?$", message = "type must be one of: post, video, recipe")
  private String type;

  /** Narrows posts/videos; never excludes recipes (a recipe has no category of its own). */
  private UUID categoryId;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant dateFrom;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant dateTo;

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }
}
