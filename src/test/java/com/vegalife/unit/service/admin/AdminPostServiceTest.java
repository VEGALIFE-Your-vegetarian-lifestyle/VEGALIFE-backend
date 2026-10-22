package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.admin.AdminPostMapper;
import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.response.admin.AdminPostDetailResponse;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.service.admin.AdminPostService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AdminPostServiceTest {

  @Mock private PostRepository postRepository;

  @Mock private ModerationLogRepository moderationLogRepository;

  @Mock private AdminPostMapper adminPostMapper;

  @Mock private RecipeRepository recipeRepository;

  @Mock private RecipeIngredientRepository recipeIngredientRepository;

  @Mock private DishRepository dishRepository;

  @Mock private IngredientRepository ingredientRepository;

  @InjectMocks private AdminPostService adminPostService;

  private User author;
  private Post post;
  private AdminPostListResponse postResponse;
  private Pageable expectedPageable;

  @BeforeEach
  void setUp() {
    author =
        User.builder()
            .id(UUID.randomUUID())
            .username("janedoe")
            .email("jane@example.com")
            .passwordHash("$2a$10$secret")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    post =
        Post.builder()
            .id(UUID.randomUUID())
            .user(author)
            .title("Vegan chili")
            .content("A hearty chili recipe")
            .status(Post.Status.published)
            .flag(Post.Flag.PASSED)
            .viewCount(7)
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .build();
    postResponse =
        AdminPostListResponse.builder()
            .id(post.getId())
            .title("Vegan chili")
            .content("A hearty chili recipe")
            .status("published")
            .flag("PASSED")
            .viewCount(7)
            .createdAt(post.getCreatedAt())
            .userId(author.getId())
            .username("janedoe")
            .email("jane@example.com")
            .build();
    expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void listPosts_withDefaults_usesDefaultPaginationAndSort() {
    Page<Post> page = new PageImpl<>(List.of(post), expectedPageable, 1);
    when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    PageResponse<AdminPostListResponse> result = adminPostService.listPosts(new PostListRequest());

    assertThat(result.getContent()).containsExactly(postResponse);
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(20);
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();

    verify(postRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listPosts_withFilters_parsesFiltersAndSort() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-12-31T23:59:59Z");
    PostListRequest request =
        PostListRequest.builder()
            .page(1)
            .size(10)
            .sort("viewCount,asc")
            .status("unpublished")
            .flag("REJECTED")
            .userId(author.getId())
            .categoryId(UUID.randomUUID())
            .createdFrom(from)
            .createdTo(to)
            .build();
    Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.ASC, "viewCount"));
    Page<Post> page = new PageImpl<>(List.of(post), pageable, 1);
    when(postRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    PageResponse<AdminPostListResponse> result = adminPostService.listPosts(request);

    assertThat(result.getContent()).containsExactly(postResponse);
    assertThat(result.getPage()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(10);
    verify(postRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  void listPosts_whenCreatedFromAfterCreatedTo_throwsValidationException() {
    PostListRequest request =
        PostListRequest.builder()
            .createdFrom(Instant.parse("2026-12-01T00:00:00Z"))
            .createdTo(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("createdFrom must be before createdTo");
  }

  @Test
  void listPosts_withInvalidStatus_throwsValidationException() {
    PostListRequest request = PostListRequest.builder().status("bogus").build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Status must be one of: created, processed, published, unpublished, hidden");
  }

  @Test
  void listPosts_withInvalidFlag_throwsValidationException() {
    PostListRequest request = PostListRequest.builder().flag("bogus").build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Flag must be one of: all, PENDING, PASSED, REJECTED, NEEDS_REVIEW");
  }

  @Test
  void listPosts_withFlagAll_passesNoFlagFilter() {
    PostListRequest request = PostListRequest.builder().flag("all").build();
    Page<Post> page = new PageImpl<>(List.of(post), expectedPageable, 1);
    when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminPostMapper.toResponse(post)).thenReturn(postResponse);

    PageResponse<AdminPostListResponse> result = adminPostService.listPosts(request);

    assertThat(result.getContent()).containsExactly(postResponse);
    verify(postRepository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void listPosts_withMalformedSort_throwsValidationException() {
    PostListRequest request = PostListRequest.builder().sort("createdAt,asc,extra").build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Sort must be in the form property,asc|desc");
  }

  @Test
  void listPosts_withDisallowedSortProperty_throwsValidationException() {
    PostListRequest request = PostListRequest.builder().sort("passwordHash,asc").build();

    assertThatThrownBy(() -> adminPostService.listPosts(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage(
            "Sort property must be one of: createdAt, publishedAt, updatedAt, viewCount, title");
  }

  @Test
  void listPosts_withEmptyPage_returnsEmptyContent() {
    Page<Post> page = Page.empty(expectedPageable);
    when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    PageResponse<AdminPostListResponse> result = adminPostService.listPosts(new PostListRequest());

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();
  }

  @Test
  void approvePost_publishesAndLogsWithReason() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.unpublished);
    post.setFlag(Post.Flag.REJECTED);
    post.setCategories(Set.of(Category.builder().id(UUID.randomUUID()).name("Dinner").build()));
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId())).thenReturn(List.of());

    adminPostService.approvePost(adminId, post.getId(), "Vegetarian recipe, filter false positive");

    assertThat(post.getStatus()).isEqualTo(Post.Status.published);
    assertThat(post.getPublishedAt()).isNotNull();
    // Auto-REJECTED is a record of what the filter decided; approving does not rewrite history.
    assertThat(post.getFlag()).isEqualTo(Post.Flag.REJECTED);

    ModerationLog entry = lastPostLog();
    assertThat(entry.getActorId()).isEqualTo(adminId);
    assertThat(entry.getAction()).isEqualTo("APPROVE_POST");
    assertThat(entry.getTargetType()).isEqualTo("POST");
    assertThat(entry.getTargetId()).isEqualTo(post.getId());
    assertThat(entry.getReason()).isEqualTo("Vegetarian recipe, filter false positive");
  }

  @Test
  void approvePost_approvesEveryPendingComponentOfThePost() {
    UUID adminId = UUID.randomUUID();
    post.setCategories(Set.of(Category.builder().id(UUID.randomUUID()).name("Dinner").build()));
    Dish dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("bún chả chay")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    Ingredient ingredient =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("đậu hũ")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    Recipe recipe =
        Recipe.builder()
            .id(UUID.randomUUID())
            .name("bún chả chay")
            .dish(dish)
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    stubPostWithRecipe(recipe, ingredient);

    adminPostService.approvePost(adminId, post.getId(), null);

    // Approving the post is the reviewer saying the names it introduced are acceptable.
    assertThat(recipe.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
    assertThat(dish.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
    assertThat(ingredient.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
    verify(recipeRepository).save(recipe);
    verify(dishRepository).save(dish);
    verify(ingredientRepository).save(ingredient);
  }

  @Test
  void approvePost_doesNotResurrectARejectedName() {
    UUID adminId = UUID.randomUUID();
    post.setCategories(Set.of(Category.builder().id(UUID.randomUUID()).name("Dinner").build()));
    Dish rejectedDish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("món bị từ chối")
            .moderationStatus(ModerationStatus.REJECTED)
            .build();
    Ingredient approvedIngredient =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("đậu hũ")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    Recipe recipe =
        Recipe.builder()
            .id(UUID.randomUUID())
            .name("món bị từ chối")
            .dish(rejectedDish)
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    stubPostWithRecipe(recipe, approvedIngredient);

    adminPostService.approvePost(adminId, post.getId(), null);

    // A rejected name is a permanent block (BR-DISH-001); approving a post that mentions it must
    // not undo a decision someone already made.
    assertThat(rejectedDish.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
    verify(dishRepository, never()).save(any(Dish.class));
    verify(ingredientRepository, never()).save(any(Ingredient.class));
  }

  @Test
  void rejectPost_unpublishesAndRejectsPendingComponents() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.published);
    post.setPublishedAt(Instant.parse("2026-09-21T10:00:00Z"));
    Dish dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("món lạ")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    Ingredient ingredient =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("nguyên liệu lạ")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    Recipe recipe =
        Recipe.builder()
            .id(UUID.randomUUID())
            .name("món lạ")
            .dish(dish)
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    stubPostWithRecipe(recipe, ingredient);

    adminPostService.rejectPost(adminId, post.getId(), "not vegan");

    assertThat(post.getStatus()).isEqualTo(Post.Status.unpublished);
    assertThat(post.getPublishedAt()).isNull();
    assertThat(recipe.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
    assertThat(dish.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
    assertThat(ingredient.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);

    ModerationLog entry = lastPostLog();
    assertThat(entry.getAction()).isEqualTo("REJECT_POST");
    assertThat(entry.getReason()).isEqualTo("not vegan");
    verify(recipeRepository, never()).delete(any(Recipe.class));
  }

  @Test
  void rejectPost_leavesAnApprovedNameApproved() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.published);
    Dish approvedDish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("phở chay")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    Ingredient pendingIngredient =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("nguyên liệu lạ")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    Recipe recipe =
        Recipe.builder()
            .id(UUID.randomUUID())
            .name("phở chay")
            .dish(approvedDish)
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    stubPostWithRecipe(recipe, pendingIngredient);

    adminPostService.rejectPost(adminId, post.getId(), null);

    // One bad post must not knock out a name other members' recipes and menus depend on.
    assertThat(approvedDish.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
    assertThat(recipe.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
    assertThat(pendingIngredient.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
    verify(dishRepository, never()).save(any(Dish.class));
    verify(recipeRepository, never()).save(any(Recipe.class));
  }

  @Test
  void approvePost_withoutCategory_throwsValidationException() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.unpublished);
    post.setCategories(Set.of());
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> adminPostService.approvePost(adminId, post.getId(), null))
        .isInstanceOf(ValidationException.class)
        .hasMessage("At least one category is required to publish a post");

    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
    verify(postRepository, never()).saveAndFlush(any(Post.class));
  }

  @Test
  void approvePost_hiddenPost_throwsValidationException() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.hidden);
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> adminPostService.approvePost(adminId, post.getId(), null))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("hidden post");
  }

  @Test
  void rejectPost_hiddenPost_throwsValidationException() {
    UUID adminId = UUID.randomUUID();
    post.setStatus(Post.Status.hidden);
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> adminPostService.rejectPost(adminId, post.getId(), null))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("hidden post");
  }

  @Test
  void approvePost_missingPost_throwsResourceNotFound() {
    UUID adminId = UUID.randomUUID();
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminPostService.approvePost(adminId, postId, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  @Test
  void rejectPost_missingPost_throwsResourceNotFound() {
    UUID adminId = UUID.randomUUID();
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminPostService.rejectPost(adminId, postId, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  /** Wires a post that has one recipe, one ingredient line, and a category to publish into. */
  private void stubPostWithRecipe(Recipe recipe, Ingredient ingredient) {
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    // The line carries only ids in the unit test; the service resolves the ingredient through the
    // repository when the association is absent.
    when(recipeIngredientRepository.findByRecipeId(recipe.getId()))
        .thenReturn(
            List.of(
                RecipeIngredient.builder()
                    .recipeId(recipe.getId())
                    .ingredientId(ingredient.getId())
                    .build()));
    lenient()
        .when(ingredientRepository.findById(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));
  }

  /** The component log entries are written after the post one, so read them from the end. */
  private ModerationLog lastPostLog() {
    ArgumentCaptor<ModerationLog> captor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getAllValues().stream()
        .filter(entry -> "POST".equals(entry.getTargetType()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no POST moderation log was written"));
  }

  @Test
  void getPostDetail_returnsScoresAndReferencedNames() {
    UUID recipeId = UUID.randomUUID();
    Dish dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("bún chả chay")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    Recipe recipe =
        Recipe.builder()
            .id(recipeId)
            .name("bún chả chay")
            .dish(dish)
            .score(0.81)
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    Ingredient tofu =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("đậu hũ")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    post.setContentScore(0.63);
    post.setFlag(Post.Flag.NEEDS_REVIEW);
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(recipeIngredientRepository.findByRecipeId(recipeId))
        .thenReturn(
            List.of(
                RecipeIngredient.builder()
                    .recipeId(recipeId)
                    .ingredientId(tofu.getId())
                    .ingredient(tofu)
                    .build()));
    AdminPostDetailResponse detail = adminPostService.getPostDetail(post.getId());

    assertThat(detail.getContentScore()).isEqualTo(0.63);
    assertThat(detail.getFlag()).isEqualTo("NEEDS_REVIEW");
    assertThat(detail.getRecipes()).hasSize(1);
    assertThat(detail.getRecipes().get(0).getScore()).isEqualTo(0.81);
    assertThat(detail.getRecipes().get(0).getType()).isEqualTo("RECIPE");
    assertThat(detail.getNames())
        .extracting(AdminPostDetailResponse.ComponentSummary::getType)
        .containsExactly("DISH", "INGREDIENT");
    assertThat(detail.getNames())
        .extracting(AdminPostDetailResponse.ComponentSummary::getModerationStatus)
        .containsExactly("PENDING", "APPROVED");
  }

  @Test
  void getPostDetail_postWithoutRecipe_hasNoComponents() {
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId())).thenReturn(List.of());

    AdminPostDetailResponse detail = adminPostService.getPostDetail(post.getId());

    assertThat(detail.getRecipes()).isEmpty();
    assertThat(detail.getNames()).isEmpty();
    // Null means never scored, which is what a post that never reached the filter looks like.
    assertThat(detail.getContentScore()).isNull();
  }

  @Test
  void getPostDetail_dishSharedByTwoRecipes_isListedOnce() {
    Dish dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("phở chay")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    Recipe first = Recipe.builder().id(UUID.randomUUID()).name("phở chay").dish(dish).build();
    Recipe second =
        Recipe.builder().id(UUID.randomUUID()).name("phở chay đặc biệt").dish(dish).build();
    when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(first, second));
    when(recipeIngredientRepository.findByRecipeId(first.getId())).thenReturn(List.of());
    when(recipeIngredientRepository.findByRecipeId(second.getId())).thenReturn(List.of());

    AdminPostDetailResponse detail = adminPostService.getPostDetail(post.getId());

    // One name, one decision: approving the dish twice would be nonsense to the reviewer.
    assertThat(detail.getRecipes()).hasSize(2);
    assertThat(detail.getNames()).hasSize(1);
  }

  @Test
  void getPostDetail_missingPost_throwsResourceNotFound() {
    UUID postId = UUID.randomUUID();
    when(postRepository.findByIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminPostService.getPostDetail(postId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }
}
