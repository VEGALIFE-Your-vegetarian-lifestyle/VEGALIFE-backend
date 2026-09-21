package com.vegalife.infrastructure.email;

import com.vegalife.service.email.EmailService;
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

  @Value("${app.verification.token-expiry-minutes:30}")
  private int tokenExpiryMinutes;

  @Override
  public void sendVerificationEmail(String to, String username, String verificationLink) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setTo(to);
      helper.setSubject("Verify your email - Vegalife");

      Context context = new Context();
      context.setVariable("username", username);
      context.setVariable("verificationLink", verificationLink);
      context.setVariable("tokenExpiryMinutes", tokenExpiryMinutes);

      String htmlContent = templateEngine.process("email/verification", context);
      helper.setText(htmlContent, true);

      mailSender.send(message);
      log.info("Verification email sent to: {}", to);
    } catch (MessagingException e) {
      log.error("Failed to send verification email to: {}", to, e);
      throw new RuntimeException("Failed to send verification email", e);
    }
  }
}
