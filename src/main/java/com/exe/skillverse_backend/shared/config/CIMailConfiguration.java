package com.exe.skillverse_backend.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Properties;

@Configuration
@Profile("ci")
@Slf4j
public class CIMailConfiguration {

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return new MockJavaMailSender();
    }

    @Slf4j
    public static class MockJavaMailSender implements JavaMailSender {

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            log.info("📧 [CI MOCK] Email would be sent to: {}", simpleMessage.getTo()[0]);
            log.info("📧 [CI MOCK] Subject: {}", simpleMessage.getSubject());
            log.info("📧 [CI MOCK] Text: {}", simpleMessage.getText());
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            for (SimpleMailMessage message : simpleMessages) {
                send(message);
            }
        }

        @Override
        public MimeMessage createMimeMessage() {
            Session session = Session.getInstance(new Properties());
            return new MimeMessage(session);
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            Session session = Session.getInstance(new Properties());
            try {
                return new MimeMessage(session, contentStream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            log.info("📧 [CI MOCK] MimeMessage would be sent");
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            for (MimeMessage message : mimeMessages) {
                send(message);
            }
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            log.info("📧 [CI MOCK] MimeMessagePreparator would be sent");
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
            for (MimeMessagePreparator preparator : mimeMessagePreparators) {
                send(preparator);
            }
        }
    }
}