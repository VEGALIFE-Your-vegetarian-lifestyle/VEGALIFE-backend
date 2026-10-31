package com.vegalife.integration.controller.location;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.post.PlaceType;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
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
                .role(UserRole.USER)
                .status(UserStatus.activated)
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
  void listLocations_includesGalleryAndDescription() throws Exception {
    Location location = createLocation("Tofu House", new BigDecimal("4.2"), null, null, null);
    location.setImageUrls(java.util.List.of("https://example.com/a.jpg"));
    location.setDescription("Cozy all-vegan spot.");
    locationRepository.save(location);

    mockMvc
        .perform(get("/api/locations").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].imageUrls[0]").value("https://example.com/a.jpg"))
        .andExpect(jsonPath("$.data.content[0].description").value("Cozy all-vegan spot."));
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
    Location cafe = createLocation("Vegan Cafe", null, PlaceType.cafe, null, null);
    createLocation("Vegan Diner", null, PlaceType.restaurant, null, null);

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
  void listNearbyLocations_withoutJwt_returns401() throws Exception {
    mockMvc
        .perform(
            get("/api/locations/nearby")
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "2000"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listNearbyLocations_noMatchesReturnsEmptyArrayWith200() throws Exception {
    mockMvc
        .perform(
            get("/api/locations/nearby")
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
  void listNearbyLocations_returnsOnlyWithinRadiusSortedByDistanceWithComputedField()
      throws Exception {
    // ~620m from the search centre — within a 2000m radius.
    Location near =
        createLocationAt("Near", new BigDecimal("10.777529"), new BigDecimal("106.695528"));
    // ~22km from the search centre — outside a 2000m radius.
    createLocationAt("Far", new BigDecimal("10.972465"), new BigDecimal("106.698055"));

    mockMvc
        .perform(
            get("/api/locations/nearby")
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
  void listNearbyLocations_sortsMultipleMatchesByAscendingDistance() throws Exception {
    Location farther =
        createLocationAt("Farther", new BigDecimal("10.777529"), new BigDecimal("106.695528"));
    Location closer =
        createLocationAt("Closer", new BigDecimal("10.773465"), new BigDecimal("106.698055"));

    mockMvc
        .perform(
            get("/api/locations/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "5000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(closer.getId().toString()))
        .andExpect(jsonPath("$.data[1].id").value(farther.getId().toString()));
  }

  @Test
  void listNearbyLocations_excludesSoftDeletedLocations() throws Exception {
    Location deleted =
        createLocationAt("Deleted", new BigDecimal("10.772465"), new BigDecimal("106.698055"));
    deleted.setDeletedAt(Instant.now());
    locationRepository.save(deleted);

    mockMvc
        .perform(
            get("/api/locations/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "5000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void listNearbyLocations_missingRequiredParamReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/locations/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listNearbyLocations_outOfRangeLatitudeReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/locations/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "95")
                .param("lng", "106.698055")
                .param("radius", "2000"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listNearbyLocations_nonPositiveRadiusReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/locations/nearby")
                .header("Authorization", "Bearer " + accessToken)
                .param("lat", "10.772465")
                .param("lng", "106.698055")
                .param("radius", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listLocations_combinedFiltersApplyWithAndLogic() throws Exception {
    Location match = createLocation("Match", new BigDecimal("4.5"), PlaceType.cafe, 2, null);
    createLocation("WrongType", new BigDecimal("4.5"), PlaceType.restaurant, 2, null);
    createLocation("TooExpensive", new BigDecimal("4.5"), PlaceType.cafe, 4, null);
    createLocation("TooLowRated", new BigDecimal("2.0"), PlaceType.cafe, 2, null);

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
      String name, BigDecimal rating, PlaceType placeType, Integer priceLevel, Instant deletedAt) {
    return createLocation(name, rating, placeType, priceLevel, deletedAt, null);
  }

  private Location createLocation(
      String name,
      BigDecimal rating,
      PlaceType placeType,
      Integer priceLevel,
      Instant deletedAt,
      String openingHours) {
    return locationRepository.save(
        Location.builder()
            .name(name)
            .address("123 Vegan St")
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .placeType(placeType == null ? PlaceType.restaurant : placeType)
            .rating(rating)
            .priceLevel(priceLevel)
            .openingHours(openingHours)
            .deletedAt(deletedAt)
            .build());
  }

  private Location createLocationAt(String name, BigDecimal lat, BigDecimal lng) {
    return locationRepository.save(
        Location.builder()
            .name(name)
            .address("123 Vegan St")
            .latitude(lat)
            .longitude(lng)
            .placeType(PlaceType.restaurant)
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
