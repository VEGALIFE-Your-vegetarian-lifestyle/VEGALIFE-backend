package com.vegalife.unit.repository.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.recipe.RecipeSpecifications;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class RecipeSpecificationsTest {

  private static final Instant CREATED_MARCH = Instant.parse("2026-03-15T12:00:00Z");
  private static final Instant CREATED_JUNE = Instant.parse("2026-06-15T12:00:00Z");
  private static final Instant CREATED_SEPTEMBER = Instant.parse("2026-09-15T12:00:00Z");

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private EntityManager entityManager;

  private User alice;
  private User bob;
  private Category vegan;
  private Category quick;
  private Dish dish;
  private Post aliceSpringRollsPost;
  private Post aliceStirFryPost;
  private Post bobBurgerPost;
  private Recipe aliceSpringRolls;
  private Recipe aliceStirFry;
  private Recipe aliceDeletedSoup;
  private Recipe bobBurger;
  private Recipe aliceDualCurry;

  @BeforeEach
  void seed() {
    alice = createUser("alice", "alice@example.com");
    bob = createUser("bob", "bob@example.com");
    vegan = createCategory("Vegan");
    quick = createCategory("Quick meals");
    dish = createDish("Bowl");

    aliceSpringRollsPost = createPost(alice, "Spring rolls post", Set.of(vegan, quick));
    aliceStirFryPost = createPost(alice, "Stir fry post", Set.of(quick));
    bobBurgerPost = createPost(bob, "Burger post", Set.of());

    aliceSpringRolls = createRecipe(alice, "Spring rolls", null, Set.of(aliceSpringRollsPost));
    aliceStirFry = createRecipe(alice, "Stir fry", null, Set.of(aliceStirFryPost));
    aliceDeletedSoup = createRecipe(alice, "Mushroom soup", Instant.now(), Set.of());
    bobBurger = createRecipe(bob, "Bean burger", null, Set.of(bobBurgerPost));
    aliceDualCurry =
        createRecipe(alice, "Coconut curry", null, Set.of(aliceSpringRollsPost, aliceStirFryPost));

    entityManager.flush();
    setCreatedAt(aliceSpringRolls, CREATED_MARCH);
    setCreatedAt(aliceStirFry, CREATED_JUNE);
    setCreatedAt(aliceDeletedSoup, CREATED_MARCH);
    setCreatedAt(bobBurger, CREATED_SEPTEMBER);
    setCreatedAt(aliceDualCurry, CREATED_JUNE);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void allWithFilters_withoutFilters_returnsEveryRecipeIncludingSoftDeleted() {
    Page<Recipe> result = findAll(null, null, null, null);

    assertThat(result.getContent())
        .extracting(Recipe::getName)
        .containsExactlyInAnyOrder(
            "Spring rolls", "Stir fry", "Mushroom soup", "Bean burger", "Coconut curry");
    assertThat(result.getTotalElements()).isEqualTo(5);
  }

  @Test
  void allWithFilters_byUserId_returnsOnlyThatAuthorsRecipes() {
    Page<Recipe> result = findAll(bob.getId(), null, null, null);

    assertThat(result.getContent()).extracting(Recipe::getName).containsExactly("Bean burger");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byCategoryId_returnsEachMatchingRecipeExactlyOnce() {
    Page<Recipe> result = findAll(null, quick.getId(), null, null);

    assertThat(result.getContent())
        .extracting(Recipe::getName)
        .containsExactlyInAnyOrder("Spring rolls", "Stir fry", "Coconut curry");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void allWithFilters_byCategoryWithSingleMembership_returnsTheRecipeOnce() {
    Page<Recipe> result = findAll(null, vegan.getId(), null, null);

    assertThat(result.getContent())
        .extracting(Recipe::getName)
        .containsExactlyInAnyOrder("Spring rolls", "Coconut curry");
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void allWithFilters_byCreatedAtRange_isInclusiveOnBothBounds() {
    Page<Recipe> result = findAll(null, null, CREATED_MARCH, CREATED_JUNE);

    assertThat(result.getContent())
        .extracting(Recipe::getName)
        .containsExactlyInAnyOrder("Spring rolls", "Mushroom soup", "Stir fry", "Coconut curry");
    assertThat(result.getTotalElements()).isEqualTo(4);
  }

  @Test
  void allWithFilters_byCreatedAtLowerBound_excludesRecipesCreatedBeforeIt() {
    Page<Recipe> result = findAll(null, null, Instant.parse("2026-04-01T00:00:00Z"), null);

    assertThat(result.getContent())
        .extracting(Recipe::getName)
        .containsExactlyInAnyOrder("Stir fry", "Bean burger", "Coconut curry");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void allWithFilters_withCombinedFilters_appliesEveryPredicate() {
    Page<Recipe> result = findAll(alice.getId(), vegan.getId(), CREATED_MARCH, CREATED_JUNE);

    assertThat(result.getContent())
        .extracting(Recipe::getName)
        .containsExactlyInAnyOrder("Spring rolls", "Coconut curry");
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void allWithFilters_withFilterMatchingNothing_returnsEmptyPage() {
    Page<Recipe> result = findAll(UUID.randomUUID(), null, null, null);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  private Page<Recipe> findAll(
      UUID userId, UUID categoryId, Instant createdFrom, Instant createdTo) {
    Specification<Recipe> spec =
        RecipeSpecifications.allWithFilters(userId, categoryId, createdFrom, createdTo);
    Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");
    return recipeRepository.findAll(spec, PageRequest.of(0, 20, sort));
  }

  private User createUser(String username, String email) {
    User user =
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("password-hash")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    entityManager.persist(user);
    return user;
  }

  private Category createCategory(String name) {
    Category category = Category.builder().name(name).build();
    entityManager.persist(category);
    return category;
  }

  private Dish createDish(String name) {
    Dish newDish = Dish.builder().name(name).build();
    entityManager.persist(newDish);
    return newDish;
  }

  private Post createPost(User owner, String title, Set<Category> categories) {
    Post post =
        Post.builder()
            .user(owner)
            .title(title)
            .content("Post content")
            .status(Post.Status.published)
            .flag(Post.Flag.PENDING)
            .viewCount(0)
            .categories(categories)
            .build();
    entityManager.persist(post);
    return post;
  }

  private Recipe createRecipe(User owner, String name, Instant deletedAt, Set<Post> posts) {
    Recipe recipe =
        Recipe.builder()
            .dish(dish)
            .user(owner)
            .name(name)
            .instructions("Stir and serve")
            .servings(2)
            .difficulty(Recipe.Difficulty.EASY)
            .posts(posts)
            .deletedAt(deletedAt)
            .build();
    entityManager.persist(recipe);
    return recipe;
  }

  private void setCreatedAt(Recipe recipe, Instant createdAt) {
    entityManager
        .createNativeQuery("UPDATE recipe SET created_at = :createdAt WHERE id = :id")
        .setParameter("createdAt", Timestamp.from(createdAt))
        .setParameter("id", recipe.getId())
        .executeUpdate();
  }
}
