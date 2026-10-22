package com.vegalife.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AbsoluteHttpUrlStringValidator
    implements ConstraintValidator<AbsoluteHttpUrl, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || value.isBlank() || UrlValidation.isAbsoluteHttpUrl(value);
  }
}
