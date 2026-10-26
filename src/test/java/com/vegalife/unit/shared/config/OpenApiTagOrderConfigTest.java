package com.vegalife.unit.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.shared.config.OpenApiTagOrderConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * Swagger UI orders its index from the document's top-level {@code tags} array. SpringDoc emits
 * none and tags each operation instead, so the UI falls back to first-seen order over {@code paths}
 * — the classpath scanner's order. These tests pin the customizer's two jobs: inventing that array
 * in alphabetical order, and leaving whatever SpringDoc did emit in place rather than overwriting
 * it.
 */
class OpenApiTagOrderConfigTest {

  private final OpenApiCustomizer customizer = new OpenApiTagOrderConfig().alphabeticalTagOrder();

  @Test
  void operationTagsAreCollectedIntoASortedTopLevelTagList() {
    OpenAPI openApi =
        document(
            path(operation("vote-controller")),
            path(operation("post-controller")),
            path(operation("dish-controller")));

    customizer.customise(openApi);

    assertThat(namesOf(openApi))
        .as("the index groups are alphabetical")
        .containsExactly("dish-controller", "post-controller", "vote-controller");
  }

  @Test
  void tagsSpringDocAlreadyDeclaredAreKeptNotReplaced() {
    OpenAPI openApi = document(path(operation("vote-controller")));
    openApi.addTagsItem(new Tag().name("admin-controller").description("kept description"));

    customizer.customise(openApi);

    assertThat(namesOf(openApi))
        .as("existing entries are merged, not dropped")
        .containsExactly("admin-controller", "vote-controller");
    assertThat(openApi.getTags().get(0).getDescription())
        .as("a description SpringDoc set survives the sort")
        .isEqualTo("kept description");
  }

  @Test
  void aTagDeclaredWithoutAnyOperationStillAppears() {
    OpenAPI openApi = document(path(operation("vote-controller")));
    openApi.addTagsItem(new Tag().name("retired-controller"));

    customizer.customise(openApi);

    assertThat(namesOf(openApi))
        .as("a declared-but-unused tag is not silently deleted")
        .containsExactly("retired-controller", "vote-controller");
  }

  @Test
  void operationsTaggedWithSeveralSurfacesAreSortedWithinTheOperation() {
    Operation operation = operation("vote-controller", "admin-controller");
    OpenAPI openApi = document(path(operation));

    customizer.customise(openApi);

    assertThat(operation.getTags())
        .as("an operation carrying two tags groups alphabetically within itself")
        .containsExactly("admin-controller", "vote-controller");
    assertThat(namesOf(openApi))
        .as("both tags are declared once each")
        .containsExactly("admin-controller", "vote-controller");
  }

  @Test
  void documentsWithoutOperationsDoNotGainATagList() {
    OpenAPI openApi = new OpenAPI();

    customizer.customise(openApi);

    assertThat(openApi.getTags())
        .as("inventing tags for an empty document would invent phantom index groups")
        .isNull();
    assertThat(openApi.getPaths()).isNull();
  }

  @Test
  void sortingIsCaseInsensitiveSoMixedCaseTagsStillReadAlphabetically() {
    OpenAPI openApi =
        document(path(operation("Vote")), path(operation("admin")), path(operation("auth")));

    customizer.customise(openApi);

    assertThat(namesOf(openApi)).containsExactly("admin", "auth", "Vote");
  }

  private static Operation operation(String... tags) {
    return new Operation().tags(new ArrayList<>(List.of(tags)));
  }

  private static PathItem path(Operation operation) {
    return new PathItem().get(operation);
  }

  private static OpenAPI document(PathItem... pathItems) {
    Paths paths = new Paths();
    int index = 0;
    for (PathItem pathItem : pathItems) {
      paths.addPathItem("/op" + index++, pathItem);
    }
    return new OpenAPI().paths(paths);
  }

  private static List<String> namesOf(OpenAPI openApi) {
    List<String> names = new ArrayList<>();
    openApi.getTags().forEach(tag -> names.add(tag.getName()));
    return names;
  }
}
