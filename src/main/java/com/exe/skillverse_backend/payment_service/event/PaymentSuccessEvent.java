package com.exe.skillverse_backend.payment_service.event;

import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentSuccessEvent extends ApplicationEvent {
    private final PaymentTransaction transaction;

    public PaymentSuccessEvent(Object source, PaymentTransaction transaction) {
        super(source);
        this.transaction = transaction;
    }
}
