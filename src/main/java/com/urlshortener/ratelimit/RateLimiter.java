package com.urlshortener.ratelimit;

import java.time.Clock;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

// Token bucket rate limiter backed by Redis: each key (an IP address) gets its own bucket that refills
// over time, so a burst is allowed but sustained abuse is not. Runs entirely inside a Redis Lua script,
// so a race between two concurrent requests from the same key can never both be let through incorrectly.
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final DefaultRedisScript<Long> script;

    public RateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
        this.script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua")));
    }

    // capacity: how many requests can burst through at once. refillPerSecond: the sustained rate afterwards.
    public boolean tryConsume(String key, int capacity, double refillPerSecond) {
        Long allowed = redis.execute(script, List.of("ratelimit:" + key), String.valueOf(capacity),
                String.valueOf(refillPerSecond), String.valueOf(clock.millis()));
        return allowed != null && allowed == 1;
    }
}
