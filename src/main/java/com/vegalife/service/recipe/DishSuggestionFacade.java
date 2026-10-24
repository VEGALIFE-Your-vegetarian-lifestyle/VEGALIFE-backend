package com.vegalife.service.recipe;

import com.vegalife.dto.request.recipe.DishSuggestionRequest;
import com.vegalife.dto.response.menu.RecipeSummaryResponse;
import com.vegalife.dto.response.recipe.DishSuggestionResponse;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.shared.dto.PageResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps a {@link DishSuggestionRequest} onto {@link DishSuggestionService} and the wire shapes
 * (issue #163). Shared by the suggestions endpoint and the {@code suggest_dishes} tool so the two
 * can never drift apart.
 */
@Service
@RequiredArgsConstructor
public class DishSuggestionFacade {

  private final DishSuggestionService dishSuggestionService;

  @Transactional(readOnly = true)
  public PageResponse<DishSuggestionResponse> suggest(UUID userId, DishSuggestionRequest request) {
    DishSuggestionService.PageResult result =
        dishSuggestionService.suggest(
            userId,
            request.getName(),
            request.getCuisineType(),
            toFilter(request),
            request.getPage(),
            request.getSize(),
            request.getRecipesPerDish());

    List<DishSuggestionResponse> content =
        result.content().stream()
            .map(
                suggestion ->
                    DishSuggestionResponse.builder()
                        .id(suggestion.dish().getId())
                        .name(suggestion.dish().getName())
                        .description(suggestion.dish().getDescription())
                        .imageUrl(suggestion.dish().getImageUrl())
                        .cuisineType(suggestion.dish().getCuisineType())
                        .recommendedRecipes(
                            suggestion.recipes().stream()
                                .map(RecipeSummaryMapper::toSummary)
                                .toList())
                        .build())
            .toList();

    return PageResponse.<DishSuggestionResponse>builder()
        .content(content)
        .page(result.page())
        .size(result.size())
        .totalElements(result.totalElements())
        .totalPages(result.totalPages())
        .first(result.page() == 0)
        .last(result.page() + 1 >= result.totalPages())
        .build();
  }

  private static RecipeFilter toFilter(DishSuggestionRequest request) {
    return RecipeFilter.builder()
        .difficulty(request.getDifficulty())
        .maxPrepTimeMinutes(request.getMaxPrepTime())
        .maxCookTimeMinutes(request.getMaxCookTime())
        .maxTotalTimeMinutes(request.getMaxTotalTime())
        .minCalories(request.getMinCalories())
        .maxCalories(request.getMaxCalories())
        .minProteinG(request.getMinProtein())
        .build();
  }

  private static RecipeSummaryResponse toRecipeSummary(Recipe recipe) {
    return RecipeSummaryMapper.toSummary(recipe);
  }
}
