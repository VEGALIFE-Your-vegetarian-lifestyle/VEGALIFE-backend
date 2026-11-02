package com.vegalife.dto.response.admin;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Non-deleted post attached to a video in the admin list; empty list when not attached. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVideoPostResponse {

  private UUID id;
  private String title;
  private String status;
}
