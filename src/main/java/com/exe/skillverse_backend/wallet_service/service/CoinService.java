package com.exe.skillverse_backend.wallet_service.service;

import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface CoinService {
        BigDecimal COIN_PRICE_VND = new BigDecimal("100");

        Map<String, Object> purchaseCoinsWithWalletCash(
                        Long userId,
                        Long coinAmount,
                        String packageId);

        CreatePaymentResponse purchaseCoinsWithPayOS(
                        Long userId,
                        Long coinAmount,
                        String packageId,
                        String returnUrl,
                        String cancelUrl);

        void handleCoinPurchaseCallback(
                        Long userId,
                        String paymentReferenceId,
                        Map<String, String> metadata);

        List<Map<String, Object>> getCoinPackages();

        BigDecimal calculateCoinPrice(Long coinAmount);

        void refundCoins(
                        Long userId,
                        Long coinAmount,
                        String reason,
                        String referenceType,
                        String referenceId);
}
