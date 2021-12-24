package io.github.d_sch.webfluxcustomjacksonstream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;

import io.github.d_sch.webfluxcustomjacksonstream.common.JsonWriter;
import io.github.d_sch.webfluxcustomjacksonstream.common.cache.FluxCache;
import io.github.d_sch.webfluxcustomjacksonstream.common.cache.impl.FluxCacheImpl;
import io.github.d_sch.webfluxcustomjacksonstream.common.cache.internal.CacheEntry;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Builder.Default;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.concurrent.Queues;
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

    public static abstract class Cached<K, T> implements Disposable {
        @NonNull
        CachedConfig cachedConfig;

        FluxCache<T> cache = new FluxCacheImpl<>();

        Sinks.Many<K> expiredKeySink; 

        Disposable expiredKeyBatch;

        public Cached() {
            expiredKeySink = Sinks.many().unicast().onBackpressureBuffer(Queues.<K>small().get(), this);
            expiredKeyBatch = 
                    BatchFlux
                        .<K>builder()
                        .build()
                        .batch(expiredKeySink.asFlux())
                        .subscribe(
                            batchFlux -> 
                                find(batchFlux), 
                                throwable -> log.warn("Error in expired key batch", throwable), 
                                () -> {
                                    expiredKeyBatch = null;
                                    log.info("Expired key batch completed.");
                                }
                        );
        }

        @Builder
        private static class BatchFlux<T> {
            
            @Default
            private ChronoUnit batchDurationChronoUnit = ChronoUnit.SECONDS;
            @Default
            private long batchDuration = 1;
            @Default
            private int batchSize = 32;

            public Flux<Flux<T>> batch(Flux<T> sourceFlux) {
                return sourceFlux.windowTimeout(
                            batchSize, 
                            Duration.of(
                                batchDuration, 
                                batchDurationChronoUnit)
                );
            }

        }
        
        public Flux<CacheEntry<String, T>> get(Flux<K> keyFlux) {
            return cache.get(
                keyFlux
                    .map(key -> toCacheKey(key))
            );
        }

        protected Flux<CacheEntry<String, T>> put(Flux<Tuple2<K, T>> entryFlux) {
            return cache.put(
                entryFlux
                    .map(entry -> entry.mapT1(key -> toCacheKey(key)))
            );
        }

        protected abstract Flux<T> find(Flux<K> keyFlux);

        public abstract String toCacheKey(K key);
        public abstract K fromCacheKey(K key);

        @Override
        public void dispose() {
            expiredKeyBatch.dispose();
        }
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
        FluxCache<Integer> cache = new FluxCacheImpl<>();

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
                ).doOnNext(entry -> log.info("Parallel Result {}", entry)).sequential().publishOn(Schedulers.single())
                .doOnNext(entry -> log.info("Result {}", entry))
                .map(entry -> entry.getValue())
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
            ).expectNextCount(10).verifyComplete();

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
            Tuples.of("Key 1", 16),
            Tuples.of("Key 2", 17),
            Tuples.of("Key 3", 18),
            Tuples.of("Key 4", 19),
            Tuples.of("Key 5", 20)
        );

        StepVerifier.create(
            Mono.just(testEntries)
                .publishOn(Schedulers.single())
                .flatMapMany(entries -> 
                    cache.put(entries)
                ).subscribeOn(Schedulers.single()
                ).doOnNext(value -> log.info("Result {}", value))
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
    }
}
