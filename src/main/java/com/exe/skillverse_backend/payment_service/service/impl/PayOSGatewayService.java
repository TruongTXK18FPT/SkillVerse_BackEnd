package com.exe.skillverse_backend.payment_service.service.impl;

import com.exe.skillverse_backend.payment_service.config.PayOSProperties;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.payment_service.service.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * PayOS payment gateway implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PayOSGatewayService implements PaymentGatewayService {

    private final PayOSProperties payOSProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    private void logConfigLoaded() {
        try {
            String clientId = payOSProperties.getClientId();
            String apiKey = payOSProperties.getApiKey();
            String checksum = payOSProperties.getChecksumKey();
            String baseUrl = payOSProperties.getBaseUrl();
            log.info("PayOS config loaded: clientIdLoaded={} apiKeyLoaded={} checksumLoaded={} baseUrl={}",
                    clientId != null && !clientId.isBlank(),
                    apiKey != null && !apiKey.isBlank(),
                    checksum != null && !checksum.isBlank(),
                    baseUrl);
        } catch (Exception e) {
            log.warn("Unable to log PayOS config loaded state", e);
        }
    }

    @Override
    public Map<String, Object> createPayment(PaymentTransaction transaction, String successUrl, String cancelUrl) {
        log.info("Creating PayOS payment for transaction: {}", transaction.getInternalReference());

        try {
            // Prepare PayOS payment request
            Map<String, Object> paymentRequest = new HashMap<>();
            // Generate a safe numeric orderCode from internal reference (max 9 digits)
            String digitsOnly = transaction.getInternalReference().replaceAll("\\D+", "");
            String lastNine = digitsOnly.length() >= 9 ? digitsOnly.substring(digitsOnly.length() - 9) : digitsOnly;
            int orderCode = Integer.parseInt(lastNine);
            String description = transaction.getDescription() != null 
                ? transaction.getDescription() 
                : "Payment for order " + transaction.getInternalReference();
            
            paymentRequest.put("orderCode", orderCode);
            paymentRequest.put("amount", transaction.getAmount().intValue());
            paymentRequest.put("description", description);
            paymentRequest.put("items", createPaymentItems(transaction));
            
            // Validate required URLs
            if (successUrl == null || cancelUrl == null) {
                log.error("Missing required URLs: successUrl={} cancelUrl={}", successUrl, cancelUrl);
                throw new IllegalArgumentException("successUrl and cancelUrl are required for PayOS payment");
            }
            
            paymentRequest.put("returnUrl", successUrl);
            paymentRequest.put("cancelUrl", cancelUrl);

            // Create signature per PayOS docs: key=value joined by '&', sorted by key (hex
            // HMAC-SHA256)
            String signature = createPaymentRequestSignature(Map.of(
                    "amount", paymentRequest.get("amount"),
                    "cancelUrl", paymentRequest.get("cancelUrl"),
                    "description", paymentRequest.get("description"),
                    "orderCode", paymentRequest.get("orderCode"),
                    "returnUrl", paymentRequest.get("returnUrl")));
            paymentRequest.put("signature", signature);

            // Debug logs for signature and payload (safe; no secrets)
            log.info("PayOS payloadForSignature amount={} cancelUrl={} description={} orderCode={} returnUrl={}",
                    paymentRequest.get("amount"),
                    paymentRequest.get("cancelUrl"),
                    paymentRequest.get("description"),
                    paymentRequest.get("orderCode"),
                    paymentRequest.get("returnUrl"));
            log.info("PayOS computedSignature={}", signature);
            log.info("PayOS request body (without headers)={}", paymentRequest);

            // Call PayOS API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", payOSProperties.getClientId());
            headers.set("x-api-key", payOSProperties.getApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(paymentRequest, headers);
            String apiUrl = payOSProperties.getBaseUrl() + "/v2/payment-requests";

            log.info("Calling PayOS API: {} with orderCode: {}", apiUrl, orderCode);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, request, Map.class);

            if ((response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED)
                    && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");

                if (data == null) {
                    log.error("PayOS response missing data field: {}", responseBody);
                    throw new RuntimeException("Invalid PayOS response: missing data");
                }

                Map<String, Object> result = new HashMap<>();
                result.put("checkoutUrl", data.get("checkoutUrl"));
                result.put("referenceId", String.valueOf(data.get("orderCode")));
                result.put("qrCode", data.get("qrCode"));

                log.info("PayOS payment created successfully: {}", data.get("orderCode"));
                return result;
            } else {
                String bodyStr = String.valueOf(response.getBody());
                log.error("PayOS create payment failed: status={} body={}", response.getStatusCode(), bodyStr);
                throw new RuntimeException("Failed to create PayOS payment: " + response.getStatusCode());
            }

        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            log.error("PayOS API error: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("PayOS API error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error creating PayOS payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create PayOS payment", e);
        }
    }

    @Override
    public PaymentTransaction.PaymentStatus verifyPayment(String gatewayReference) {
        log.info("Verifying PayOS payment: {}", gatewayReference);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-client-id", payOSProperties.getClientId());
            headers.set("x-api-key", payOSProperties.getApiKey());

            HttpEntity<Void> request = new HttpEntity<>(headers);
            String apiUrl = payOSProperties.getBaseUrl() + "/v2/payment-requests/" + gatewayReference;

            ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.GET, request, Map.class);

            if ((response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED)
                    && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data == null) {
                    log.error("PayOS verify response missing data field: {}", responseBody);
                    return PaymentTransaction.PaymentStatus.PENDING;
                }
                String status = (String) data.get("status");

                return switch (status.toLowerCase()) {
                    case "paid" -> PaymentTransaction.PaymentStatus.COMPLETED;
                    case "cancelled" -> PaymentTransaction.PaymentStatus.CANCELLED;
                    case "expired" -> PaymentTransaction.PaymentStatus.FAILED;
                    default -> PaymentTransaction.PaymentStatus.PENDING;
                };
            }

            return PaymentTransaction.PaymentStatus.PENDING;

        } catch (Exception e) {
            log.error("Error verifying PayOS payment: {}", e.getMessage(), e);
            return PaymentTransaction.PaymentStatus.PENDING;
        }
    }

    @Override
    public boolean processRefund(String gatewayReference, java.math.BigDecimal amount, String reason) {
        log.info("Processing PayOS refund: {}", gatewayReference);
        // Implement refund logic here if needed
        return false;
    }

    @Override
    public PaymentTransaction.PaymentMethod getSupportedPaymentMethod() {
        return PaymentTransaction.PaymentMethod.PAYOS;
    }

    @Override
    public boolean validateCallback(String signature, Map<String, Object> data) {
        log.info("Validating PayOS callback signature");

        try {
            String expectedSignature = createSignature(data);
            boolean isValid = signature.equals(expectedSignature);

            log.info("PayOS signature validation result: {}", isValid);
            return isValid;

        } catch (Exception e) {
            log.error("Error validating PayOS signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private java.util.List<Map<String, Object>> createPaymentItems(PaymentTransaction transaction) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", transaction.getDescription() != null ? transaction.getDescription() : "Premium");
        item.put("quantity", 1);
        item.put("price", transaction.getAmount().intValue());

        return java.util.List.of(item);
    }

    private String createSignature(Map<String, Object> data) throws NoSuchAlgorithmException, InvalidKeyException {
        // Remove signature field if present
        Map<String, Object> dataToSign = new HashMap<>(data);
        dataToSign.remove("signature");

        // Sort and concatenate values
        String dataString = dataToSign.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toString())
                .reduce("", (a, b) -> a + b);

        // Create HMAC-SHA256 signature
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
                payOSProperties.getChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);

        byte[] signatureBytes = mac.doFinal(dataString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    /**
     * Build signature string 'key=value&key2=value2' sorted by key and sign with
     * checksum (hex).
     */
    private String createPaymentRequestSignature(Map<String, Object> fields)
            throws NoSuchAlgorithmException, InvalidKeyException {
        String payload = fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue().toString()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
                payOSProperties.getChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
