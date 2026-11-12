package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.restaurant.Restaurant;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.restaurant.RestaurantRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class AdminRestaurantControllerIntegrationTest {

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

  @Autowired private ModerationLogRepository moderationLogRepository;

  @Autowired private DishRepository dishRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    User admin =
        userRepository.save(
            User.builder()
                .displayName("adminuser")
                .email("admin@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.ADMIN)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    User regular =
        userRepository.save(
            User.builder()
                .displayName("regularuser")
                .email("user@example.com")
                .passwordHash("$2a$10$test")
                .role(UserRole.USER)
                .status(UserStatus.activated)
                .emailVerified(true)
                .build());
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(regular);
  }

  @Test
  void createRestaurant_asAdmin_returns201WithIdAndTimestamps() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue, District 1",
                      "latitude": 10.776889,
                      "longitude": 106.700806
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Restaurant created successfully"))
        .andExpect(jsonPath("$.data.id").exists())
        .andExpect(jsonPath("$.data.name").value("Vegan Garden"))
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

    assertThat(moderationLogRepository.findAll())
        .singleElement()
        .satisfies(log -> assertThat(log.getAction()).isEqualTo("CREATE_RESTAURANT"));
  }

  @Test
  void createRestaurant_priceLevelOutOfRange_returns400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "priceLevel": 5
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(restaurantRepository.findAll()).isEmpty();
  }

  @Test
  void createRestaurant_outOfRangeCoordinates_returns400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 95,
                      "longitude": 106.700806
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(restaurantRepository.findAll()).isEmpty();
  }

  @Test
  void createRestaurant_omittingGalleryAndDescription_storesNull() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.imageUrls").doesNotExist())
        .andExpect(jsonPath("$.data.description").doesNotExist());
  }

  @Test
  void createRestaurant_withGalleryAndDescription_roundTripsArrayColumn() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "imageUrls": ["https://example.com/a.jpg", "https://example.com/b.jpg"],
                      "description": "Cozy all-vegan spot."
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.imageUrls[0]").value("https://example.com/a.jpg"))
        .andExpect(jsonPath("$.data.imageUrls[1]").value("https://example.com/b.jpg"))
        .andExpect(jsonPath("$.data.description").value("Cozy all-vegan spot."));

    assertThat(restaurantRepository.findAll())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getImageUrls())
                  .containsExactly("https://example.com/a.jpg", "https://example.com/b.jpg");
              assertThat(saved.getDescription()).isEqualTo("Cozy all-vegan spot.");
            });
  }

  @Test
  void createRestaurant_nonAbsoluteImageUrl_returns400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "imageUrls": ["not-a-url"]
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(restaurantRepository.findAll()).isEmpty();
  }

  @Test
  void createRestaurant_nonAbsoluteWebsiteUrl_returns400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "websiteUrl": "ftp://example.com"
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(restaurantRepository.findAll()).isEmpty();
  }

  @Test
  void createRestaurant_asNonAdmin_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void createRestaurant_noToken_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateRestaurant_asAdmin_changesOnlySuppliedFieldsAndRefreshesUpdatedAt() throws Exception {
    Restaurant restaurant = createRestaurant("Old Name", new BigDecimal("4.0"));

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Restaurant updated successfully"))
        .andExpect(jsonPath("$.data.name").value("New Name"))
        .andExpect(jsonPath("$.data.address").value("123 Vegan St"));

    assertThat(moderationLogRepository.findAll())
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getAction()).isEqualTo("UPDATE_RESTAURANT");
              assertThat(log.getTargetId()).isEqualTo(restaurant.getId());
            });
  }

  @Test
  void updateRestaurant_emptyImageUrlsArrayClearsGallery() throws Exception {
    Restaurant restaurant = createRestaurant("Gallery Place", new BigDecimal("4.0"));
    restaurant.setImageUrls(java.util.List.of("https://example.com/a.jpg"));
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageUrls\": []}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.imageUrls").isArray())
        .andExpect(jsonPath("$.data.imageUrls").isEmpty());

    assertThat(restaurantRepository.findById(restaurant.getId()).orElseThrow().getImageUrls())
        .isEmpty();
  }

  @Test
  void updateRestaurant_omittedImageUrlsLeavesGalleryUnchanged() throws Exception {
    Restaurant restaurant = createRestaurant("Gallery Place", new BigDecimal("4.0"));
    restaurant.setImageUrls(java.util.List.of("https://example.com/a.jpg"));
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Renamed\"}"))
        .andExpect(status().isOk());

    assertThat(restaurantRepository.findById(restaurant.getId()).orElseThrow().getImageUrls())
        .containsExactly("https://example.com/a.jpg");
  }

  @Test
  void updateRestaurant_blankDescriptionClearsToNull() throws Exception {
    Restaurant restaurant = createRestaurant("Described Place", new BigDecimal("4.0"));
    restaurant.setDescription("Some text");
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"   \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").doesNotExist());

    assertThat(restaurantRepository.findById(restaurant.getId()).orElseThrow().getDescription())
        .isNull();
  }

  @Test
  void updateRestaurant_noFieldsSupplied_stillReturns400() throws Exception {
    Restaurant restaurant = createRestaurant("Some Place", new BigDecimal("4.0"));

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateRestaurant_nonAbsoluteImageUrl_returns400() throws Exception {
    Restaurant restaurant = createRestaurant("Some Place", new BigDecimal("4.0"));

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"imageUrls\": [\"not-a-url\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateRestaurant_invalidPriceLevel_returns400() throws Exception {
    Restaurant restaurant = createRestaurant("Some Place", new BigDecimal("4.0"));

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceLevel\": 0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateRestaurant_unknownIdReturns404() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", java.util.UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateRestaurant_asNonAdmin_returns403() throws Exception {
    Restaurant restaurant = createRestaurant("Some Place", new BigDecimal("4.0"));

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteRestaurant_asAdmin_softDeletesAndLogsModeration() throws Exception {
    Restaurant restaurant = createRestaurant("To Delete", new BigDecimal("4.0"));

    mockMvc
        .perform(
            delete("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Restaurant deleted successfully"));

    assertThat(restaurantRepository.findById(restaurant.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    assertThat(moderationLogRepository.findAll())
        .singleElement()
        .satisfies(log -> assertThat(log.getAction()).isEqualTo("DELETE_RESTAURANT"));
  }

  @Test
  void deleteRestaurant_alreadyDeletedReturns404() throws Exception {
    Restaurant restaurant = createRestaurant("Already Gone", new BigDecimal("4.0"));
    restaurant.setDeletedAt(java.time.Instant.now());
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            delete("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteRestaurant_asNonAdmin_returns403() throws Exception {
    Restaurant restaurant = createRestaurant("Some Place", new BigDecimal("4.0"));

    mockMvc
        .perform(
            delete("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void createRestaurant_withDishIds_attachesAndReturnsNestedSortedByName() throws Exception {
    Dish zebra = createDish("Zebra Noodles", ModerationStatus.APPROVED, null);
    Dish apple = createDish("Apple Salad", ModerationStatus.APPROVED, null);

    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue, District 1",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "dishIds": ["%s", "%s"]
                    }
                    """
                        .formatted(zebra.getId(), apple.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.dishes.length()").value(2))
        .andExpect(jsonPath("$.data.dishes[0].name").value("Apple Salad"))
        .andExpect(jsonPath("$.data.dishes[1].name").value("Zebra Noodles"));
  }

  @Test
  void createRestaurant_withUnknownOrUnapprovedDish_returns404AndPersistsNothing()
      throws Exception {
    Dish pending = createDish("Pending Dish", ModerationStatus.PENDING, null);

    mockMvc
        .perform(
            post("/api/admin/restaurants")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue, District 1",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "dishIds": ["%s"]
                    }
                    """
                        .formatted(pending.getId())))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.message").value("One or more dishes are not found or are not approved."));

    assertThat(restaurantRepository.findAll()).isEmpty();
  }

  @Test
  void updateRestaurant_nonEmptyDishIds_replacesWholeSet() throws Exception {
    Dish oldDish = createDish("Old Dish", ModerationStatus.APPROVED, null);
    Dish newDish = createDish("New Dish", ModerationStatus.APPROVED, null);
    Restaurant restaurant = createRestaurant("Gallery Place", new BigDecimal("4.0"));
    restaurant.setDishes(new HashSet<>(Set.of(oldDish)));
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dishIds\": [\"" + newDish.getId() + "\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dishes.length()").value(1))
        .andExpect(jsonPath("$.data.dishes[0].name").value("New Dish"));

    assertThat(
            restaurantRepository.findById(restaurant.getId()).orElseThrow().getDishes().stream()
                .map(Dish::getId))
        .containsExactly(newDish.getId());
  }

  @Test
  void updateRestaurant_emptyDishIdsArray_clearsAllDishes() throws Exception {
    Dish oldDish = createDish("Old Dish", ModerationStatus.APPROVED, null);
    Restaurant restaurant = createRestaurant("Gallery Place", new BigDecimal("4.0"));
    restaurant.setDishes(new HashSet<>(Set.of(oldDish)));
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dishIds\": []}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.dishes").isArray())
        .andExpect(jsonPath("$.data.dishes").isEmpty());

    assertThat(restaurantRepository.findById(restaurant.getId()).orElseThrow().getDishes())
        .isEmpty();
  }

  @Test
  void updateRestaurant_omittedDishIds_leavesAttachedDishesUnchanged() throws Exception {
    Dish oldDish = createDish("Old Dish", ModerationStatus.APPROVED, null);
    Restaurant restaurant = createRestaurant("Gallery Place", new BigDecimal("4.0"));
    restaurant.setDishes(new HashSet<>(Set.of(oldDish)));
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Renamed\"}"))
        .andExpect(status().isOk());

    assertThat(
            restaurantRepository.findById(restaurant.getId()).orElseThrow().getDishes().stream()
                .map(Dish::getId))
        .containsExactly(oldDish.getId());
  }

  @Test
  void updateRestaurant_withUnknownDish_returns404AndLeavesExistingDishesUntouched()
      throws Exception {
    Dish oldDish = createDish("Old Dish", ModerationStatus.APPROVED, null);
    Dish softDeleted = createDish("Soft Deleted Dish", ModerationStatus.APPROVED, Instant.now());
    Restaurant restaurant = createRestaurant("Gallery Place", new BigDecimal("4.0"));
    restaurant.setDishes(new HashSet<>(Set.of(oldDish)));
    restaurantRepository.save(restaurant);

    mockMvc
        .perform(
            patch("/api/admin/restaurants/{id}", restaurant.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dishIds\": [\"" + softDeleted.getId() + "\"]}"))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.message").value("One or more dishes are not found or are not approved."));

    assertThat(
            restaurantRepository.findById(restaurant.getId()).orElseThrow().getDishes().stream()
                .map(Dish::getId))
        .containsExactly(oldDish.getId());
  }

  private Dish createDish(String name, ModerationStatus status, Instant deletedAt) {
    return dishRepository.save(
        Dish.builder().name(name).moderationStatus(status).deletedAt(deletedAt).build());
  }

  private Restaurant createRestaurant(String name, BigDecimal rating) {
    return restaurantRepository.save(
        Restaurant.builder()
            .name(name)
            .address("123 Vegan St")
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .rating(rating)
            .build());
  }
}
