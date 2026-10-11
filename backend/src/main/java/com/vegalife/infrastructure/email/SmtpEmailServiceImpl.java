package com.vegalife.infrastructure.email;

import com.vegalife.service.email.EmailService;
import com.vegalife.service.outbound.OutboundEmailPayload;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailServiceImpl implements EmailService {

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;

  @Value("${app.password-reset.otp-expiry-minutes:10}")
  private int otpExpiryMinutes;

  @Value("${app.email-verification.otp-expiry-minutes:10}")
  private int emailVerificationOtpExpiryMinutes;

  @Value("${app.mail.from:}")
  private String fromAddress;

  @Override
  public void sendVerificationOtp(String to, String username, String otp) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      applyFrom(helper);

      helper.setTo(to);
      helper.setSubject("Verify your email - Vegalife");

      Context context = new Context();
      context.setVariable("username", username);
      context.setVariable("otp", otp);
      context.setVariable("otpExpiryMinutes", emailVerificationOtpExpiryMinutes);

      String htmlContent = templateEngine.process("email/verification-otp", context);
      helper.setText(htmlContent, true);

      mailSender.send(message);
      log.info("Verification OTP email sent to: {}", to);
    } catch (MessagingException e) {
      log.error("Failed to send verification OTP email to: {}", to, e);
      throw new RuntimeException("Failed to send verification OTP email", e);
    }
  }

  @Override
  public void sendPasswordResetOtp(String to, String username, String otp) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      applyFrom(helper);

      helper.setTo(to);
      helper.setSubject("Reset your password - Vegalife");

      Context context = new Context();
      context.setVariable("username", username);
      context.setVariable("otp", otp);
      context.setVariable("otpExpiryMinutes", otpExpiryMinutes);

      String htmlContent = templateEngine.process("email/password-reset-otp", context);
      helper.setText(htmlContent, true);

      mailSender.send(message);
      log.info("Password reset email sent to: {}", to);
    } catch (MessagingException e) {
      log.error("Failed to send password reset email to: {}", to, e);
      throw new RuntimeException("Failed to send password reset email", e);
    }
  }

  @Override
  public void sendPaymentReceipt(String to, String username, OutboundEmailPayload.Receipt receipt) {
    if (receipt == null) {
      throw new IllegalArgumentException("Payment receipt details are required");
    }
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      applyFrom(helper);

      helper.setTo(to);
      helper.setSubject("Your payment receipt - Vegalife");

      Context context = new Context();
      context.setVariable("username", username);
      context.setVariable("planName", receipt.planName());
      context.setVariable("amount", receipt.amount());
      context.setVariable("currency", receipt.currency());
      context.setVariable("paidAt", receipt.paidAt());
      context.setVariable("reference", receipt.reference());

      String htmlContent = templateEngine.process("email/payment-receipt", context);
      helper.setText(htmlContent, true);

      mailSender.send(message);
      log.info("Payment receipt email sent to: {}", to);
    } catch (MessagingException e) {
      log.error("Failed to send payment receipt email to: {}", to, e);
      throw new RuntimeException("Failed to send payment receipt email", e);
    }
  }

  private void applyFrom(MimeMessageHelper helper) throws MessagingException {
    if (fromAddress != null && !fromAddress.isBlank()) {
      helper.setFrom(fromAddress);
    }
  }
}
