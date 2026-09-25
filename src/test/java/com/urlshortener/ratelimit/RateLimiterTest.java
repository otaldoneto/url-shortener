package com.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

// Against a real Redis (Testcontainers), running the actual Lua script
@SpringBootTest
@Import({TestcontainersConfiguration.class, RateLimiterTest.MutableClockConfig.class})
class RateLimiterTest {

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-09-25T12:00:00Z"));
        }
    }

    @Autowired
    RateLimiter limiter;

    @Autowired
    MutableClock clock;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.parse("2026-09-25T12:00:00Z"));
    }

    @AfterEach
    void cleanRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void allowsRequestsUpToTheBucketCapacity() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("client-a", 5, 1)).isTrue();
        }

        assertThat(limiter.tryConsume("client-a", 5, 1)).isFalse();
    }

    @Test
    void refillsTokensOverTime() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("client-b", 5, 1);
        }
        assertThat(limiter.tryConsume("client-b", 5, 1)).isFalse();

        // 1 token per second; 3 seconds pass, so 3 tokens are refilled
        clock.advance(Duration.ofSeconds(3));

        assertThat(limiter.tryConsume("client-b", 5, 1)).isTrue();
        assertThat(limiter.tryConsume("client-b", 5, 1)).isTrue();
        assertThat(limiter.tryConsume("client-b", 5, 1)).isTrue();
        assertThat(limiter.tryConsume("client-b", 5, 1)).isFalse();
    }

    @Test
    void neverRefillsPastTheCapacity() {
        limiter.tryConsume("client-c", 5, 1);

        // A whole day passes: the bucket must cap at capacity, not grow without bound
        clock.advance(Duration.ofDays(1));

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("client-c", 5, 1)).isTrue();
        }
        assertThat(limiter.tryConsume("client-c", 5, 1)).isFalse();
    }

    @Test
    void tracksEachKeySeparately() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("client-d", 5, 1);
        }

        assertThat(limiter.tryConsume("client-d", 5, 1)).isFalse();
        assertThat(limiter.tryConsume("client-e", 5, 1)).isTrue();
    }

    @Test
    void isAtomicUnderConcurrentRequestsFromTheSameKey() throws InterruptedException {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (limiter.tryConsume("client-race", 10, 1)) {
                    allowedCount.incrementAndGet();
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // Exactly 10 (the capacity), never more: the script is atomic even with 50 concurrent callers
        assertThat(allowedCount.get()).isEqualTo(10);
    }
}
