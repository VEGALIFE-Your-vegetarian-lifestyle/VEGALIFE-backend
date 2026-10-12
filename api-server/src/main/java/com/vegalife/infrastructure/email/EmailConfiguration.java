package com.vegalife.infrastructure.email;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class EmailConfiguration {

  @Value("${spring.mail.host:localhost}")
  private String host;

  @Value("${spring.mail.port:25}")
  private int port;

  @Value("${spring.mail.username:}")
  private String username;

  @Value("${spring.mail.password:}")
  private String password;

  @Value("${spring.mail.properties.mail.smtp.auth:true}")
  private String smtpAuth;

  @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
  private String smtpStartTls;

  @Value("${spring.mail.properties.mail.debug:false}")
  private String mailDebug;

  @Bean
  public JavaMailSender javaMailSender() {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(host);
    mailSender.setPort(port);
    mailSender.setUsername(username);
    mailSender.setPassword(password);

    Properties props = mailSender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", smtpAuth);
    props.put("mail.smtp.starttls.enable", smtpStartTls);
    props.put("mail.debug", mailDebug);

    return mailSender;
  }
}
