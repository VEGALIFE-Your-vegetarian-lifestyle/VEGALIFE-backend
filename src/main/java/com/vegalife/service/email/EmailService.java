package com.vegalife.service.email;

public interface EmailService {

  void sendVerificationEmail(String to, String username, String verificationLink);

  void sendPasswordResetOtp(String to, String username, String otp);
}
