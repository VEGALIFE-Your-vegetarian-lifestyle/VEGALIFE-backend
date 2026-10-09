package com.vegalife.unit.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guards the model in ADR-009 (Option 2): protected endpoints declare their rule with
 * {@code @PreAuthorize}, and public endpoints are {@code permitAll} path rules in {@code
 * SecurityConfig}.
 *
 * <p>This test fails the build when a controller handler is neither annotated nor on the explicit
 * public allowlist below, so adding an endpoint forces a conscious access decision rather than an
 * accidental one. The public allowlist must be kept in sync with the {@code permitAll} rules in
 * {@code SecurityConfig}.
 */
class EndpointAuthorizationGuardTest {

  private static final Set<String> MAPPING_ANNOTATIONS =
      Set.of(
          "RequestMapping",
          "GetMapping",
          "PostMapping",
          "PutMapping",
          "PatchMapping",
          "DeleteMapping");

  /**
   * Public controllers whose every handler is a {@code permitAll} path rule. Keep in sync with
   * {@code SecurityConfig}.
   */
  private static final Set<String> PUBLIC_CONTROLLERS =
      Set.of("AuthController", "UserPostController", "CategoryController", "IngredientController");

  /**
   * Public handlers inside otherwise-protected controllers. Keep in sync with {@code
   * SecurityConfig}.
   */
  private static final Set<String> PUBLIC_HANDLERS =
      Set.of(
          "PostController#getPost",
          "PostController#listFeed",
          "SubscriptionController#getAvailablePlans",
          "ProfileController#getProfileByUserId",
          "PaymentWebhookController#ipnGet",
          "PaymentWebhookController#ipnPost",
          "DishController#listDishes");

  @Test
  void everyControllerHandlerIsEitherPublicOrAnnotated() {
    List<String> unclassified = new ArrayList<>();

    for (Class<?> controller : controllerClasses()) {
      boolean classAnnotated = controller.isAnnotationPresent(PreAuthorize.class);
      boolean publicController = PUBLIC_CONTROLLERS.contains(controller.getSimpleName());
      for (Method method : controller.getDeclaredMethods()) {
        if (!isHandler(method) || classAnnotated || publicController) {
          continue;
        }
        String id = controller.getSimpleName() + "#" + method.getName();
        if (!method.isAnnotationPresent(PreAuthorize.class) && !PUBLIC_HANDLERS.contains(id)) {
          unclassified.add(id);
        }
      }
    }

    assertThat(unclassified)
        .as(
            "Every handler must be either a permitAll path rule (listed in SecurityConfig and in "
                + "this test's allowlist) or carry @PreAuthorize — see ADR-009. Unlisted: %s",
            unclassified)
        .isEmpty();
  }

  private static boolean isHandler(Method method) {
    return java.util.Arrays.stream(method.getAnnotations())
        .anyMatch(a -> MAPPING_ANNOTATIONS.contains(a.annotationType().getSimpleName()));
  }

  private static List<Class<?>> controllerClasses() {
    var provider =
        new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(
            false);
    provider.addIncludeFilter(
        new org.springframework.core.type.filter.AnnotationTypeFilter(RestController.class));
    provider.addIncludeFilter(
        new org.springframework.core.type.filter.AnnotationTypeFilter(Controller.class));

    List<Class<?>> controllers = new ArrayList<>();
    for (var candidate : provider.findCandidateComponents("com.vegalife.controller")) {
      try {
        controllers.add(Class.forName(candidate.getBeanClassName()));
      } catch (ClassNotFoundException ex) {
        throw new IllegalStateException(ex);
      }
    }
    return controllers;
  }
}
