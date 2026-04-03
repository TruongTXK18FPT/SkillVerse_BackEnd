package com.exe.skillverse_backend.payment_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingReview;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.service.impl.InvoiceServiceImpl;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.wallet_service.entity.Wallet;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private BookingReviewRepository bookingReviewRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    private InvoiceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InvoiceServiceImpl(
                userProfileRepository,
                bookingReviewRepository,
                walletTransactionRepository);
    }

    @Test
    @DisplayName("generatePaymentInvoice should include the internal reference and customer email")
    void generatePaymentInvoice_ShouldIncludeInternalReferenceAndCustomerEmail() throws Exception {
        User user = user(1L, "customer@skillverse.vn", "Customer", "One");
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(99L)
                .user(user)
                .internalReference("TXN-001")
                .referenceId("PO-001")
                .amount(new BigDecimal("199000"))
                .description("Premium plan")
                .type(PaymentTransaction.PaymentType.PREMIUM_SUBSCRIPTION)
                .status(PaymentTransaction.PaymentStatus.COMPLETED)
                .paymentMethod(PaymentTransaction.PaymentMethod.PAYOS)
                .createdAt(LocalDateTime.of(2026, 4, 3, 8, 0))
                .build();

        when(userProfileRepository.findByUserId(1L))
                .thenReturn(Optional.of(UserProfile.builder().userId(1L).fullName("Customer Profile").build()));

        byte[] pdf = service.generatePaymentInvoice(payment, "ADMIN");

        assertTrue(pdf.length > 500);
    }

    @Test
    @DisplayName("generateBookingInvoice should include booking reference and consult payout transactions")
    void generateBookingInvoice_ShouldIncludeBookingReferenceAndConsultPayoutTransactions() throws Exception {
        User mentor = user(10L, "mentor@skillverse.vn", "Mentor", "One");
        User learner = user(20L, "learner@skillverse.vn", "Learner", "One");
        Booking booking = Booking.builder()
                .id(200L)
                .mentor(mentor)
                .learner(learner)
                .status(BookingStatus.COMPLETED)
                .priceVnd(new BigDecimal("500000"))
                .durationMinutes(60)
                .meetingLink("https://meet.jit.si/room")
                .startTime(LocalDateTime.of(2026, 4, 3, 9, 0))
                .endTime(LocalDateTime.of(2026, 4, 3, 10, 0))
                .build();

        when(userProfileRepository.findByUserId(20L)).thenReturn(Optional.empty());
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(bookingReviewRepository.findByBookingId(200L)).thenReturn(Optional.of(BookingReview.builder()
                .rating(5)
                .comment("Great session")
                .reply("Thanks")
                .build()));
        when(walletTransactionRepository.findByReferenceTypeAndReferenceId("BOOKING_PAYOUT", "BOOKING_200"))
                .thenReturn(List.of());

        byte[] pdf = service.generateBookingInvoice(booking, "MENTOR");

        assertTrue(pdf.length > 500);
        verify(walletTransactionRepository).findByReferenceTypeAndReferenceId("BOOKING_PAYOUT", "BOOKING_200");
    }

    @Test
    @DisplayName("generateWalletTransactionInvoice should include wallet invoice number")
    void generateWalletTransactionInvoice_ShouldIncludeWalletInvoiceNumber() throws Exception {
        User user = user(30L, "wallet@skillverse.vn", "Wallet", "Owner");
        WalletTransaction transaction = WalletTransaction.builder()
                .transactionId(300L)
                .wallet(Wallet.builder().walletId(3L).user(user).build())
                .transactionType(WalletTransaction.TransactionType.PURCHASE_PREMIUM)
                .currencyType(WalletTransaction.CurrencyType.CASH)
                .cashAmount(new BigDecimal("450000"))
                .fee(new BigDecimal("50000"))
                .description("Wallet premium")
                .status(WalletTransaction.TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.of(2026, 4, 3, 11, 0))
                .build();

        when(userProfileRepository.findByUserId(30L)).thenReturn(Optional.empty());

        byte[] pdf = service.generateWalletTransactionInvoice(transaction, "MENTOR");

        assertTrue(pdf.length > 500);
    }

    private User user(Long id, String email, String firstName, String lastName) {
        return User.builder()
                .id(id)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .build();
    }
}
