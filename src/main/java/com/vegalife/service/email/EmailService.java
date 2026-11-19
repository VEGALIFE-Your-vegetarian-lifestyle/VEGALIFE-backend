package com.vegalife.service.email;

public interface EmailService {

  void sendVerificationOtp(String to, String displayName, String otp);

  void sendPasswordResetOtp(String to, String displayName, String otp);

  void sendPaymentReceipt(String to, String displayName, EmailPayload.Receipt receipt);
}
