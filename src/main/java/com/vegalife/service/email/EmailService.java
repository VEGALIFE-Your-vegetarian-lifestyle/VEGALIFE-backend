package com.vegalife.service.email;

import com.vegalife.service.outbound.OutboundEmailPayload;

public interface EmailService {

  void sendVerificationOtp(String to, String displayName, String otp);

  void sendPasswordResetOtp(String to, String displayName, String otp);

  void sendPaymentReceipt(String to, String displayName, OutboundEmailPayload.Receipt receipt);
}
