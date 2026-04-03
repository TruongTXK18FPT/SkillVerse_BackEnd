package com.exe.skillverse_backend.community_service.service;

import com.exe.skillverse_backend.community_service.service.impl.RateLimiterServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceImplTest {

    private final RateLimiterServiceImpl service = new RateLimiterServiceImpl();

    @Test
    @DisplayName("tryConsume should reject requests once the limit is reached")
    void tryConsume_ShouldRejectRequestsOnceLimitReached() {
        assertTrue(service.tryConsume("user-1", 2, 60));
        assertTrue(service.tryConsume("user-1", 2, 60));
        assertFalse(service.tryConsume("user-1", 2, 60));
    }

    @Test
    @DisplayName("tryConsume should reset the counter after the window expires")
    void tryConsume_ShouldResetCounterAfterWindowExpires() throws InterruptedException {
        assertTrue(service.tryConsume("user-2", 1, 1));
        assertFalse(service.tryConsume("user-2", 1, 1));

        Thread.sleep(1100L);

        assertTrue(service.tryConsume("user-2", 1, 1));
    }
}
