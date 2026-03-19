package com.exe.skillverse_backend.payment_service.service;

import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;

public interface InvoiceService {
    byte[] generatePaymentInvoice(PaymentTransaction payment);

    byte[] generatePaymentInvoice(PaymentTransaction payment, String role);

    byte[] generateWalletTransactionInvoice(WalletTransaction transaction);

    byte[] generateWalletTransactionInvoice(WalletTransaction transaction, String role);

    byte[] generateBookingInvoice(Booking booking);

    byte[] generateBookingInvoice(Booking booking, String role);
}
