package com.vegalife.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private SmtpEmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // Use reflection to set the tokenExpiryMinutes field
        try {
            var field = SmtpEmailServiceImpl.class.getDeclaredField("tokenExpiryMinutes");
            field.setAccessible(true);
            field.set(emailService, 30);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sendVerificationEmail_callsMailSender() throws MessagingException {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/verification"), any(Context.class)))
            .thenReturn("<html>Verification email</html>");

        emailService.sendVerificationEmail("test@example.com", "testuser", "http://localhost:8080/api/auth/verify-email?token=abc");

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
        verify(templateEngine).process(eq("email/verification"), any(Context.class));
    }
}