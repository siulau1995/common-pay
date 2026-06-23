package io.github.commonpay.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class PayRedisLock {
    private static final String LOCK_KEY_PREFIX = "pay-lock:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public String lock(String lockName, long expireSeconds) {
        String lockValue = UUID.randomUUID().toString();
        Boolean result = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + lockName, lockValue, expireSeconds, TimeUnit.SECONDS);
        if (result != null && result) {
            return lockValue;
        }
        return null;
    }

    public boolean unlock(String lockName, String lockValue) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(LOCK_KEY_PREFIX + lockName), Collections.singletonList(lockValue));
        return result != null && result > 0;
    }
}
