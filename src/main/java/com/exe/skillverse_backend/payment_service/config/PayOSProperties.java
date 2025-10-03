package com.exe.skillverse_backend.payment_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PayOS configuration properties
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment.payos")
public class PayOSProperties {

    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String baseUrl;
    private String webhookUrl;
}
