package com.vegalife.service.recipe;

import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.DishSpecifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dish suggestions for the AI and the suggestions endpoint (issue #163).
 *
 * <p>A dish is suggested only when at least one of its recipes survives the allergy check and the
 * caller's recipe filters — "block on all": a dish with <em>some</em> safe recipes stays, with the
 * unsafe ones simply absent from its recommendations.
 *
 * <p>Dishes with no qualifying recipe are dropped <em>before</em> paging, so the returned totals
 * describe what the caller actually receives rather than being inflated by dishes that were later
 * filtered out.
 *
 * <p>Ordering is fixed ({@code name ASC}); the caller cannot supply a Spring Data sort, which would
 * let a request name a nonexistent property and surface a {@code PropertyReferenceException}.
 */
@Service
@RequiredArgsConstructor
public class DishSuggestionService {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 50;

  private final DishRepository dishRepository;
  private final RecipeRecommendationService recipeRecommendationService;

  /** One suggested dish and the recipes that may be recommended for it. */
  public record Suggestion(Dish dish, List<Recipe> recipes) {}

  /**
   * @param name optional dish-name substring
   * @param cuisineType optional exact cuisine, case-insensitive
   * @param filter optional recipe constraints, applied to both eligibility and recommendations
   * @param page zero-based page index over the eligible dishes
   * @param size dishes per page, clamped to 1..{@link #MAX_SIZE}
   * @param recipesPerDish recommendations per dish, clamped by {@link RecipeRecommendationService}
   * @return the eligible dishes of the requested page, each with its recommendations
   */
  @Transactional(readOnly = true)
  public PageResult suggest(
      UUID userId,
      String name,
      String cuisineType,
      RecipeFilter filter,
      int page,
      int size,
      int recipesPerDish) {
    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(MAX_SIZE, size));

    Page<Dish> candidates =
        dishRepository.findAll(
            DishSpecifications.activeWithFilters(name, cuisineType),
            PageRequest.of(0, Integer.MAX_VALUE, Sort.by(Sort.Direction.ASC, "name")));

    List<Dish> dishes = candidates.getContent();
    if (dishes.isEmpty()) {
      return new PageResult(List.of(), 0, safePage, safeSize);
    }

    Map<UUID, List<Recipe>> byDish =
        recipeRecommendationService.recommendFor(
            dishes.stream().map(Dish::getId).toList(), userId, filter, recipesPerDish);

    List<Suggestion> eligible = new ArrayList<>();
    for (Dish dish : dishes) {
      List<Recipe> recipes = byDish.get(dish.getId());
      if (recipes != null && !recipes.isEmpty()) {
        eligible.add(new Suggestion(dish, recipes));
      }
    }

    int total = eligible.size();
    int from = Math.min(safePage * safeSize, total);
    int to = Math.min(from + safeSize, total);
    return new PageResult(List.copyOf(eligible.subList(from, to)), total, safePage, safeSize);
  }

  /** Page of suggestions; totals describe the eligible dishes, after filtering. */
  public record PageResult(List<Suggestion> content, long totalElements, int page, int size) {
    public int totalPages() {
      return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
  }
}
