package com.vegalife.unit.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.search.SearchRequest;
import com.vegalife.dto.response.search.SearchResultItem;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.restaurant.Restaurant;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.restaurant.RestaurantRepository;
import com.vegalife.repository.restaurant.RestaurantRepository.RestaurantDishMatch;
import com.vegalife.service.search.SearchService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  @Mock private PostRepository postRepository;

  @Mock private RecipeRepository recipeRepository;

  @Mock private RestaurantRepository restaurantRepository;

  @InjectMocks private SearchService searchService;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient()
        .when(recipeRepository.findAll(any(Specification.class)))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(postRepository.findAll(any(Specification.class)))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(restaurantRepository.findAll(any(Specification.class)))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(restaurantRepository.findDishNameMatches(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(List.of());
  }

  @Test
  void search_blankQueryThrowsValidationException() {
    assertThatThrownBy(() -> searchService.search(SearchRequest.builder().q("   ").build()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void search_titleMatchOutranksBodyOnlyMatch() {
    Post titleMatch = post("Vegan tofu bowl", "A simple lunch.", Instant.now().minusSeconds(100));
    Post bodyMatch = post("Easy lunch", "A simple vegan tofu recipe.", Instant.now());
    when(postRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(bodyMatch, titleMatch));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("tofu").build());

    assertThat(response.getContent())
        .extracting(SearchResultItem::getTitle)
        .containsExactly("Vegan tofu bowl", "Easy lunch");
  }

  @Test
  void search_sameRankSortsNewestFirst() {
    Post older = post("Tofu bowl", "Body", Instant.now().minusSeconds(100));
    Post newer = post("Tofu wrap", "Body", Instant.now());
    when(postRepository.findAll(any(Specification.class))).thenReturn(List.of(older, newer));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("tofu").build());

    assertThat(response.getContent())
        .extracting(SearchResultItem::getTitle)
        .containsExactly("Tofu wrap", "Tofu bowl");
  }

  @Test
  void search_postWithVideoMediaIsClassifiedAsVideo() {
    Post videoPost = post("Tofu cooking video", "Watch this.", Instant.now());
    videoPost.setMedia(Set.of(Media.builder().mimeType("video/mp4").build()));
    when(postRepository.findAll(any(Specification.class))).thenReturn(List.of(videoPost));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("tofu").build());

    assertThat(response.getContent())
        .singleElement()
        .extracting(SearchResultItem::getType)
        .isEqualTo("video");
  }

  @Test
  void search_typeFilterExcludesMismatchedPostSubtype() {
    Post videoPost = post("Tofu cooking video", "Watch this.", Instant.now());
    videoPost.setMedia(Set.of(Media.builder().mimeType("video/mp4").build()));
    Post plainPost = post("Tofu written guide", "Read this.", Instant.now());
    when(postRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(videoPost, plainPost));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("tofu").type("post").build());

    assertThat(response.getContent())
        .extracting(SearchResultItem::getTitle)
        .containsExactly("Tofu written guide");
  }

  @Test
  void search_typeRecipeSkipsPostRepository() {
    searchService.search(SearchRequest.builder().q("tofu").type("recipe").build());

    org.mockito.Mockito.verify(postRepository, org.mockito.Mockito.never())
        .findAll(any(Specification.class));
  }

  @Test
  void search_includesRecipesMatchingNameOrInstructions() {
    Recipe recipe = recipe("Tofu stir fry", "A quick dinner", "Fry the tofu.", Instant.now());
    when(recipeRepository.findAll(any(Specification.class))).thenReturn(List.of(recipe));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("tofu").build());

    assertThat(response.getContent())
        .anySatisfy(
            item -> {
              assertThat(item.getType()).isEqualTo("recipe");
              assertThat(item.getTitle()).isEqualTo("Tofu stir fry");
              assertThat(item.getCategoryIds()).isEmpty();
            });
  }

  @Test
  void search_snippetHighlightsMatchedText() {
    Post matched = post("Vegan tofu bowl", "A simple plant-based lunch with tofu.", Instant.now());
    when(postRepository.findAll(any(Specification.class))).thenReturn(List.of(matched));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("tofu").build());

    assertThat(response.getContent().get(0).getSnippet()).contains("<mark>tofu</mark>");
  }

  @Test
  void search_noMatchesReturnsEmptyPage() {
    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("nonexistent").build());

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isZero();
  }

  @Test
  void search_paginatesAcrossCombinedResults() {
    Post postA = post("Tofu A", "Body", Instant.now().minusSeconds(300));
    Post postB = post("Tofu B", "Body", Instant.now().minusSeconds(200));
    Post postC = post("Tofu C", "Body", Instant.now().minusSeconds(100));
    when(postRepository.findAll(any(Specification.class))).thenReturn(List.of(postA, postB, postC));

    PageResponse<SearchResultItem> firstPage =
        searchService.search(SearchRequest.builder().q("tofu").page(0).size(2).build());

    assertThat(firstPage.getContent()).hasSize(2);
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  void search_typeRestaurantReturnsNameMatch() {
    Restaurant restaurant = restaurant("Vegan Tofu House", "Cozy spot.", Instant.now());
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of(restaurant));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("tofu").type("restaurant").build());

    assertThat(response.getContent())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getType()).isEqualTo("restaurant");
              assertThat(item.getTitle()).isEqualTo("Vegan Tofu House");
              assertThat(item.getCategoryIds()).isEmpty();
            });
  }

  @Test
  void search_restaurantDescriptionOnlyMatchRanksBelowNameMatch() {
    Restaurant descriptionMatch =
        restaurant("Garden Cafe", "Famous for its tofu stir fry.", Instant.now().minusSeconds(50));
    Restaurant nameMatch = restaurant("Tofu Corner", "A small eatery.", Instant.now());
    when(restaurantRepository.findAll(any(Specification.class)))
        .thenReturn(List.of(descriptionMatch, nameMatch));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("tofu").type("restaurant").build());

    assertThat(response.getContent())
        .extracting(SearchResultItem::getTitle)
        .containsExactly("Tofu Corner", "Garden Cafe");
  }

  @Test
  void search_restaurantDishOnlyMatchProducesDishNamingSnippetAndRanksWithDescriptionTier() {
    Restaurant dishMatched = restaurant("Garden Cafe", "A small vegan eatery.", Instant.now());
    when(restaurantRepository.findDishNameMatches("pho"))
        .thenReturn(List.of(dishMatch(dishMatched.getId(), "Pho Chay")));
    when(restaurantRepository.findAllById(List.of(dishMatched.getId())))
        .thenReturn(List.of(dishMatched));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("pho").type("restaurant").build());

    assertThat(response.getContent())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getTitle()).isEqualTo("Garden Cafe");
              assertThat(item.getSnippet()).contains("Pho").contains("<mark>Pho</mark>");
            });
  }

  @Test
  void search_restaurantMatchedByBothNameAndDishAppearsOnceAtBestRank() {
    Restaurant matchedBoth = restaurant("Pho House", "A cozy spot.", Instant.now());
    when(restaurantRepository.findAll(any(Specification.class))).thenReturn(List.of(matchedBoth));
    when(restaurantRepository.findDishNameMatches("pho"))
        .thenReturn(List.of(dishMatch(matchedBoth.getId(), "Pho Chay")));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("pho").type("restaurant").build());

    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getContent().get(0).getSnippet()).doesNotContain("Matches via dish");
  }

  @Test
  void search_restaurantDishMatchFromDifferentRestaurantIsNotAFalsePositive() {
    // The matched dish belongs only to "Other Place"; "Target" must not appear (issue #196,
    // point 10).
    Restaurant otherPlace = restaurant("Other Place", "Serves many dishes.", Instant.now());
    when(restaurantRepository.findDishNameMatches("pho"))
        .thenReturn(List.of(dishMatch(otherPlace.getId(), "Pho Chay")));
    when(restaurantRepository.findAllById(List.of(otherPlace.getId())))
        .thenReturn(List.of(otherPlace));

    PageResponse<SearchResultItem> response =
        searchService.search(SearchRequest.builder().q("pho").type("restaurant").build());

    assertThat(response.getContent())
        .extracting(SearchResultItem::getTitle)
        .containsExactly("Other Place");
  }

  private RestaurantDishMatch dishMatch(UUID restaurantId, String dishName) {
    return new RestaurantDishMatch() {
      @Override
      public UUID getRestaurantId() {
        return restaurantId;
      }

      @Override
      public String getDishName() {
        return dishName;
      }
    };
  }

  private Restaurant restaurant(String name, String description, Instant createdAt) {
    Restaurant restaurant =
        Restaurant.builder()
            .id(UUID.randomUUID())
            .name(name)
            .address("123 Vegan St")
            .latitude(new BigDecimal("10.0"))
            .longitude(new BigDecimal("106.0"))
            .description(description)
            .build();
    restaurant.setCreatedAt(createdAt);
    return restaurant;
  }

  private Post post(String title, String content, Instant createdAt) {
    Post post =
        Post.builder()
            .id(UUID.randomUUID())
            .title(title)
            .content(content)
            .status(PostStatus.published)
            .categories(Set.of(Category.builder().id(UUID.randomUUID()).build()))
            .build();
    post.setCreatedAt(createdAt);
    return post;
  }

  private Recipe recipe(String name, String description, String instructions, Instant createdAt) {
    Recipe recipe =
        Recipe.builder()
            .id(UUID.randomUUID())
            .dish(Dish.builder().id(UUID.randomUUID()).build())
            .name(name)
            .description(description)
            .instructions(instructions)
            .servings(2)
            .build();
    recipe.setCreatedAt(createdAt);
    return recipe;
  }
}
