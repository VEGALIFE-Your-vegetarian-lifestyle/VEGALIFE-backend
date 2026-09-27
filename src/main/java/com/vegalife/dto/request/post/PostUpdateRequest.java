package com.vegalife.dto.request.post;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostUpdateRequest {

  @NotBlank(message = "Title must not be blank")
  @Size(max = 255, message = "Title must not exceed 255 characters")
  private String title;

  @NotBlank(message = "Content must not be blank")
  private String content;

  private String featuredImageUrl;

  @AssertTrue(message = "At least one field must be provided")
  public boolean isUpdateRequestNotEmpty() {
    return title != null || content != null || featuredImageUrl != null;
  }
}
