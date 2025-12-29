package com.exe.skillverse_backend.payment_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PayOSProperties.class)
public class PaymentConfig {
}
