package com.vegalife.unit.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guards the OpenAPI side of ADR-009: a controller that declares {@code @PreAuthorize} must also
 * declare {@code @SecurityRequirement(bearerAuth)}, otherwise the endpoint returns 401 in
 * production while Swagger renders it lock-free and sends no token.
 *
 * <p>OpenApiConfig registers the {@code bearerAuth} scheme but does not apply it globally, so the
 * requirement has to travel with each protected controller. That makes the two annotations easy to
 * drift apart, which is exactly how {@code DishController} and {@code IngredientController} ended
 * up authenticated at runtime and public-looking in the docs.
 *
 * <p>Handlers covered by a {@code permitAll} path rule carry no {@code @PreAuthorize} and are
 * therefore out of scope here; their absence from OpenAPI is correct.
 */
class OpenApiSecurityRequirementTest {

  private static final Set<String> MAPPING_ANNOTATIONS =
      Set.of(
          "RequestMapping",
          "GetMapping",
          "PostMapping",
          "PutMapping",
          "PatchMapping",
          "DeleteMapping");

  @Test
  void everyProtectedHandlerDeclaresTheBearerRequirement() {
    List<String> missing = new ArrayList<>();

    for (Class<?> controller : controllerClasses()) {
      boolean controllerProtected = declares(controller, PreAuthorize.class);
      for (Method method : controller.getDeclaredMethods()) {
        if (!isHandler(method)) {
          continue;
        }
        boolean methodProtected = declares(method, PreAuthorize.class);
        if (!controllerProtected && !methodProtected) {
          continue;
        }
        if (!declaresBearer(controller) && !declaresBearer(method)) {
          missing.add(controller.getSimpleName() + "#" + method.getName());
        }
      }
    }

    assertThat(missing)
        .as(
            "Handlers guarded by @PreAuthorize must also declare"
                + " @SecurityRequirement(name = OpenApiSchemeNames.BEARER_AUTH) so Swagger sends the"
                + " token — see ADR-009. Missing: %s",
            missing)
        .isEmpty();
  }

  private static boolean isHandler(Method method) {
    for (var annotation : method.getAnnotations()) {
      if (MAPPING_ANNOTATIONS.contains(annotation.annotationType().getSimpleName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean declares(
      AnnotatedElement element, Class<? extends java.lang.annotation.Annotation> type) {
    return AnnotatedElementUtils.hasAnnotation(element, type);
  }

  private static boolean declaresBearer(AnnotatedElement element) {
    for (SecurityRequirement requirement :
        element.getAnnotationsByType(SecurityRequirement.class)) {
      if (OpenApiSchemeNames.BEARER_AUTH.equals(requirement.name())) {
        return true;
      }
    }
    return false;
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
