package com.vegalife.integration.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.post.Category;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.Map;
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
class AdminCategoryControllerIntegrationTest {

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

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private ObjectMapper objectMapper;

  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    categoryRepository.deleteAll();

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
  void createCategory_asAdmin_returns201WithTrimmedFields() throws Exception {
    Map<String, String> body =
        Map.of("name", "  Pure Vegan  ", "description", "  Strictly plant-based  ");

    mockMvc
        .perform(
            post("/api/admin/categories")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Category created successfully"))
        .andExpect(jsonPath("$.data.name").value("Pure Vegan"))
        .andExpect(jsonPath("$.data.description").value("Strictly plant-based"))
        .andExpect(jsonPath("$.data.id").exists());
  }

  @Test
  void createCategory_duplicateNameCaseInsensitive_returns409() throws Exception {
    Map<String, String> body = Map.of("name", "Vegan");
    mockMvc
        .perform(
            post("/api/admin/categories")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());

    Map<String, String> duplicate = Map.of("name", "VEGAN");
    mockMvc
        .perform(
            post("/api/admin/categories")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(duplicate)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Category name already exists"));
  }

  @Test
  void createCategory_blankName_returns400() throws Exception {
    Map<String, String> body = Map.of("name", "   ");
    mockMvc
        .perform(
            post("/api/admin/categories")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void createCategory_asNonAdmin_returns403() throws Exception {
    Map<String, String> body = Map.of("name", "Vegan");
    mockMvc
        .perform(
            post("/api/admin/categories")
                .header("Authorization", "Bearer " + userToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }

  @Test
  void createCategory_noToken_returns401() throws Exception {
    Map<String, String> body = Map.of("name", "Vegan");
    mockMvc
        .perform(
            post("/api/admin/categories")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateCategory_asAdmin_returns200WithUpdatedFields() throws Exception {
    Category category =
        categoryRepository.save(Category.builder().name("Vegan").description("Old").build());

    Map<String, String> body = Map.of("name", "  Pure Vegan  ", "description", "  Updated  ");
    mockMvc
        .perform(
            patch("/api/admin/categories/" + category.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Category updated successfully"))
        .andExpect(jsonPath("$.data.name").value("Pure Vegan"))
        .andExpect(jsonPath("$.data.description").value("Updated"));
  }

  @Test
  void updateCategory_partialBody_leavesOmittedFieldUnchanged() throws Exception {
    Category category =
        categoryRepository.save(Category.builder().name("Vegan").description("Old").build());

    Map<String, String> body = Map.of("description", "New description");
    mockMvc
        .perform(
            patch("/api/admin/categories/" + category.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Vegan"))
        .andExpect(jsonPath("$.data.description").value("New description"));
  }

  @Test
  void updateCategory_duplicateNameAmongOtherActiveCategory_returns409() throws Exception {
    categoryRepository.save(Category.builder().name("Dessert").build());
    Category category = categoryRepository.save(Category.builder().name("Vegan").build());

    Map<String, String> body = Map.of("name", "dessert");
    mockMvc
        .perform(
            patch("/api/admin/categories/" + category.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Category name already exists"));
  }

  @Test
  void updateCategory_missingCategory_returns404() throws Exception {
    Map<String, String> body = Map.of("name", "Vegan");
    mockMvc
        .perform(
            patch("/api/admin/categories/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Category not found"));
  }

  @Test
  void updateCategory_softDeletedCategory_returns404() throws Exception {
    Category deleted =
        categoryRepository.save(
            Category.builder().name("Retired").deletedAt(Instant.now()).build());

    Map<String, String> body = Map.of("name", "New Name");
    mockMvc
        .perform(
            patch("/api/admin/categories/" + deleted.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateCategory_emptyBody_returns400() throws Exception {
    Category category = categoryRepository.save(Category.builder().name("Vegan").build());

    mockMvc
        .perform(
            patch("/api/admin/categories/" + category.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateCategory_asNonAdmin_returns403() throws Exception {
    Category category = categoryRepository.save(Category.builder().name("Vegan").build());

    Map<String, String> body = Map.of("name", "New Name");
    mockMvc
        .perform(
            patch("/api/admin/categories/" + category.getId())
                .header("Authorization", "Bearer " + userToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateCategory_noToken_returns401() throws Exception {
    Category category = categoryRepository.save(Category.builder().name("Vegan").build());

    Map<String, String> body = Map.of("name", "New Name");
    mockMvc
        .perform(
            patch("/api/admin/categories/" + category.getId())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnauthorized());
  }
}
