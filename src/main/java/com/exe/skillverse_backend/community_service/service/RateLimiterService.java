package com.exe.skillverse_backend.community_service.service;

public interface RateLimiterService {
    boolean tryConsume(String key, int limit, long windowSeconds);
}
