package com.example.demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfiguration {
    @Bean
    public CacheManager cacheManager() {
        var caches = new CaffeineCacheManager("deliverySearches");
        caches.setCaffeine(Caffeine.newBuilder().maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(5)).recordStats());
        caches.setAllowNullValues(false);
        // Defer cache puts/evictions until commit; rollback does not publish changes.
        return new TransactionAwareCacheManagerProxy(caches);
    }
}
