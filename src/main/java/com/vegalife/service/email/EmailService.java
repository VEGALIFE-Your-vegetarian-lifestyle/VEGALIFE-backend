package com.vegalife.service.email;

public interface EmailService {

  void sendVerificationOtp(String to, String username, String otp);

  void sendPasswordResetOtp(String to, String username, String otp);
}
