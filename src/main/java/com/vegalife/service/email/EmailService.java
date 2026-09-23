package com.vegalife.service.email;

public interface EmailService {

  void sendVerificationEmail(String to, String username, String verificationLink);

  void sendVerificationOtp(String to, String username, String otp);

  void sendPasswordResetOtp(String to, String username, String otp);
}
