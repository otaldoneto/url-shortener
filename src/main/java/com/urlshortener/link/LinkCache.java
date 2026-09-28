package com.urlshortener.link;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

// Cache-aside on top of Redis: the target URL (with a TTL) and a click counter (with none, it must last).
// Two separate keys because the two values have different lifetimes.
@Component
public class LinkCache {

    static final Duration TARGET_URL_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public LinkCache(StringRedisTemplate redis, MeterRegistry registry) {
        this.redis = redis;
        this.cacheHits = registry.counter("link.cache.access", "result", "hit");
        this.cacheMisses = registry.counter("link.cache.access", "result", "miss");
    }

    public Optional<String> getTargetUrl(String code) {
        String value = redis.opsForValue().get(targetUrlKey(code));
        (value != null ? cacheHits : cacheMisses).increment();
        return Optional.ofNullable(value);
    }

    public void cacheTargetUrl(String code, String targetUrl) {
        redis.opsForValue().set(targetUrlKey(code), targetUrl, TARGET_URL_TTL);
    }

    // Atomic increment: concurrent redirects never lose a click to a race condition
    public long incrementClicks(String code) {
        Long clicks = redis.opsForValue().increment(clicksKey(code));
        return clicks == null ? 0 : clicks;
    }

    public long getClicks(String code) {
        String value = redis.opsForValue().get(clicksKey(code));
        return value == null ? 0 : Long.parseLong(value);
    }

    private static String targetUrlKey(String code) {
        return "link:" + code;
    }

    private static String clicksKey(String code) {
        return "clicks:" + code;
    }
}
