package com.expensetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expensetracker.common.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
    private final LoginRateLimiter limiter = new LoginRateLimiter(
            new AuthProperties(null, Duration.ofMinutes(15), Duration.ofDays(30), false, true,
                    new AuthProperties.RateLimit(Duration.ofMinutes(15), 3, 5, 10), Duration.ofHours(1)),
            clock);

    @Test
    void blocksUserFromIpAfterTooManyFailures() {
        fail("1.1.1.1", "toni", 3);

        assertThatThrownBy(() -> limiter.checkAllowed("1.1.1.1", "TONI"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfter())
                        .isEqualTo(Duration.ofMinutes(15)));
        // Same user from another device is still allowed
        assertThatCode(() -> limiter.checkAllowed("2.2.2.2", "toni")).doesNotThrowAnyException();
    }

    @Test
    void blocksUserAcrossIpsAfterTooManyFailures() {
        fail("1.1.1.1", "toni", 2);
        fail("2.2.2.2", "toni", 2);
        fail("3.3.3.3", "toni", 1);

        assertThatThrownBy(() -> limiter.checkAllowed("4.4.4.4", "toni")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> limiter.checkAllowed("4.4.4.4", "anna")).doesNotThrowAnyException();
    }

    @Test
    void blocksIpAfterTooManyAttempts() {
        for (int i = 0; i < 10; i++) {
            limiter.recordAttempt("1.1.1.1");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("1.1.1.1", null)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void windowExpiresAndSuccessResetsCounter() {
        fail("1.1.1.1", "toni", 3);
        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "toni")).doesNotThrowAnyException();

        fail("1.1.1.1", "anna", 2);
        limiter.recordSuccess("1.1.1.1", "anna");
        fail("1.1.1.1", "anna", 2);
        assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "anna")).doesNotThrowAnyException();
    }

    private void fail(String ip, String username, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkAllowed(ip, username);
            limiter.recordAttempt(ip);
            limiter.recordFailure(ip, username);
        }
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
