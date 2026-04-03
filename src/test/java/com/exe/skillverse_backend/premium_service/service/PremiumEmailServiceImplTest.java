package com.exe.skillverse_backend.premium_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.service.impl.PremiumEmailServiceImpl;
import com.exe.skillverse_backend.shared.service.EmailService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PremiumEmailServiceImplTest {

    @Mock
    private EmailService emailService;

    private PremiumEmailServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PremiumEmailServiceImpl(emailService);
    }

    @Test
    @DisplayName("sendAutoRenewalFailedEmail should send the reminder email content")
    void sendAutoRenewalFailedEmail_ShouldSendReminderEmailContent() {
        User user = User.builder().id(1L).email("learner@skillverse.vn").firstName("Learner").lastName("One").build();
        PremiumPlan plan = PremiumPlan.builder()
                .name("premium_plus")
                .displayName("Co Van Pro")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startDate(LocalDateTime.of(2026, 4, 3, 8, 0))
                .endDate(LocalDateTime.of(2026, 5, 3, 8, 0))
                .build();

        service.sendAutoRenewalFailedEmail(user, subscription, new BigDecimal("249000"));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(org.mockito.ArgumentMatchers.eq(user.getEmail()), subjectCaptor.capture(),
                htmlCaptor.capture());
        assertTrue(subjectCaptor.getValue().contains("Co Van Pro"));
        assertTrue(htmlCaptor.getValue().contains("249"));
    }

    @Test
    @DisplayName("sendAutoRenewalFailedEmail should swallow email transport failures")
    void sendAutoRenewalFailedEmail_ShouldSwallowEmailTransportFailures() {
        User user = User.builder().id(1L).email("learner@skillverse.vn").firstName("Learner").lastName("One").build();
        PremiumPlan plan = PremiumPlan.builder()
                .name("premium_plus")
                .displayName("Co Van Pro")
                .planType(PremiumPlan.PlanType.PREMIUM_PLUS)
                .build();
        UserSubscription subscription = UserSubscription.builder()
                .user(user)
                .plan(plan)
                .startDate(LocalDateTime.of(2026, 4, 3, 8, 0))
                .endDate(LocalDateTime.of(2026, 5, 3, 8, 0))
                .build();
        doThrow(new RuntimeException("SMTP unavailable")).when(emailService)
                .sendHtmlEmail(org.mockito.ArgumentMatchers.eq(user.getEmail()), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());

        assertDoesNotThrow(() -> service.sendAutoRenewalFailedEmail(user, subscription, new BigDecimal("249000")));
    }
}
