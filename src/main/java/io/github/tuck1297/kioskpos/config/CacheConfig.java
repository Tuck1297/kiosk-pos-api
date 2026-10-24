package io.github.tuck1297.kioskpos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Redis is an accelerator, not a dependency: if it is slow or down, cache calls fail fast
 * (see spring.data.redis.timeout), get logged, and the request falls through to Postgres.
 * Registers keep selling either way.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache read failed for {}:{}; using the database. {}", cache.getName(), key, e.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache write failed for {}:{}. {}", cache.getName(), key, e.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed for {}:{}; entry expires by TTL. {}", cache.getName(), key, e.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed for {}. {}", cache.getName(), e.toString());
            }
        };
    }
}
