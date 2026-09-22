package com.vegalife.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Field;

public class FieldsEqualValidator implements ConstraintValidator<FieldsEqual, Object> {

  private String[] fields;

  @Override
  public void initialize(FieldsEqual constraintAnnotation) {
    this.fields = constraintAnnotation.value();
  }

  @Override
  public boolean isValid(Object value, ConstraintValidatorContext context) {
    if (fields == null || fields.length < 2) {
      return true;
    }

    try {
      Object firstValue = null;
      for (int i = 0; i < fields.length; i++) {
        Field field = value.getClass().getDeclaredField(fields[i]);
        field.setAccessible(true);
        Object fieldValue = field.get(value);

        if (i == 0) {
          firstValue = fieldValue;
        } else {
          if (firstValue == null && fieldValue == null) {
            continue;
          }
          if (firstValue == null || !firstValue.equals(fieldValue)) {
            return false;
          }
        }
      }
      return true;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      return false;
    }
  }
}
