package com.vegalife.dto.response.admin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingGroupResponse {

  private String category;
  private List<SettingItemResponse> items;
}
