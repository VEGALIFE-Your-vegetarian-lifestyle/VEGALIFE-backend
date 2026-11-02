package com.vegalife.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiDocsIntegrationTest {

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

  @Test
  void v3ApiDocsIsServedWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").exists())
        .andExpect(jsonPath("$.info").exists())
        .andExpect(jsonPath("$.paths").exists());
  }

  @Test
  void swaggerUiRedirectIsWhitelisted() throws Exception {
    mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
  }

  @Test
  void openApiDeclaresBearerSecurityScheme() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
  }

  @Test
  void protectedOperationsDeclareBearerRequirement() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/posts'].get.security[0].bearerAuth").exists())
        .andExpect(
            jsonPath("$.paths['/api/posts/{postId}/comments'].post.security[0].bearerAuth")
                .exists())
        .andExpect(jsonPath("$.paths['/api/profile'].put.security[0].bearerAuth").exists())
        .andExpect(jsonPath("$.paths['/api/admin/users'].get.security[0].bearerAuth").exists())
        .andExpect(jsonPath("$.paths['/api/admin/posts'].get.security[0].bearerAuth").exists())
        .andExpect(
            jsonPath("$.paths['/api/admin/posts/{postId}'].get.security[0].bearerAuth").exists())
        .andExpect(
            jsonPath("$.paths['/api/admin/posts/{postId}/approve'].post.security[0].bearerAuth")
                .exists())
        .andExpect(
            jsonPath("$.paths['/api/admin/posts/{postId}/reject'].post.security[0].bearerAuth")
                .exists());
  }

  @Test
  void publicOperationsDeclareNoSecurityRequirement() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.security").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/categories'].get.security").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist())
        .andExpect(jsonPath("$.paths['/api/posts/{postId}'].get.security").doesNotExist());
  }

  /**
   * Swagger UI builds its index from the document's top-level {@code tags} array, and SpringDoc
   * emits none — it only tags each operation, leaving the UI to order groups by first-seen order
   * while walking {@code paths}, which follows the classpath scanner. {@code OpenApiTagOrderConfig}
   * fills that array in, sorted; asserting it here proves the fix reaches the served document
   * rather than only the customizer in isolation.
   */
  @Test
  void apiDocsDeclaresATopLevelTagsListInAlphabeticalOrder() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = new ObjectMapper().readTree(body);
    JsonNode tags = root.path("tags");
    assertThat(tags.isArray()).as("the document declares a top-level tags array").isTrue();

    List<String> names = new ArrayList<>();
    tags.forEach(tag -> names.add(tag.path("name").asText()));

    List<String> sorted = new ArrayList<>(names);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);

    assertThat(names).as("tag groups as Swagger UI renders them: %s", names).isNotEmpty();
    assertThat(names).as("tags are alphabetical").containsExactlyElementsOf(sorted);

    // Every operation's tag must be declared, or that group would be missing from the index.
    List<String> operationTags = new ArrayList<>();
    JsonNode paths = root.path("paths");
    var pathNames = paths.fieldNames();
    while (pathNames.hasNext()) {
      String path = pathNames.next();
      var methods = paths.path(path).fieldNames();
      while (methods.hasNext()) {
        paths
            .path(path)
            .path(methods.next())
            .path("tags")
            .forEach(t -> operationTags.add(t.asText()));
      }
    }
    assertThat(operationTags).as("operations carry tags").isNotEmpty();
    assertThat(names)
        .as("every operation tag is declared in the index")
        .containsAll(new java.util.LinkedHashSet<>(operationTags));
  }
}
