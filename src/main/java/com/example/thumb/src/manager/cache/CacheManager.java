package com.example.thumb.src.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManager {
    private final RedisTemplate<String, Object> redisTemplate;
    private TopK hotKeyDetector;
    private Cache<String, Object> localCache;

    public CacheManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public TopK getHotKeyDetector() {
        hotKeyDetector = new HeavyKeeper(
                // 监控 Top 100 Key
                100,
                // 宽度
                100000,
                // 深度
                5,
                // 衰减系数
                0.92,
                // 最小出现 10 次才记录
                10
        );
        return hotKeyDetector;
    }

    @Bean
    public Cache<String, Object> localCache() {
        return localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    // 辅助方法：构造复合Key
    private String buildCacheKey(String hashKey, String key){
        return hashKey + ":" + key;
    }

    public Object get(String hashKey, String key){
        // 构造唯一的 composite key（组合键）
        String composite = buildCacheKey(hashKey, key);

        // 1.先查询本地缓存
        Object value = localCache.getIfPresent(composite);
        if(value != null){
            // 查询到了本地缓存
            log.info("本地缓存获取到数据{} = {}",composite, value);
            // 记录访问次数+1
            hotKeyDetector.add(composite, 1);
            return value;
        }

        // 2.查询 redis 缓存
        Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
        if(redisValue == null){
            return null;
        }

        // 访问记录+1
        AddResult addResult = hotKeyDetector.add(key, 1);

        // 如果是热点数据，则保存到本地缓存
        if(addResult.isHotKey()){
            localCache.put(composite, redisValue);
        }

        return redisValue;
    }

    public void putIfPresent(String hashKey, String key, Object value){
        String composite = buildCacheKey(hashKey, key);
        Object obj = localCache.getIfPresent(composite);
        if (obj == null) {
            return;
        }
        localCache.put(composite, value);
    }

    // 定时清理过期的热 Key 检测数据
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void run(){
        hotKeyDetector.fading();
    }

}

