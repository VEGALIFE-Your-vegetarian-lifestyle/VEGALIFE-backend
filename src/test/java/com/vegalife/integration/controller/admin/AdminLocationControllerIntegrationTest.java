package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.post.Location;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.LocationRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.math.BigDecimal;
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
class AdminLocationControllerIntegrationTest {

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

  @Autowired private ModerationLogRepository moderationLogRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    User admin =
        userRepository.save(
            User.builder()
                .username("adminuser")
                .email("admin@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.ADMIN)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    User regular =
        userRepository.save(
            User.builder()
                .username("regularuser")
                .email("user@example.com")
                .passwordHash("$2a$10$test")
                .role(User.Role.USER)
                .status(User.Status.activated)
                .emailVerified(true)
                .build());
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(regular);
  }

  @Test
  void createLocation_asAdmin_returns201WithIdAndTimestamps() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/locations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue, District 1",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "placeType": "restaurant"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Location created successfully"))
        .andExpect(jsonPath("$.data.id").exists())
        .andExpect(jsonPath("$.data.name").value("Vegan Garden"))
        .andExpect(jsonPath("$.data.placeType").value("restaurant"))
        .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

    assertThat(moderationLogRepository.findAll())
        .singleElement()
        .satisfies(log -> assertThat(log.getAction()).isEqualTo("CREATE_LOCATION"));
  }

  @Test
  void createLocation_invalidPlaceType_returns400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/locations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "placeType": "spaceship"
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(locationRepository.findAll()).isEmpty();
  }

  @Test
  void createLocation_priceLevelOutOfRange_returns400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/locations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "placeType": "restaurant",
                      "priceLevel": 5
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(locationRepository.findAll()).isEmpty();
  }

  @Test
  void createLocation_outOfRangeCoordinates_returns400AndPersistsNothing() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/locations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 95,
                      "longitude": 106.700806,
                      "placeType": "restaurant"
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertThat(locationRepository.findAll()).isEmpty();
  }

  @Test
  void createLocation_asNonAdmin_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/locations")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "placeType": "restaurant"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void createLocation_noToken_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Vegan Garden",
                      "address": "123 Nguyen Hue",
                      "latitude": 10.776889,
                      "longitude": 106.700806,
                      "placeType": "restaurant"
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateLocation_asAdmin_changesOnlySuppliedFieldsAndRefreshesUpdatedAt() throws Exception {
    Location location = createLocation("Old Name", new BigDecimal("4.0"));

    mockMvc
        .perform(
            patch("/api/admin/locations/{id}", location.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Location updated successfully"))
        .andExpect(jsonPath("$.data.name").value("New Name"))
        .andExpect(jsonPath("$.data.address").value("123 Vegan St"));

    assertThat(moderationLogRepository.findAll())
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getAction()).isEqualTo("UPDATE_LOCATION");
              assertThat(log.getTargetId()).isEqualTo(location.getId());
            });
  }

  @Test
  void updateLocation_invalidPriceLevel_returns400() throws Exception {
    Location location = createLocation("Some Place", new BigDecimal("4.0"));

    mockMvc
        .perform(
            patch("/api/admin/locations/{id}", location.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceLevel\": 0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateLocation_unknownIdReturns404() throws Exception {
    mockMvc
        .perform(
            patch("/api/admin/locations/{id}", java.util.UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateLocation_asNonAdmin_returns403() throws Exception {
    Location location = createLocation("Some Place", new BigDecimal("4.0"));

    mockMvc
        .perform(
            patch("/api/admin/locations/{id}", location.getId())
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteLocation_asAdmin_softDeletesAndLogsModeration() throws Exception {
    Location location = createLocation("To Delete", new BigDecimal("4.0"));

    mockMvc
        .perform(
            delete("/api/admin/locations/{id}", location.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Location deleted successfully"));

    assertThat(locationRepository.findById(location.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    assertThat(moderationLogRepository.findAll())
        .singleElement()
        .satisfies(log -> assertThat(log.getAction()).isEqualTo("DELETE_LOCATION"));
  }

  @Test
  void deleteLocation_alreadyDeletedReturns404() throws Exception {
    Location location = createLocation("Already Gone", new BigDecimal("4.0"));
    location.setDeletedAt(java.time.Instant.now());
    locationRepository.save(location);

    mockMvc
        .perform(
            delete("/api/admin/locations/{id}", location.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteLocation_asNonAdmin_returns403() throws Exception {
    Location location = createLocation("Some Place", new BigDecimal("4.0"));

    mockMvc
        .perform(
            delete("/api/admin/locations/{id}", location.getId())
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  private Location createLocation(String name, BigDecimal rating) {
    return locationRepository.save(
        Location.builder()
            .name(name)
            .address("123 Vegan St")
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .placeType(Location.PlaceType.restaurant)
            .rating(rating)
            .build());
  }
}
