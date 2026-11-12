package com.vegalife.integration.controller.restaurant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.restaurant.Restaurant;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.restaurant.RestaurantRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class RestaurantControllerIntegrationTest {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("pgvector/pgvector:pg15")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private RestaurantRepository restaurantRepository;

  @Autowired private DishRepository dishRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String accessToken;

  @BeforeEach
  void setUp() {
    User user =
        userRepository.save(
            User.builder()
                .displayName("diner")
                .email("diner@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void listRestaurants_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/restaurants")).andExpect(status().isUnauthorized());
  }

  @Test
  void listRestaurants_noMatchesReturnsEmptyPageWith200() throws Exception {
    mockMvc
        .perform(get("/api/restaurants").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void listRestaurants_sortsByRatingDescendingAndExcludesDeleted() throws Exception {
    Restaurant highRated = createRestaurant("Top Pick", new BigDecimal("4.8"), null, null);
    Restaurant midRated = createRestaurant("Mid Tier", new BigDecimal("4.0"), null, null);
    createRestaurant("Soft Deleted", new BigDecimal("5.0"), null, Instant.now());

    mockMvc
        .perform(get("/api/restaurants").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[0].id").value(highRated.getId().toString()))
        .andExpect(jsonPath("$.data.content[1].id").value(midRated.getId().toString()));
  }

  @Test
  void listRestaurants_includesGalleryAndDescription() throws Exception {
    Restaurant restaurant = createRestaurant("Tofu House", new BigDecimal("4.2"), null, null);
    restaurant.setImageUrls(java.util.List.of("https://example.com/a.jpg"));
    restaurant.setDescription("Cozy all-vegan spot.");
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(get("/api/restaurants").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].imageUrls[0]").value("https://example.com/a.jpg"))
        .andExpect(jsonPath("$.data.content[0].description").value("Cozy all-vegan spot."));
  }

  @Test
  void listRestaurants_responseNeverIncludesTimestamps() throws Exception {
    createRestaurant("Tofu House", new BigDecimal("4.2"), null, null);

    mockMvc
        .perform(get("/api/restaurants").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].updatedAt").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].deletedAt").doesNotExist());
  }

  @Test
  void listRestaurants_minRatingFilterExcludesLowerRated() throws Exception {
    Restaurant highRated = createRestaurant("Top Pick", new BigDecimal("4.8"), null, null);
    createRestaurant("Low Rated", new BigDecimal("3.0"), null, null);
    createRestaurant("No Rating", null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("minRating", "4.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(highRated.getId().toString()));
  }

  @Test
  void listRestaurants_maxPriceLevelFilterExcludesPricierRestaurants() throws Exception {
    Restaurant affordable = createRestaurant("Budget Eats", null, 1, null);
    createRestaurant("Fancy Place", null, 4, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("maxPriceLevel", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(affordable.getId().toString()));
  }

  @Test
  void listRestaurants_openNowExcludesRestaurantsOutsideHoursOrWithoutAny() throws Exception {
    LocalTime now = LocalTime.now();
    String today = LocalDate.now().getDayOfWeek().toString().toLowerCase(Locale.ROOT);
    Restaurant openNow =
        createRestaurant(
            "Open Now",
            null,
            null,
            null,
            openingHoursJson(today, now.minusHours(1), now.plusHours(1)));
    createRestaurant(
        "Closed Now",
        null,
        null,
        null,
        openingHoursJson(today, now.plusHours(2), now.plusHours(3)));
    createRestaurant("No Hours", null, null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("openNow", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(openNow.getId().toString()));
  }

  @Test
  void listRestaurants_pageAndSizeAreHonored() throws Exception {
    createRestaurant("A", new BigDecimal("5.0"), null, null);
    createRestaurant("B", new BigDecimal("4.5"), null, null);
    createRestaurant("C", new BigDecimal("4.0"), null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2));
  }

  @Test
  void listNearbyRestaurants_withoutJwt_returns401() throws Exception {
    mockMvc
        .perform(
            get("/api/restaurants/nearby")
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "2000"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listNearbyRestaurants_noMatchesReturnsEmptyArrayWith200() throws Exception {
    mockMvc
        .perform(
            get("/api/restaurants/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "2000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void listNearbyRestaurants_returnsOnlyWithinRadiusSortedByDistanceWithComputedField()
      throws Exception {
    // ~620m from the search centre — within a 2000m radius.
    Restaurant near =
        createRestaurantAt("Near", new BigDecimal("10.777529"), new BigDecimal("106.695528"));
    // ~22km from the search centre — outside a 2000m radius.
    createRestaurantAt("Far", new BigDecimal("10.972465"), new BigDecimal("106.698055"));

    mockMvc
        .perform(
            get("/api/restaurants/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "2000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(near.getId().toString()))
        .andExpect(
            jsonPath("$.data[0].distance").value(org.hamcrest.Matchers.closeTo(620.0, 50.0)));
  }

  @Test
  void listNearbyRestaurants_sortsMultipleMatchesByAscendingDistance() throws Exception {
    Restaurant farther =
        createRestaurantAt("Farther", new BigDecimal("10.777529"), new BigDecimal("106.695528"));
    Restaurant closer =
        createRestaurantAt("Closer", new BigDecimal("10.773465"), new BigDecimal("106.698055"));

    mockMvc
        .perform(
            get("/api/restaurants/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "5000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(closer.getId().toString()))
        .andExpect(jsonPath("$.data[1].id").value(farther.getId().toString()));
  }

  @Test
  void listNearbyRestaurants_excludesSoftDeletedRestaurants() throws Exception {
    Restaurant deleted =
        createRestaurantAt("Deleted", new BigDecimal("10.772465"), new BigDecimal("106.698055"));
    deleted.setDeletedAt(Instant.now());
    restaurantRepository.save(deleted);

    mockMvc
        .perform(
            get("/api/restaurants/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "5000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void listNearbyRestaurants_missingRequiredParamReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/restaurants/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listNearbyRestaurants_outOfRangeLatitudeReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/restaurants/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "95")
                .param("lng", "106.698055")
                .param("radius", "2000"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listNearbyRestaurants_nonPositiveRadiusReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/restaurants/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listRestaurants_combinedFiltersApplyWithAndLogic() throws Exception {
    Restaurant match = createRestaurant("Match", new BigDecimal("4.5"), 2, null);
    createRestaurant("TooExpensive", new BigDecimal("4.5"), 4, null);
    createRestaurant("TooLowRated", new BigDecimal("2.0"), 2, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("minRating", "4.0")
                .param("maxPriceLevel", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(match.getId().toString()));
  }

  @Test
  void listRestaurants_keywordMatchesName() throws Exception {
    Restaurant match = createRestaurant("Vegan Bistro", null, null, null);
    createRestaurant("Other Place", null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("keyword", "bistro"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(match.getId().toString()));
  }

  @Test
  void listRestaurants_keywordMatchesDescription() throws Exception {
    Restaurant match = createRestaurant("Garden Cafe", null, null, null);
    match.setDescription("Famous for its pho.");
    restaurantRepository.save(match);
    createRestaurant("Other Place", null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("keyword", "PHO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(match.getId().toString()));
  }

  @Test
  void listRestaurants_keywordMatchesAttachedApprovedDishName() throws Exception {
    Restaurant match = createRestaurant("Unrelated Name", null, null, null);
    Dish dish = createDish("Pho Chay", ModerationStatus.APPROVED, null);
    match.setDishes(new HashSet<>(Set.of(dish)));
    restaurantRepository.save(match);
    createRestaurant("Other Place", null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("keyword", "pho"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(match.getId().toString()));
  }

  @Test
  void listRestaurants_keywordDishMatchFromDifferentRestaurantIsNotAFalsePositive()
      throws Exception {
    Restaurant otherRestaurant = createRestaurant("Other Place", null, null, null);
    Dish dish = createDish("Pho Chay", ModerationStatus.APPROVED, null);
    otherRestaurant.setDishes(new HashSet<>(Set.of(dish)));
    restaurantRepository.save(otherRestaurant);
    createRestaurant("Target Restaurant", null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("keyword", "pho"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(otherRestaurant.getId().toString()));
  }

  @Test
  void listRestaurants_blankKeywordAppliesNoFilter() throws Exception {
    createRestaurant("Vegan Bistro", null, null, null);
    createRestaurant("Other Place", null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("keyword", "   "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));
  }

  @Test
  void listRestaurants_keywordCombinesWithMinRatingAndMaxPriceLevelAndOpenNow() throws Exception {
    LocalTime now = LocalTime.now();
    String today = LocalDate.now().getDayOfWeek().toString().toLowerCase(Locale.ROOT);
    Restaurant match =
        createRestaurant(
            "Vegan Bistro Prime",
            new BigDecimal("4.5"),
            2,
            null,
            openingHoursJson(today, now.minusHours(1), now.plusHours(1)));
    createRestaurant("Vegan Bistro Pricey", new BigDecimal("4.5"), 4, null);
    createRestaurant("Vegan Bistro Low Rated", new BigDecimal("2.0"), 2, null);
    createRestaurant(
        "Vegan Bistro Closed",
        new BigDecimal("4.5"),
        2,
        null,
        openingHoursJson(today, now.plusHours(2), now.plusHours(3)));

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("keyword", "bistro")
                .param("minRating", "4.0")
                .param("maxPriceLevel", "2")
                .param("openNow", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(match.getId().toString()));
  }

  @Test
  void listRestaurants_keywordNoMatchesReturnsEmptyArrayWith200() throws Exception {
    createRestaurant("Other Place", null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants")
                .header("Authorization", "Bearer " + accessToken)
                .param("keyword", "nonexistentkeyword"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void getRestaurant_withoutJwt_returns401() throws Exception {
    Restaurant restaurant = createRestaurant("Vegan Garden", null, null, null);

    mockMvc
        .perform(get("/api/restaurants/{id}", restaurant.getId()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getRestaurant_unknownIdReturns404WithMessage() throws Exception {
    mockMvc
        .perform(
            get("/api/restaurants/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Restaurant not found"));
  }

  @Test
  void getRestaurant_softDeletedRestaurantReturns404() throws Exception {
    Restaurant restaurant = createRestaurant("Gone", null, null, Instant.now());

    mockMvc
        .perform(
            get("/api/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Restaurant not found"));
  }

  @Test
  void getRestaurant_nestsApprovedDishesSortedByNameAndExcludesSoftDeleted() throws Exception {
    Restaurant restaurant = createRestaurant("Vegan Garden", new BigDecimal("4.5"), null, null);
    Dish zebra = createDish("Zebra Noodles", ModerationStatus.APPROVED, null);
    Dish apple = createDish("Apple Salad", ModerationStatus.APPROVED, null);
    Dish removed = createDish("Removed Dish", ModerationStatus.APPROVED, Instant.now());
    restaurant.setDishes(new HashSet<>(Set.of(zebra, apple, removed)));
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            get("/api/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(restaurant.getId().toString()))
        .andExpect(jsonPath("$.data.dishes.length()").value(2))
        .andExpect(jsonPath("$.data.dishes[0].name").value("Apple Salad"))
        .andExpect(jsonPath("$.data.dishes[1].name").value("Zebra Noodles"));
  }

  @Test
  void getRestaurant_withNoAttachedDishesReturnsEmptyArray() throws Exception {
    Restaurant restaurant = createRestaurant("No Dishes", null, null, null);

    mockMvc
        .perform(
            get("/api/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dishes").isArray())
        .andExpect(jsonPath("$.data.dishes").isEmpty());
  }

  @Test
  void listRestaurants_responseNeverIncludesPopulatedDishesArray() throws Exception {
    Restaurant restaurant = createRestaurant("Vegan Garden", new BigDecimal("4.5"), null, null);
    Dish dish = createDish("Pho Chay", ModerationStatus.APPROVED, null);
    restaurant.setDishes(new HashSet<>(Set.of(dish)));
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(get("/api/restaurants").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].dishes").doesNotExist());
  }

  private Dish createDish(String name, ModerationStatus status, Instant deletedAt) {
    return dishRepository.save(
        Dish.builder().name(name).moderationStatus(status).deletedAt(deletedAt).build());
  }

  private Restaurant createRestaurant(
      String name, BigDecimal rating, Integer priceLevel, Instant deletedAt) {
    return createRestaurant(name, rating, priceLevel, deletedAt, null);
  }

  private Restaurant createRestaurant(
      String name, BigDecimal rating, Integer priceLevel, Instant deletedAt, String openingHours) {
    return restaurantRepository.save(
        Restaurant.builder()
            .name(name)
            .address("123 Vegan St")
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .rating(rating)
            .priceLevel(priceLevel)
            .openingHours(openingHours)
            .deletedAt(deletedAt)
            .build());
  }

  private Restaurant createRestaurantAt(String name, BigDecimal lat, BigDecimal lng) {
    return restaurantRepository.save(
        Restaurant.builder()
            .name(name)
            .address("123 Vegan St")
            .latitude(lat)
            .longitude(lng)
            .build());
  }

  private String openingHoursJson(String day, LocalTime open, LocalTime close) {
    return "{\""
        + day
        + "\": [{\"open\": \""
        + TIME_FORMAT.format(open)
        + "\", \"close\": \""
        + TIME_FORMAT.format(close)
        + "\"}]}";
  }
}
