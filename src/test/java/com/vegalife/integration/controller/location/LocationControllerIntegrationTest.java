package com.vegalife.integration.controller.location;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.post.Location;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.LocationRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
class LocationControllerIntegrationTest {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
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

  @Autowired private LocationRepository locationRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String accessToken;

  @BeforeEach
  void setUp() {
    User user =
        userRepository.save(
            User.builder()
                .username("diner")
                .email("diner@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void listLocations_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/locations")).andExpect(status().isUnauthorized());
  }

  @Test
  void listLocations_noMatchesReturnsEmptyPageWith200() throws Exception {
    mockMvc
        .perform(get("/api/locations").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isEmpty())
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void listLocations_sortsByRatingDescendingAndExcludesDeleted() throws Exception {
    Location highRated = createLocation("Top Pick", new BigDecimal("4.8"), null, null, null);
    Location midRated = createLocation("Mid Tier", new BigDecimal("4.0"), null, null, null);
    createLocation("Soft Deleted", new BigDecimal("5.0"), null, null, Instant.now());

    mockMvc
        .perform(get("/api/locations").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[0].id").value(highRated.getId().toString()))
        .andExpect(jsonPath("$.data.content[1].id").value(midRated.getId().toString()));
  }

  @Test
  void listLocations_responseNeverIncludesTimestamps() throws Exception {
    createLocation("Tofu House", new BigDecimal("4.2"), null, null, null);

    mockMvc
        .perform(get("/api/locations").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].updatedAt").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].deletedAt").doesNotExist());
  }

  @Test
  void listLocations_minRatingFilterExcludesLowerRated() throws Exception {
    Location highRated = createLocation("Top Pick", new BigDecimal("4.8"), null, null, null);
    createLocation("Low Rated", new BigDecimal("3.0"), null, null, null);
    createLocation("No Rating", null, null, null, null);

    mockMvc
        .perform(
            get("/api/locations")
                .header("Authorization", "Bearer " + accessToken)
                .param("minRating", "4.0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(highRated.getId().toString()));
  }

  @Test
  void listLocations_placeTypeFilterNarrowsResults() throws Exception {
    Location cafe = createLocation("Vegan Cafe", null, Location.PlaceType.cafe, null, null);
    createLocation("Vegan Diner", null, Location.PlaceType.restaurant, null, null);

    mockMvc
        .perform(
            get("/api/locations")
                .header("Authorization", "Bearer " + accessToken)
                .param("placeType", "cafe"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(cafe.getId().toString()));
  }

  @Test
  void listLocations_invalidPlaceTypeReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/locations")
                .header("Authorization", "Bearer " + accessToken)
                .param("placeType", "spaceship"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listLocations_maxPriceLevelFilterExcludesPricierLocations() throws Exception {
    Location affordable = createLocation("Budget Eats", null, null, 1, null);
    createLocation("Fancy Place", null, null, 4, null);

    mockMvc
        .perform(
            get("/api/locations")
                .header("Authorization", "Bearer " + accessToken)
                .param("maxPriceLevel", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(affordable.getId().toString()));
  }

  @Test
  void listLocations_openNowExcludesLocationsOutsideHoursOrWithoutAny() throws Exception {
    LocalTime now = LocalTime.now();
    String today = LocalDate.now().getDayOfWeek().toString().toLowerCase(Locale.ROOT);
    Location openNow =
        createLocation(
            "Open Now",
            null,
            null,
            null,
            null,
            openingHoursJson(today, now.minusHours(1), now.plusHours(1)));
    createLocation(
        "Closed Now",
        null,
        null,
        null,
        null,
        openingHoursJson(today, now.plusHours(2), now.plusHours(3)));
    createLocation("No Hours", null, null, null, null, null);

    mockMvc
        .perform(
            get("/api/locations")
                .header("Authorization", "Bearer " + accessToken)
                .param("openNow", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(openNow.getId().toString()));
  }

  @Test
  void listLocations_pageAndSizeAreHonored() throws Exception {
    createLocation("A", new BigDecimal("5.0"), null, null, null);
    createLocation("B", new BigDecimal("4.5"), null, null, null);
    createLocation("C", new BigDecimal("4.0"), null, null, null);

    mockMvc
        .perform(
            get("/api/locations")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2));
  }

  @Test
  void listLocations_combinedFiltersApplyWithAndLogic() throws Exception {
    Location match =
        createLocation("Match", new BigDecimal("4.5"), Location.PlaceType.cafe, 2, null);
    createLocation("WrongType", new BigDecimal("4.5"), Location.PlaceType.restaurant, 2, null);
    createLocation("TooExpensive", new BigDecimal("4.5"), Location.PlaceType.cafe, 4, null);
    createLocation("TooLowRated", new BigDecimal("2.0"), Location.PlaceType.cafe, 2, null);

    mockMvc
        .perform(
            get("/api/locations")
                .header("Authorization", "Bearer " + accessToken)
                .param("minRating", "4.0")
                .param("placeType", "cafe")
                .param("maxPriceLevel", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(match.getId().toString()));
  }

  private Location createLocation(
      String name,
      BigDecimal rating,
      Location.PlaceType placeType,
      Integer priceLevel,
      Instant deletedAt) {
    return createLocation(name, rating, placeType, priceLevel, deletedAt, null);
  }

  private Location createLocation(
      String name,
      BigDecimal rating,
      Location.PlaceType placeType,
      Integer priceLevel,
      Instant deletedAt,
      String openingHours) {
    return locationRepository.save(
        Location.builder()
            .name(name)
            .address("123 Vegan St")
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .placeType(placeType == null ? Location.PlaceType.restaurant : placeType)
            .rating(rating)
            .priceLevel(priceLevel)
            .openingHours(openingHours)
            .deletedAt(deletedAt)
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
