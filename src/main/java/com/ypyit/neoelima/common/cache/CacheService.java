package com.ypyit.neoelima.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void saveValueWithExpiration(String key, Object value, long timeout) {
        try {
            this.redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error on redis save cmd {}", ExceptionUtils.getMessage(e));
        }
    }

    public void saveValueWithExpiration(String key, Object value, long timeout, TimeUnit unit) {
        try {
            this.redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("Error on redis cmd {}", ExceptionUtils.getMessage(e));
        }
    }


    public void saveValueWithoutExpiration(String key, Object value) {
        try {
            this.redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("Error on redis permanently cmd {}", ExceptionUtils.getMessage(e));
        }
    }

    public void delete(String key) {
        try {
            this.redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error on redis delete cmd {}", ExceptionUtils.getMessage(e));
        }
    }

    public Object getValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error on redis get cmd {}", ExceptionUtils.getMessage(e));
        }
        return null;
    }
}
