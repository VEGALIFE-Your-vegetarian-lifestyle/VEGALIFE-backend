package com.vegalife.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FieldsEqualValidator.class)
@Documented
public @interface FieldsEqual {
  String message() default "Fields must be equal";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  String[] value();
}