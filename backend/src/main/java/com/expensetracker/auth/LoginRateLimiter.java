package com.expensetracker.auth;

import com.expensetracker.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window limiter for login and registration.
 *
 * <p>Failures are counted per user and IP (stops guessing without locking the owner out from their own
 * device), per user across all IPs (stops distributed guessing) and all attempts per IP (stops spraying
 * many usernames or mass registration).
 */
@Component
public class LoginRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AuthProperties.RateLimit limits;
    private final Clock clock;

    public LoginRateLimiter(AuthProperties properties, Clock clock) {
        this.limits = properties.rateLimit();
        this.clock = clock;
    }

    /** Throws if the next attempt from {@code ip} (optionally for {@code username}) is not allowed. */
    public void checkAllowed(String ip, String username) {
        Instant now = clock.instant();
        check(ipKey(ip), limits.maxAttemptsPerIp(), now);
        if (username != null) {
            check(userIpKey(username, ip), limits.maxFailuresPerUserAndIp(), now);
            check(userKey(username), limits.maxFailuresPerUser(), now);
        }
    }

    public void recordAttempt(String ip) {
        increment(ipKey(ip));
    }

    public void recordFailure(String ip, String username) {
        increment(userIpKey(username, ip));
        increment(userKey(username));
    }

    public void recordSuccess(String ip, String username) {
        windows.remove(userIpKey(username, ip));
    }

    private void check(String key, int max, Instant now) {
        Window window = windows.get(key);
        if (window != null && !expired(window, now) && window.count() >= max) {
            Duration retryAfter = Duration.between(now, window.start().plus(limits.window()));
            throw new TooManyRequestsException(
                    "Zu viele Anmeldeversuche. Bitte in " + minutes(retryAfter) + " erneut versuchen.", retryAfter);
        }
    }

    private void increment(String key) {
        Instant now = clock.instant();
        windows.compute(key, (k, window) ->
                window == null || expired(window, now) ? new Window(now, 1) : new Window(window.start(), window.count() + 1));
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> expired(window, now));
        }
    }

    private boolean expired(Window window, Instant now) {
        return !now.isBefore(window.start().plus(limits.window()));
    }

    private static String minutes(Duration duration) {
        long minutes = Math.max(1, (duration.toSeconds() + 59) / 60);
        return minutes == 1 ? "einer Minute" : minutes + " Minuten";
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String userKey(String username) {
        return "user:" + username.toLowerCase(Locale.ROOT);
    }

    private static String userIpKey(String username, String ip) {
        return "user-ip:" + username.toLowerCase(Locale.ROOT) + "|" + ip;
    }
}
