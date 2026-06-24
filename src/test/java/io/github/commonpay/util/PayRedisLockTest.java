package io.github.commonpay.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayRedisLockTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PayRedisLock lock;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lock = new PayRedisLock(redisTemplate);
    }

    @Test
    void acquiresAndReleasesOnlyWithTheOwnerToken() {
        when(valueOperations.setIfAbsent(eq("pay-lock:order:1001"), any(String.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList("pay-lock:order:1001")), any(String.class)))
                .thenReturn(1L);

        String token = lock.lock("order:1001", 30L);

        assertNotNull(token);
        assertTrue(lock.unlock("order:1001", token));
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(Collections.singletonList("pay-lock:order:1001")), eq(token));
    }

    @Test
    void reportsContentionWithoutInventingOwnership() {
        when(valueOperations.setIfAbsent(eq("pay-lock:order:1001"), any(String.class), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        assertNull(lock.lock("order:1001", 30L));
        assertFalse(lock.unlock("order:1001", null));
    }

    @Test
    void rejectsNonPositiveExpiration() {
        assertThrows(IllegalArgumentException.class, () -> lock.lock("order:1001", 0L));
    }
}
