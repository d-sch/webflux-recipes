package io.github.d_sch.webfluxcached;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("Put multiple entries in parallel and sequentially - all entries stored")
    public void putMultipleEntries_parallelAndSequential_shouldStoreAllEntries() {
        // Arrange: create a cache and several entry streams
        var loopResources = LoopResources.create("prefix");
        FluxCache<Integer> cache = new FluxCacheImpl<>(loopResources);

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

        // Act & Assert: put many entries in parallel and verify total number of stored entries
        StepVerifier.create(
            Flux.just(testEntries, testEntries2, testEntries3)  
                .publishOn(Schedulers.single())
                .parallel()
                .doOnNext(value -> log.info("Before put {}"))
                .flatMap(entries -> 
                    cache.put(entries)
                ).doOnNext(entry -> log.info("Parallel Result {}", entry.getValue()))
                .sequential()
                .publishOn(Schedulers.single())
                .doOnNext(entry -> log.info("Result {}", entry.getValue()))
                .flatMapSequential(entry -> Mono.just(entry.getValue()))
            ).expectNextCount(15).verifyComplete();

        // Act & Assert: retrieve existing keys and verify we get one entry per key
        StepVerifier.create(
            Mono.just(testKeys)
                .flatMapMany(keys -> 
                    cache.get(keys)
                ).publishOn(Schedulers.single()
                ).doOnNext(
                    entry -> log.info("Result {}", entry.getValue())
                )
        ).expectNextCount(5).verifyComplete();

        // Arrange: update existing keys with new values
        testEntries = Flux.just(
            KeyValueHolder.of("Key 1", 16),
            KeyValueHolder.of("Key 2", 17),
            KeyValueHolder.of("Key 3", 18),
            KeyValueHolder.of("Key 4", 19),
            KeyValueHolder.of("Key 5", 20)
        );

        // Act & Assert: verify overwrites of existing keys are reflected by put
        StepVerifier.create(
            Mono.just(testEntries)
                .publishOn(Schedulers.single())
                .flatMapMany(entries -> 
                    cache.put(entries)
                ).subscribeOn(Schedulers.single()
                ).doOnNext(entry -> log.info("Result {}", entry.getValue()))
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

        // Act & Assert: verify get returns updated values
        StepVerifier.create(
            Mono.just(testKeys)
                .publishOn(Schedulers.single())
                .flatMapMany(keys -> 
                    cache.get(keys)
                
                ).doOnNext(
                    entry -> log.info("Result {}", entry.getValue())
                )
        ).expectNextCount(5).verifyComplete();
    }

    @Test
    @DisplayName("Get after update returns updated values")
    public void getAfterUpdate_returnsUpdatedValues() {
        // Arrange: create cache and update entries
        var loopResources = LoopResources.create("prefix");
        FluxCache<Integer> cache = new FluxCacheImpl<>(loopResources);

        var initialEntries = Flux.just(
            KeyValueHolder.of("K1", 1),
            KeyValueHolder.of("K2", 2)
        );

        var updatedEntries = Flux.just(
            KeyValueHolder.of("K1", 11),
            KeyValueHolder.of("K2", 12)
        );

        StepVerifier.create(cache.put(initialEntries)).expectNextCount(2).verifyComplete();
        StepVerifier.create(cache.put(updatedEntries)).expectNextCount(2).verifyComplete();

        // Act & Assert: get returns the updated values
        StepVerifier.create(cache.get(Flux.just("K1", "K2")).map(CacheEntry::getValue))
            .assertNext(actual -> assertEquals(11, actual))
            .assertNext(actual -> assertEquals(12, actual))
            .verifyComplete();
    }

    @Test
    @DisplayName("Get with empty Flux returns no entries")
    public void getWithEmptyFlux_returnsNoEntries() {
        var loopResources = LoopResources.create("prefix");
        FluxCache<Integer> cache = new FluxCacheImpl<>(loopResources);

        // Act & Assert: nothing emitted when keys flux is empty
        StepVerifier.create(cache.get(Flux.empty())).expectNextCount(0).verifyComplete();
    }

    @Test
    @DisplayName("Get missing key returns empty cache entry")
    public void getMissingKey_shouldReturnEmptyCacheEntry() {
        // Arrange: create cache and put limited keys
        var loopResources = LoopResources.create("prefix");
        FluxCache<Integer> cache = new FluxCacheImpl<>(loopResources);

        var testEntries = Flux.just(
            KeyValueHolder.of("KnownKey1", 1),
            KeyValueHolder.of("KnownKey2", 2)
        );

        StepVerifier.create(
            cache.put(testEntries)
                .map(CacheEntry::getKey)
        ).expectNextCount(2).verifyComplete();

        // Act & Assert: get on a missing key returns an empty entry (value == null)
        StepVerifier.create(
            cache.get(Flux.just("UnknownKey"))
        ).assertNext(entry -> assertTrue(entry.isEmpty()))
         .verifyComplete();
    }

    @Test
    @DisplayName("Putting an entry with null key throws NullPointerException")
    public void putWithNullKey_shouldThrowNullPointerException() {
        // Arrange: a cache - a null key is not supported by CacheEntryImpl; a NPE is expected.
        var loopResources = LoopResources.create("prefix");
        FluxCache<Integer> cache = new FluxCacheImpl<>(loopResources);

        var entryWithNullKey = Flux.just(KeyValueHolder.of((String) null, 99));

        // Act & Assert: attempt to put a null key into the cache should result in an NPE
        StepVerifier.create(cache.put(entryWithNullKey))
            .expectError(NullPointerException.class)
            .verify();
    }

    @Test
    @DisplayName("Putting an entry with null value throws NullPointerException")
    public void putWithNullValue_shouldThrowNullPointerException() {
        // Arrange: a cache - a null value is not supported by CacheEntryImpl; a NPE is expected.
        var loopResources = LoopResources.create("prefix");
        FluxCache<Integer> cache = new FluxCacheImpl<>(loopResources);

        var entryWithNullValue = Flux.just(KeyValueHolder.<String, Integer>of("KeyNullValue", null));

        // Act & Assert: attempt to put an entry with null value should result in an NPE
        StepVerifier.create(cache.put(entryWithNullValue))
            .expectError(NullPointerException.class)
            .verify();
    }

    @Test
    @DisplayName("Constructor with null LoopResources throws NullPointerException")
    public void constructor_withNullLoopResources_shouldThrowNullPointerException() {
        // Exceptional behavior: creating the cache with a null LoopResources should lead to NPE
        assertThrows(NullPointerException.class, () -> new FluxCacheImpl<Integer>(null));
    }
}
