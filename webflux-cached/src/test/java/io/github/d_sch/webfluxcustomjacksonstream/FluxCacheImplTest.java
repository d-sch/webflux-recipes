package io.github.d_sch.webfluxcustomjacksonstream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.github.d_sch.webfluxcached.common.KeyValueHolder;
import io.github.d_sch.webfluxcached.common.cache.FluxCache;
import io.github.d_sch.webfluxcached.common.cache.impl.FluxCacheImpl;
import io.github.d_sch.webfluxcached.common.cache.internal.CacheEntry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.resources.LoopResources;
import reactor.test.StepVerifier;

@Slf4j
public class FluxCacheImplTest {

    @Configuration
    @ConfigurationProperties(prefix = "cached")
    public static class CachedConfig {
        @Getter
        @Setter
        private Duration cleanUpDuration;

        @Getter
        @Setter
        private Duration expirationDuration;
    }

    @Test
    public void testCache() {
        FluxCache<Integer> cache = new FluxCacheImpl<>(LoopResources.create("prefix"));

        var testKeys = Flux.just(
            "Key 1", "Key 2", "Key 3", "Key 4", "Key 5"
        );
        
        var testEntries = Flux.just(
            KeyValueHolder.of("Key 1", 1),
            KeyValueHolder.of("Key 2", 2),
            KeyValueHolder.of("Key 3", 3),
            KeyValueHolder.of("Key 4", 4),
            KeyValueHolder.of("Key 5", 5)
        );

        var testEntries2 = Flux.just(
            KeyValueHolder.of("Key 1", 6),
            KeyValueHolder.of("Key 2", 7),
            KeyValueHolder.of("Key 3", 8),
            KeyValueHolder.of("Key 4", 9),
            KeyValueHolder.of("Key 5", 10)
        );
        
        var testEntries3 = Flux.just(
            KeyValueHolder.of("Key 11", 11),
            KeyValueHolder.of("Key 12", 12),
            KeyValueHolder.of("Key 13", 13),
            KeyValueHolder.of("Key 14", 14),
            KeyValueHolder.of("Key 15", 15)
        );

        StepVerifier.create(
            Flux.just(testEntries, testEntries2, testEntries3)  
                .publishOn(Schedulers.single())
                .parallel()
                .doOnNext(value -> log.info("Before put"))
                .flatMap(entries -> 
                    cache.put(entries)
                ).doOnNext(entry -> log.info("Parallel Result {}", entry.getValue())).sequential().publishOn(Schedulers.single())
                .doOnNext(entry -> log.info("Result {}", entry.getValue()))
                .flatMapSequential(entry -> Mono.just(entry.getValue()))
            ).expectNextCount(15).verifyComplete();

        StepVerifier.create(
            Mono.just(testKeys)
                .flatMapMany(keys -> 
                    cache.get(keys)
                ).subscribeOn(Schedulers.single()
                ).doOnNext(
                    value -> log.info("Result {}", value)
                )
        ).verifyComplete();

        testEntries = Flux.just(
            KeyValueHolder.of("Key 1", 16),
            KeyValueHolder.of("Key 2", 17),
            KeyValueHolder.of("Key 3", 18),
            KeyValueHolder.of("Key 4", 19),
            KeyValueHolder.of("Key 5", 20)
        );

        StepVerifier.create(
            Mono.just(testEntries)
                .publishOn(Schedulers.single())
                .flatMapMany(entries -> 
                    cache.put(entries)
                ).subscribeOn(Schedulers.single()
                ).doOnNext(value -> log.info("Result {}", value.getValue()))
                .map(CacheEntry::getValue)
        ).assertNext(
            actual -> assertEquals(16, actual)
        ).assertNext(
            actual -> assertEquals(17, actual)
        ).assertNext(
            actual -> assertEquals(18, actual)
        ).assertNext(
            actual -> assertEquals(19, actual)
        ).assertNext(
            actual -> assertEquals(20, actual)
        ).verifyComplete();

        StepVerifier.create(
            Mono.just(testKeys)
                .publishOn(Schedulers.single())
                .flatMapMany(keys -> 
                    cache.get(keys)
                ).subscribeOn(Schedulers.single()
                ).doOnNext(
                    value -> log.info("Result {}", value)
                )
        ).verifyComplete();
    }
}
