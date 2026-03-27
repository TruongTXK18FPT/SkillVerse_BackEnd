package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.purchasedto.CoursePurchaseDTO;
import com.exe.skillverse_backend.course_service.dto.purchasedto.CoursePurchaseRequestDTO;
import com.exe.skillverse_backend.payment_service.event.PaymentSuccessEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CoursePurchaseService {
    CoursePurchaseDTO purchaseWithWallet(Long userId, CoursePurchaseRequestDTO request);
    void handlePaymentSuccess(PaymentSuccessEvent event);
    Page<CoursePurchaseDTO> getMentorPurchases(Long mentorId, Pageable pageable);
}
