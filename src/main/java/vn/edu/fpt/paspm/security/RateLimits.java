package vn.edu.fpt.paspm.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import vn.edu.fpt.paspm.common.ApiException;

/**
 * Single-instance limiter. Replace with shared Redis/gateway limits for replicas.
 */
@Component
public class RateLimits {
    private record Window(Instant until, int count) {
    }

    private final Map<String, Window> windows = new HashMap<>();

    public synchronized void hit(String key, int max, int seconds) {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> entry.getValue().until().isBefore(now));

        Window current = windows.get(key);
        if (current != null && current.count() >= max) {
            throw new ApiException(429, "Too many attempts; try later");
        }
        if (current == null && windows.size() >= 10_000) {
            throw new ApiException(429, "Request limit reached");
        }

        int nextCount = current == null ? 1 : current.count() + 1;
        Instant nextUntil = current == null ? now.plusSeconds(seconds) : current.until();
        windows.put(key, new Window(nextUntil, nextCount));
    }
}
