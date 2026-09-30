package com.vegalife.integration.controller.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.post.Category;
import com.vegalife.repository.post.CategoryRepository;
import java.time.Instant;
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
class CategoryControllerIntegrationTest {

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

  @Autowired private CategoryRepository categoryRepository;

  @BeforeEach
  void setUp() {
    categoryRepository.deleteAll();
  }

  @Test
  void listCategories_noToken_returns200WithActiveCategoriesSortedByNamePaginated()
      throws Exception {
    categoryRepository.save(Category.builder().name("Vegan").description("Plant-based").build());
    categoryRepository.save(Category.builder().name("Dessert").build());
    categoryRepository.save(Category.builder().name("Retired").deletedAt(Instant.now()).build());

    mockMvc
        .perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Categories retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].name").value("Dessert"))
        .andExpect(jsonPath("$.data.content[1].name").value("Vegan"))
        .andExpect(jsonPath("$.data.content[1].description").value("Plant-based"));
  }

  @Test
  void listCategories_noCategories_returns200WithEmptyContent() throws Exception {
    mockMvc
        .perform(get("/api/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content.length()").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(0));
  }

  @Test
  void listCategories_nameFilter_returnsOnlyMatchingCategoriesCaseInsensitively() throws Exception {
    categoryRepository.save(Category.builder().name("Pure Vegan").build());
    categoryRepository.save(Category.builder().name("High-Protein Vegan").build());
    categoryRepository.save(Category.builder().name("Dessert").build());

    mockMvc
        .perform(get("/api/categories").param("name", "VEGAN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[0].name").value("High-Protein Vegan"))
        .andExpect(jsonPath("$.data.content[1].name").value("Pure Vegan"));
  }

  @Test
  void listCategories_pageAndSize_returnsRequestedPage() throws Exception {
    categoryRepository.save(Category.builder().name("Breakfast").build());
    categoryRepository.save(Category.builder().name("Dessert").build());
    categoryRepository.save(Category.builder().name("Dinner").build());

    mockMvc
        .perform(get("/api/categories").param("page", "1").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].name").value("Dinner"));
  }

  @Test
  void listCategories_invalidSize_returns400() throws Exception {
    mockMvc.perform(get("/api/categories").param("size", "0")).andExpect(status().isBadRequest());
  }
}
