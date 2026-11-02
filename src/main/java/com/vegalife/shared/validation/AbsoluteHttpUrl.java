package com.vegalife.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a {@code String} field (null/blank allowed) or every element of a {@code
 * List<String>} field (null entries/blank entries rejected, a null or empty list allowed) is an
 * absolute {@code http://} or {@code https://} URL. Shared by {@code websiteUrl} and {@code
 * imageUrls} on location create/update requests (issue #159).
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(
    validatedBy = {AbsoluteHttpUrlStringValidator.class, AbsoluteHttpUrlListValidator.class})
@Documented
public @interface AbsoluteHttpUrl {
  String message() default "must be an absolute http or https URL";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
