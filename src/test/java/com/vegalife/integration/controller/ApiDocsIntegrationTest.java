package com.vegalife.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
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

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping requestMappingHandlerMapping;

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

  /**
   * Every query parameter the controllers actually bind must be discoverable in Swagger. The list
   * and search endpoints bind their filters through {@code @ModelAttribute} DTOs, so this walks the
   * real handler mappings and checks each mapped operation against the served document instead of
   * hard-coding a few endpoints.
   */
  @Test
  void everyQueryDtoFieldAppearsAsAQueryParameterInTheApiDoc() throws Exception {
    JsonNode paths = readApiDocs().path("paths");
    List<String> problems = new ArrayList<>();
    int endpoints = 0;

    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (!handler.getBeanType().getPackageName().startsWith("com.vegalife.controller")) {
        continue;
      }

      Set<String> explicitQueryParams = new LinkedHashSet<>();
      for (MethodParameter parameter : handler.getMethodParameters()) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
          explicitQueryParams.add(
              requestParam.name().isEmpty() ? requestParam.value() : requestParam.name());
        }
      }

      for (MethodParameter parameter : handler.getMethodParameters()) {
        if (!parameter.hasParameterAnnotation(ModelAttribute.class)) {
          continue;
        }
        Class<?> dtoType = parameter.getParameterType();
        List<String> fields = instanceFieldNames(dtoType);
        Set<String> expectedQueryParams = new LinkedHashSet<>(fields);
        expectedQueryParams.addAll(explicitQueryParams);

        for (String path : entry.getKey().getPatternValues()) {
          for (RequestMethod method : entry.getKey().getMethodsCondition().getMethods()) {
            endpoints++;
            String operationId = method + " " + path;
            JsonNode operation = paths.path(path).path(method.name().toLowerCase(Locale.ROOT));
            if (operation.isMissingNode()) {
              problems.add(operationId + ": operation is missing from /v3/api-docs");
              continue;
            }

            Set<String> documentedQueryParams = new TreeSet<>();
            operation
                .path("parameters")
                .forEach(
                    p -> {
                      if ("query".equals(p.path("in").asText())) {
                        documentedQueryParams.add(p.path("name").asText());
                      }
                    });

            for (String field : fields) {
              if (!documentedQueryParams.contains(field)) {
                problems.add(
                    operationId
                        + ": field '"
                        + field
                        + "' of "
                        + dtoType.getSimpleName()
                        + " is not documented as a query parameter");
              }
            }
            for (String documented : documentedQueryParams) {
              if (!expectedQueryParams.contains(documented)) {
                problems.add(
                    operationId
                        + ": documented query parameter '"
                        + documented
                        + "' is not a bindable field of "
                        + dtoType.getSimpleName());
              }
            }
          }
        }
      }
    }

    assertThat(endpoints)
        .as("operations bound through @ModelAttribute request DTOs")
        .isGreaterThanOrEqualTo(25);
    assertThat(problems).as("OpenAPI query parameter mismatches").isEmpty();
  }

  private JsonNode readApiDocs() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new ObjectMapper().readTree(body);
  }

  /**
   * The bindable state of a request DTO: its declared instance fields, including inherited ones.
   */
  private static List<String> instanceFieldNames(Class<?> type) {
    List<String> names = new ArrayList<>();
    Class<?> current = type;
    while (current != null && current != Object.class) {
      for (Field field : current.getDeclaredFields()) {
        int modifiers = field.getModifiers();
        if (Modifier.isStatic(modifiers)
            || Modifier.isTransient(modifiers)
            || field.isSynthetic()) {
          continue;
        }
        names.add(field.getName());
      }
      current = current.getSuperclass();
    }
    return names;
  }
}
