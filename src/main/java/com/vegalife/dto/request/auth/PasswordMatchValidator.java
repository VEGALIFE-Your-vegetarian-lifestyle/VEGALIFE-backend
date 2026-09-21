package com.vegalife.dto.request.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, Object> {

  private String passwordField;
  private String confirmPasswordField;

  @Override
  public void initialize(PasswordMatch constraintAnnotation) {
    this.passwordField = constraintAnnotation.passwordField();
    this.confirmPasswordField = constraintAnnotation.confirmPasswordField();
  }

  @Override
  public boolean isValid(Object value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }

    Object password = new BeanWrapperImpl(value).getPropertyValue(passwordField);
    Object confirmPassword = new BeanWrapperImpl(value).getPropertyValue(confirmPasswordField);

    if (password == null || confirmPassword == null) {
      return true;
    }

    return password.equals(confirmPassword);
  }
}
