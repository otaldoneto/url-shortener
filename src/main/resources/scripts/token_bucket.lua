-- Token bucket rate limiter. Runs as a single atomic operation in Redis, so concurrent
-- requests from the same key can never both read the same stale token count.
--
-- KEYS[1] = the bucket key (e.g. "ratelimit:203.0.113.5")
-- ARGV[1] = capacity (max tokens the bucket can hold)
-- ARGV[2] = refill rate, in tokens per second
-- ARGV[3] = current time, in milliseconds (passed in instead of read from Redis, so tests can control it)
--
-- Returns 1 if the request is allowed (a token was spent), 0 if the bucket was empty.
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_per_second = tonumber(ARGV[2])
local now_ms = tonumber(ARGV[3])

local bucket = redis.call("HMGET", key, "tokens", "updated_at_ms")
local tokens = tonumber(bucket[1])
local updated_at_ms = tonumber(bucket[2])

-- First request from this key: start with a full bucket
if tokens == nil then
    tokens = capacity
    updated_at_ms = now_ms
end

-- Refill based on how much time passed since the last request
local elapsed_seconds = math.max(0, now_ms - updated_at_ms) / 1000
tokens = math.min(capacity, tokens + elapsed_seconds * refill_per_second)

local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

redis.call("HSET", key, "tokens", tokens, "updated_at_ms", now_ms)
-- The bucket is forgotten if nobody uses this key for a while, instead of every IP living forever in Redis
redis.call("EXPIRE", key, math.ceil(capacity / refill_per_second) + 60)

return allowed
