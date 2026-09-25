package com.urlshortener.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.urlshortener.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

// Against a real Redis (Testcontainers)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LinkCacheTest {

    @Autowired
    LinkCache cache;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void cleanRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void cachesAndReturnsTheTargetUrl() {
        assertThat(cache.getTargetUrl("abc1234")).isEmpty();

        cache.cacheTargetUrl("abc1234", "https://example.com");

        assertThat(cache.getTargetUrl("abc1234")).contains("https://example.com");
    }

    @Test
    void setsATtlOnTheCachedUrl() {
        cache.cacheTargetUrl("abc1234", "https://example.com");

        Long ttl = redis.getExpire("link:abc1234");

        assertThat(ttl).isPositive().isLessThanOrEqualTo(LinkCache.TARGET_URL_TTL.toSeconds());
    }

    @Test
    void countsClicksStartingFromZero() {
        assertThat(cache.getClicks("abc1234")).isZero();

        long first = cache.incrementClicks("abc1234");
        long second = cache.incrementClicks("abc1234");

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
        assertThat(cache.getClicks("abc1234")).isEqualTo(2);
    }

    @Test
    void theClickCounterHasNoExpiration() {
        cache.incrementClicks("abc1234");

        assertThat(redis.getExpire("clicks:abc1234")).isEqualTo(-1);
    }
}
