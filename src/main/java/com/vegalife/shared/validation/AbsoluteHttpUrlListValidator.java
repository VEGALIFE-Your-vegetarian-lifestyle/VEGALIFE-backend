package com.vegalife.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class AbsoluteHttpUrlListValidator
    implements ConstraintValidator<AbsoluteHttpUrl, List<String>> {

  @Override
  public boolean isValid(List<String> value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return value.stream()
        .allMatch(
            entry -> entry != null && !entry.isBlank() && UrlValidation.isAbsoluteHttpUrl(entry));
  }
}
