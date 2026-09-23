package com.vegalife.shared.exception;

import org.springframework.security.authentication.AuthenticationServiceException;

public class AccountInactiveException extends AuthenticationServiceException {

  public AccountInactiveException(String message) {
    super(message);
  }
}
