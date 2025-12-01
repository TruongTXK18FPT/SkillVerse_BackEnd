package com.exe.skillverse_backend.community_service.service;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, int limit, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        Window w = windows.computeIfAbsent(key, k -> new Window(now, 0));
        if (now - w.start >= windowSeconds) {
            w.start = now;
            w.count = 0;
        }
        if (w.count >= limit) {
            return false;
        }
        w.count++;
        return true;
    }

    static class Window {
        long start;
        int count;
        Window(long s, int c) { this.start = s; this.count = c; }
    }
}
