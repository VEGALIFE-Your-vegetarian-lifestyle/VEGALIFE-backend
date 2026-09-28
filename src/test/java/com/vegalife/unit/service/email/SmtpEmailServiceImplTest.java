package com.vegalife.unit.service.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.infrastructure.email.SmtpEmailServiceImpl;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceImplTest {

  @Mock private JavaMailSender mailSender;

  @Mock private TemplateEngine templateEngine;

  @Mock private MimeMessage mimeMessage;

  @InjectMocks private SmtpEmailServiceImpl emailService;

  @BeforeEach
  void setUp() {
    try {
      var otpField = SmtpEmailServiceImpl.class.getDeclaredField("otpExpiryMinutes");
      otpField.setAccessible(true);
      otpField.set(emailService, 10);

      var verificationOtpField =
          SmtpEmailServiceImpl.class.getDeclaredField("emailVerificationOtpExpiryMinutes");
      verificationOtpField.setAccessible(true);
      verificationOtpField.set(emailService, 10);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void sendVerificationOtp_callsMailSender() throws MessagingException {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    when(templateEngine.process(eq("email/verification-otp"), any(Context.class)))
        .thenReturn("<html>Verification OTP</html>");

    emailService.sendVerificationOtp("test@example.com", "testuser", "123456");

    verify(mailSender).createMimeMessage();
    verify(mailSender).send(mimeMessage);
    verify(templateEngine).process(eq("email/verification-otp"), any(Context.class));
  }

  @Test
  void sendPasswordResetOtp_callsMailSender() throws MessagingException {
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    when(templateEngine.process(eq("email/password-reset-otp"), any(Context.class)))
        .thenReturn("<html>Password reset OTP</html>");

    emailService.sendPasswordResetOtp("test@example.com", "testuser", "654321");

    verify(mailSender).createMimeMessage();
    verify(mailSender).send(mimeMessage);
    verify(templateEngine).process(eq("email/password-reset-otp"), any(Context.class));
  }

  @Test
  void sendVerificationOtp_setsExplicitFromAddress() throws Exception {
    MimeMessage realMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(mailSender.createMimeMessage()).thenReturn(realMessage);
    when(templateEngine.process(eq("email/verification-otp"), any(Context.class)))
        .thenReturn("<html>Verification OTP</html>");

    var fromField = SmtpEmailServiceImpl.class.getDeclaredField("fromAddress");
    fromField.setAccessible(true);
    fromField.set(emailService, "noreply@vegalife.local");

    emailService.sendVerificationOtp("test@example.com", "testuser", "123456");

    verify(mailSender).send(realMessage);
    assertEquals(1, realMessage.getFrom().length);
    assertEquals(
        "noreply@vegalife.local", ((InternetAddress) realMessage.getFrom()[0]).getAddress());
  }
}
