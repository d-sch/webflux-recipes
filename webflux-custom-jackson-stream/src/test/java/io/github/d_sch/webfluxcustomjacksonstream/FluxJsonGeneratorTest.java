package io.github.d_sch.webfluxcustomjacksonstream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;

import io.github.d_sch.webfluxcustomjacksonstream.common.JsonWriter;
import io.github.d_sch.webfluxcustomjacksonstream.common.cache.Cache;
import io.github.d_sch.webfluxcustomjacksonstream.common.cache.CacheImpl;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class FluxJsonGeneratorTest {

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

    public static abstract class Cached<K, T> {
        @NonNull
        CachedConfig cachedConfig;

        Cache<T> cache = new CacheImpl<>();

        public Flux<T> get(Flux<K> keyFlux) {
            return cache.get(
                keyFlux
                    .map(key -> cacheKey(key))
            );
        }

        protected Flux<T> put(Flux<Tuple2<K, T>> entryFlux) {
            return cache.put(
                entryFlux
                    .map(entry -> entry.mapT1(key -> cacheKey(key)))
            );
        }

        protected abstract Flux<T> find(Flux<K> keyFlux);

        public abstract String cacheKey(K key);
    }

    @Test
    public void test() {
        Flux<DataBuffer> source = Flux.defer(() -> Flux.range(1, 5))
            .as(inFlux -> JsonWriter.asDataBufferFlux(inFlux));
        
        StepVerifier.create(source).assertNext(
            actual -> assertEquals("[1,2,3,4,5]", actual.toString(StandardCharsets.UTF_8))
        ).verifyComplete();
    }

    @Test
    public void testCache() {
        Cache<Integer> cache = new CacheImpl<>();

        var testKeys = Flux.just(
            "Key 1", "Key 2", "Key 3", "Key 4", "Key 5"
        );
        
        var testEntries = Flux.just(
            Tuples.of("Key 1", 1),
            Tuples.of("Key 2", 2),
            Tuples.of("Key 3", 3),
            Tuples.of("Key 4", 4),
            Tuples.of("Key 5", 5)
        );

        var testEntries2 = Flux.just(
            Tuples.of("Key 1", 6),
            Tuples.of("Key 2", 7),
            Tuples.of("Key 3", 8),
            Tuples.of("Key 4", 9),
            Tuples.of("Key 5", 10)
        );
        
        var testEntries3 = Flux.just(
            Tuples.of("Key 11", 11),
            Tuples.of("Key 12", 12),
            Tuples.of("Key 13", 13),
            Tuples.of("Key 14", 14),
            Tuples.of("Key 15", 15)
        );

        StepVerifier.create(
            Flux.just(testEntries, testEntries2, testEntries3)  
                .publishOn(Schedulers.single())
                .parallel()
                .doOnNext(value -> log.info("Before put"))
                .flatMap(entries -> 
                    cache.put(entries)
                ).doOnNext(value -> log.info("Parallel Result {}", value)).sequential().publishOn(Schedulers.single())
                .doOnNext(value -> log.info("Result {}", value))
            ).assertNext(
                actual -> assertEquals(1, actual)
            ).assertNext(
                actual -> assertEquals(2, actual)
            ).assertNext(
                actual -> assertEquals(3, actual)
            ).assertNext(
                actual -> assertEquals(4, actual)
            ).assertNext(
                actual -> assertEquals(5, actual)
            ).verifyComplete();

        StepVerifier.create(
            Mono.just(testKeys)
                .flatMapMany(keys -> 
                    cache.get(keys)
                ).subscribeOn(Schedulers.single()
                ).doOnNext(
                    value -> log.info("Result {}", value)
                )
        ).assertNext(
            actual -> assertEquals(1, actual)
        ).assertNext(
            actual -> assertEquals(2, actual)
        ).assertNext(
            actual -> assertEquals(3, actual)
        ).assertNext(
            actual -> assertEquals(4, actual)
        ).assertNext(
            actual -> assertEquals(5, actual)
        ).verifyComplete();

        testEntries = Flux.just(
            Tuples.of("Key 1", 6),
            Tuples.of("Key 2", 7),
            Tuples.of("Key 3", 8),
            Tuples.of("Key 4", 9),
            Tuples.of("Key 5", 10)
        );

        StepVerifier.create(
            Mono.just(testEntries)
                .publishOn(Schedulers.single())
                .flatMapMany(entries -> 
                    cache.put(entries)
                ).subscribeOn(Schedulers.single()
                ).doOnNext(value -> log.info("Result {}", value))
        ).assertNext(
            actual -> assertEquals(6, actual)
        ).assertNext(
            actual -> assertEquals(7, actual)
        ).assertNext(
            actual -> assertEquals(8, actual)
        ).assertNext(
            actual -> assertEquals(9, actual)
        ).assertNext(
            actual -> assertEquals(10, actual)
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
        ).assertNext(
            actual -> assertEquals(6, actual)
        ).assertNext(
            actual -> assertEquals(7, actual)
        ).assertNext(
            actual -> assertEquals(8, actual)
        ).assertNext(
            actual -> assertEquals(9, actual)
        ).assertNext(
            actual -> assertEquals(10, actual)
        ).verifyComplete();
    }
}
