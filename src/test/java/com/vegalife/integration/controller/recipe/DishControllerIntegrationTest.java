package com.vegalife.integration.controller.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.recipe.Dish;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.List;
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
class DishControllerIntegrationTest {

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

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private DishRepository dishRepository;

  private String accessToken;

  @BeforeEach
  void setUp() {
    dishRepository.deleteAll();
    User testUser =
        User.builder()
            .username("dishuser")
            .email("dish@example.com")
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    testUser = userRepository.save(testUser);
    accessToken = jwtTokenService.generateAccessToken(testUser);
  }

  @Test
  void listDishes_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/dishes")).andExpect(status().isUnauthorized());
  }

  @Test
  void listDishes_withToken_returns200DefaultsExcludingSoftDeletedSortedByName() throws Exception {
    dishRepository.save(Dish.builder().name("tofu stir-fry").build());
    dishRepository.save(Dish.builder().name("banh mi chay").build());
    dishRepository.save(Dish.builder().name("old dish").deletedAt(Instant.now()).build());

    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Dishes retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.first").value(true))
        .andExpect(jsonPath("$.data.last").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].name").value("banh mi chay"))
        .andExpect(jsonPath("$.data.content[1].name").value("tofu stir-fry"))
        .andExpect(jsonPath("$.data.content[0].id").isNotEmpty());

    assertThat(dishRepository.count()).isEqualTo(3);
  }

  @Test
  void listDishes_nameFilter_returnsOnlyMatchingDishesCaseInsensitively() throws Exception {
    dishRepository.save(Dish.builder().name("pho chay").build());
    dishRepository.save(Dish.builder().name("tofu stir-fry").build());

    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + accessToken)
                .param("name", "PHO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("pho chay"));
  }

  @Test
  void listDishes_pageAndSize_returnsRequestedPage() throws Exception {
    dishRepository.save(Dish.builder().name("aaa").build());
    dishRepository.save(Dish.builder().name("bbb").build());
    dishRepository.save(Dish.builder().name("ccc").build());

    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + accessToken)
                .param("page", "1")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.first").value(false))
        .andExpect(jsonPath("$.data.last").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("ccc"));
  }

  @Test
  void listDishes_negativePage_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/dishes").header("Authorization", "Bearer " + accessToken).param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listDishes_sizeBelowMinimum_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/dishes").header("Authorization", "Bearer " + accessToken).param("size", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listDishes_sizeAboveMaximum_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/dishes")
                .header("Authorization", "Bearer " + accessToken)
                .param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listDishes_emptyTable_returns200WithEmptyContent() throws Exception {
    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.totalPages").value(0));
  }

  @Test
  void listDishes_returnsPersistedIdForRecipeLookupRoundTrip() throws Exception {
    Dish saved =
        dishRepository.save(Dish.builder().name("pho chay").description("Noodle soup").build());

    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].id").value(saved.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].name").value("pho chay"))
        .andExpect(jsonPath("$.data.content[0].description").value("Noodle soup"));
  }

  @Test
  void listDishes_performsNoWrites() throws Exception {
    dishRepository.save(Dish.builder().name("pho chay").build());
    dishRepository.save(Dish.builder().name("tofu stir-fry").build());
    long before = dishRepository.count();

    mockMvc
        .perform(get("/api/dishes").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    List<Dish> dishes = dishRepository.findAll();
    assertThat(dishes).hasSize((int) before);
    assertThat(dishes).allMatch(dish -> dish.getDeletedAt() == null);
  }
}
