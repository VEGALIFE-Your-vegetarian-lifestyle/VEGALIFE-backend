package com.vegalife.service.email;

import com.vegalife.service.outbound.OutboundEmailPayload;

public interface EmailService {

  void sendVerificationOtp(String to, String username, String otp);

  void sendPasswordResetOtp(String to, String username, String otp);

  void sendPaymentReceipt(String to, String username, OutboundEmailPayload.Receipt receipt);
}
