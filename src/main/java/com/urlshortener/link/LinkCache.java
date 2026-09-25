package com.urlshortener.link;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// Cache-aside on top of Redis: the target URL (with a TTL) and a click counter (with none, it must last).
// Two separate keys because the two values have different lifetimes.
@Component
public class LinkCache {

    static final Duration TARGET_URL_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public LinkCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<String> getTargetUrl(String code) {
        return Optional.ofNullable(redis.opsForValue().get(targetUrlKey(code)));
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
