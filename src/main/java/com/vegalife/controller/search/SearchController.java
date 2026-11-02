package com.vegalife.controller.search;

import com.vegalife.dto.request.search.SearchRequest;
import com.vegalife.dto.response.search.SearchResultItem;
import com.vegalife.service.search.SearchService;
import com.vegalife.shared.dto.ApiResponse;
import com.vegalife.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

  private final SearchService searchService;

  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<SearchResultItem>>> search(
      @Valid @ModelAttribute SearchRequest request) {
    PageResponse<SearchResultItem> results = searchService.search(request);
    return ResponseEntity.ok(ApiResponse.success(results, "Search completed successfully"));
  }
}
