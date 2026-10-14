package com.vegalife.controller.search;

import com.vegalife.dto.request.search.SearchType;
import com.vegalife.dto.response.search.SearchResultResponse;
import com.vegalife.service.search.SearchService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

  private final SearchService searchService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<SearchResultResponse>>> search(
      @RequestParam @NotBlank @Size(max = 100) String q,
      @RequestParam(required = false) SearchType type,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    PageResponse<SearchResultResponse> results =
        searchService.search(q, type, from, to, categoryId, page, size);
    return ResponseEntity.ok(ApiResponse.success(results, "Search results retrieved successfully"));
  }
}
