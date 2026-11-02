package com.vegalife.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI index order.
 *
 * <p>SpringDoc names a tag after the controller — {@code DishController} → {@code dish-controller}
 * — and emits one per operation, with no top-level {@code tags} array. Swagger UI then builds its
 * index by first-seen order while walking {@code paths}, which follows the classpath scanner's
 * order and is neither alphabetical nor stable.
 *
 * <p>This customizer collects every operation's tag into that missing top-level array, sorted
 * alphabetically, so the index order becomes deterministic. Each operation carries exactly one tag,
 * so per-operation lists are only sorted when an operation is tagged with several surfaces.
 *
 * <p>The tag list is derived from the document rather than maintained by hand, so renaming or
 * adding a controller cannot make it drift out of date. {@code springdoc.swagger-ui.tags-sorter} is
 * deliberately not relied on: client-side sorting hides the disorder instead of fixing the document
 * other consumers of {@code /v3/api-docs} read.
 *
 * <p>Operations inside a tag are ordered by HTTP method by {@code
 * springdoc.swagger-ui.operations-sorter}.
 */
@Configuration
public class OpenApiTagOrderConfig {

  @Bean
  public OpenApiCustomizer alphabeticalTagOrder() {
    return OpenApiTagOrderConfig::sortTags;
  }

  static void sortTags(OpenAPI openApi) {
    Set<String> seen = collectOperationTags(openApi);

    Paths paths = openApi.getPaths();
    if (paths != null) {
      for (PathItem pathItem : paths.values()) {
        for (Operation operation : pathItem.readOperations()) {
          sortOperationTags(operation);
        }
      }
    }

    if (seen.isEmpty()) {
      return;
    }

    List<Tag> existing = openApi.getTags();
    List<Tag> merged = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
    Set<String> names = new LinkedHashSet<>();
    for (Tag tag : merged) {
      names.add(tag.getName());
    }
    for (String name : seen) {
      if (names.add(name)) {
        merged.add(new Tag().name(name));
      }
    }
    merged.sort(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER));
    openApi.setTags(merged);
  }

  private static Set<String> collectOperationTags(OpenAPI openApi) {
    Set<String> seen = new LinkedHashSet<>();
    Paths paths = openApi.getPaths();
    if (paths == null) {
      return seen;
    }
    for (PathItem pathItem : paths.values()) {
      for (Operation operation : pathItem.readOperations()) {
        if (operation.getTags() != null) {
          seen.addAll(operation.getTags());
        }
      }
    }
    return seen;
  }

  private static void sortOperationTags(Operation operation) {
    List<String> tags = operation.getTags();
    if (tags == null || tags.size() < 2) {
      return;
    }
    List<String> sorted = new ArrayList<>(tags);
    sorted.sort(Comparator.naturalOrder());
    operation.setTags(sorted);
  }
}
