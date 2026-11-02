package com.example.remoteportal.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailDeliveryService {
  private final JavaMailSender sender;
  private final String from;

  public MailDeliveryService(ObjectProvider<JavaMailSender> senderProvider,
      @Value("${MAIL_FROM:${spring.mail.username:}}") String from) {
    this.sender = senderProvider.getIfAvailable();
    this.from = from == null ? "" : from.trim();
  }

  public boolean configured() {
    return sender != null && !from.isBlank();
  }

  public void sendVerificationCode(String email, String displayName, String code) {
    send(email, "Verify your Electrical Workbench account",
        "Hi " + safeName(displayName) + ",\n\n"
        + "Your Electrical Workbench verification code is: " + code + "\n\n"
        + "The code expires in 15 minutes. If you did not request this account, you can ignore this email.\n\n"
        + "Electrical Workbench");
  }

  public void sendPasswordResetCode(String email, String username, String code) {
    send(email, "Electrical Workbench password reset code",
        "A password reset was requested for Electrical Workbench account " + username + ".\n\n"
        + "Reset code: " + code + "\n\n"
        + "The code expires in 15 minutes. If you did not request this reset, ignore this email.\n\n"
        + "Electrical Workbench");
  }

  public void sendUsernameReminder(String email, String usernames) {
    send(email, "Your Electrical Workbench username",
        "The Electrical Workbench username associated with this email is:\n\n"
        + usernames + "\n\n"
        + "If you did not request this reminder, you can ignore this email.\n\n"
        + "Electrical Workbench");
  }

  private void send(String to, String subject, String text) {
    if (!configured()) throw new IllegalStateException("Email delivery is not configured yet.");
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);
    sender.send(message);
  }

  private static String safeName(String name) {
    return name == null || name.isBlank() ? "there" : name.trim();
  }
}
