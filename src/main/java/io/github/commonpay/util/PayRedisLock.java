package io.github.commonpay.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Redis lock used to serialize callback and state-changing payment operations. */
@Component
public class PayRedisLock {
    private static final String LOCK_KEY_PREFIX = "pay-lock:";
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public PayRedisLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String lock(String lockName, long expireSeconds) {
        if (expireSeconds <= 0) {
            throw new IllegalArgumentException("Lock expiration must be greater than zero");
        }
        String lockValue = UUID.randomUUID().toString();
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
                LOCK_KEY_PREFIX + lockName, lockValue, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result) ? lockValue : null;
    }

    public boolean unlock(String lockName, String lockValue) {
        if (lockValue == null || lockValue.isEmpty()) {
            return false;
        }
        Long result = redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_KEY_PREFIX + lockName),
                lockValue);
        return result != null && result > 0;
    }
}
