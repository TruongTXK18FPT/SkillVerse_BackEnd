package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.service.EmailService.EmailSendingResult;
import com.exe.skillverse_backend.shared.service.impl.EmailServiceImpl;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailServiceImpl(mailSender);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@skillverse.vn");
        ReflectionTestUtils.setField(service, "fromName", "SkillVerse");
    }

    @Test
    @DisplayName("sendHtmlEmail should build and send a mime message")
    void sendHtmlEmail_ShouldBuildAndSendMimeMessage() throws Exception {
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendHtmlEmail("to@skillverse.vn", "Subject", "<strong>Hello</strong>");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage sent = messageCaptor.getValue();

        assertEquals("Subject", sent.getSubject());
        assertEquals("to@skillverse.vn", sent.getAllRecipients()[0].toString());
    }

    @Test
    @DisplayName("sendHtmlEmailAsync should return false when sending fails")
    void sendHtmlEmailAsync_ShouldReturnFalseWhenSendingFails() {
        when(mailSender.createMimeMessage()).thenThrow(new IllegalStateException("smtp down"));

        CompletableFuture<Boolean> result = service.sendHtmlEmailAsync(
                "to@skillverse.vn",
                "Subject",
                "<strong>Hello</strong>");

        assertFalse(result.join());
    }

    @Test
    @DisplayName("sendBulkEmailAsync should aggregate failed recipients")
    void sendBulkEmailAsync_ShouldAggregateFailedRecipients() {
        EmailServiceImpl spy = Mockito.spy(service);
        doNothing()
                .doThrow(new RuntimeException("failed"))
                .doNothing()
                .when(spy).sendHtmlEmail(anyString(), anyString(), anyString());

        EmailSendingResult result = spy.sendBulkEmailAsync(
                List.of("a@skillverse.vn", "b@skillverse.vn", "c@skillverse.vn"),
                "Bulk",
                "<p>Hello</p>",
                2,
                0)
                .join();

        assertEquals(3, result.totalRecipients());
        assertEquals(2, result.successCount());
        assertEquals(1, result.failedCount());
        assertEquals(List.of("b@skillverse.vn"), result.failedEmails());
    }

    @Test
    @DisplayName("sendBulkEmailToUsersAsync should skip users without emails")
    void sendBulkEmailToUsersAsync_ShouldSkipUsersWithoutEmails() {
        EmailServiceImpl spy = Mockito.spy(service);
        doNothing().when(spy).sendHtmlEmail(anyString(), anyString(), anyString());

        EmailSendingResult result = spy.sendBulkEmailToUsersAsync(
                List.of(
                        User.builder().id(1L).email("one@skillverse.vn").build(),
                        User.builder().id(2L).email("").build(),
                        User.builder().id(3L).email(null).build()),
                "Bulk",
                "<p>Hello</p>")
                .join();

        assertEquals(1, result.totalRecipients());
        assertEquals(1, result.successCount());
    }
}
