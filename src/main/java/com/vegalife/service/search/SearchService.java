package com.vegalife.service.search;

import com.vegalife.dto.request.search.SearchRequest;
import com.vegalife.dto.response.search.SearchResultItem;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.PostSpecifications;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.recipe.RecipeSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keyword search across posts, videos, and recipes (issue #40). No new search dependency: matching
 * is a case-insensitive {@code ILIKE} (via {@link PostSpecifications#searchable} / {@link
 * RecipeSpecifications#searchable}), "relevance" is a two-tier rank — a title/name match outranks a
 * body-only match — with recency as the tiebreak, and "video" is a post carrying at least one
 * {@code video/*} media (there is no separate video entity). Every matching row is loaded up front
 * and ranked/paginated in memory, the same pattern already used for locations (issues #9/#10): a
 * relevance rank can only be computed after inspecting each row's matched field.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

  /** Characters kept on each side of the first match when building a snippet. */
  private static final int SNIPPET_RADIUS = 75;

  /** Rank ascending (title/name match first), then newest first within the same rank. */
  private static final Comparator<ScoredResult> RESULT_ORDER =
      Comparator.comparingInt(ScoredResult::rank)
          .thenComparing(Comparator.comparing(ScoredResult::sortTime).reversed());

  private final PostRepository postRepository;
  private final RecipeRepository recipeRepository;

  @Transactional(readOnly = true)
  public PageResponse<SearchResultItem> search(SearchRequest request) {
    String query = request.getQ().trim();
    if (query.isEmpty()) {
      throw new ValidationException("q must not be blank");
    }
    String type =
        request.getType() == null ? "" : request.getType().trim().toLowerCase(Locale.ROOT);

    List<ScoredResult> results = new ArrayList<>();
    if (type.isEmpty() || type.equals("post") || type.equals("video")) {
      results.addAll(
          searchPosts(
              query, type, request.getCategoryId(), request.getDateFrom(), request.getDateTo()));
    }
    if (type.isEmpty() || type.equals("recipe")) {
      results.addAll(searchRecipes(query, request.getDateFrom(), request.getDateTo()));
    }

    results.sort(RESULT_ORDER);

    Page<SearchResultItem> page =
        paginate(
            results.stream().map(ScoredResult::item).toList(),
            request.getPage(),
            request.getSize());
    return PageResponse.from(page);
  }

  private List<ScoredResult> searchPosts(
      String query, String type, UUID categoryId, Instant dateFrom, Instant dateTo) {
    List<Post> posts =
        postRepository.findAll(PostSpecifications.searchable(query, categoryId, dateFrom, dateTo));

    List<ScoredResult> scored = new ArrayList<>();
    for (Post post : posts) {
      String rowType = isVideo(post) ? "video" : "post";
      if (!type.isEmpty() && !type.equals(rowType)) {
        continue;
      }
      scored.add(scorePost(post, query, rowType));
    }
    return scored;
  }

  private List<ScoredResult> searchRecipes(String query, Instant dateFrom, Instant dateTo) {
    List<Recipe> recipes =
        recipeRepository.findAll(RecipeSpecifications.searchable(query, dateFrom, dateTo));
    return recipes.stream().map(recipe -> scoreRecipe(recipe, query)).toList();
  }

  private boolean isVideo(Post post) {
    return post.getMedia().stream()
        .anyMatch(media -> media.getMimeType() != null && media.getMimeType().startsWith("video/"));
  }

  private ScoredResult scorePost(Post post, String query, String rowType) {
    boolean titleMatch = containsIgnoreCase(post.getTitle(), query);
    String snippetSource = titleMatch ? post.getTitle() : post.getContent();
    Set<UUID> categoryIds =
        post.getCategories().stream().map(Category::getId).collect(Collectors.toSet());

    SearchResultItem item =
        SearchResultItem.builder()
            .type(rowType)
            .id(post.getId())
            .title(post.getTitle())
            .snippet(buildSnippet(snippetSource, query))
            .categoryIds(categoryIds)
            .createdAt(post.getCreatedAt())
            .build();
    return new ScoredResult(item, titleMatch ? 0 : 1, post.getCreatedAt());
  }

  private ScoredResult scoreRecipe(Recipe recipe, String query) {
    boolean nameMatch = containsIgnoreCase(recipe.getName(), query);
    String snippetSource =
        nameMatch
            ? recipe.getName()
            : firstContaining(query, recipe.getDescription(), recipe.getInstructions());

    SearchResultItem item =
        SearchResultItem.builder()
            .type("recipe")
            .id(recipe.getId())
            .title(recipe.getName())
            .snippet(buildSnippet(snippetSource, query))
            .categoryIds(Set.of())
            .createdAt(recipe.getCreatedAt())
            .build();
    return new ScoredResult(item, nameMatch ? 0 : 1, recipe.getCreatedAt());
  }

  private String firstContaining(String query, String... candidates) {
    for (String candidate : candidates) {
      if (containsIgnoreCase(candidate, query)) {
        return candidate;
      }
    }
    return candidates.length > 0 ? candidates[0] : null;
  }

  private boolean containsIgnoreCase(String text, String query) {
    return text != null && text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
  }

  /**
   * Wraps the first case-insensitive match of {@code query} in {@code text} with {@code <mark>}.
   */
  private String buildSnippet(String text, String query) {
    if (text == null || text.isBlank()) {
      return "";
    }
    int matchIndex = text.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
    if (matchIndex < 0) {
      return truncate(text);
    }

    int start = Math.max(0, matchIndex - SNIPPET_RADIUS);
    int matchEnd = matchIndex + query.length();
    int end = Math.min(text.length(), matchEnd + SNIPPET_RADIUS);

    String prefix = start > 0 ? "…" : "";
    String suffix = end < text.length() ? "…" : "";
    return prefix
        + text.substring(start, matchIndex)
        + "<mark>"
        + text.substring(matchIndex, matchEnd)
        + "</mark>"
        + text.substring(matchEnd, end)
        + suffix;
  }

  private String truncate(String text) {
    int limit = SNIPPET_RADIUS * 2;
    return text.length() <= limit ? text : text.substring(0, limit) + "…";
  }

  private Page<SearchResultItem> paginate(List<SearchResultItem> items, int page, int size) {
    int fromIndex = Math.min(page * size, items.size());
    int toIndex = Math.min(fromIndex + size, items.size());
    return new PageImpl<>(
        items.subList(fromIndex, toIndex), PageRequest.of(page, size), items.size());
  }

  private record ScoredResult(SearchResultItem item, int rank, Instant sortTime) {}
}
