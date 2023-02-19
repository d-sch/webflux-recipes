package io.github.d_sch.webfluxcustomjacksonstream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.client.reactive.ReactorResourceFactory;

import io.github.d_sch.webfluxcached.common.KeyValueHolder;
import io.github.d_sch.webfluxcached.common.SchedulerContext;
import io.github.d_sch.webfluxcached.common.cache.FluxCache;
import io.github.d_sch.webfluxcached.common.cache.impl.FluxCacheImpl;
import io.github.d_sch.webfluxcached.common.cache.internal.CacheEntry;
import io.github.d_sch.webfluxcustomjacksonstream.common.JsonWriter;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.resources.LoopResources;
import reactor.test.StepVerifier;
import reactor.util.concurrent.Queues;
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

        @NonNull
        ReactorResourceFactory reactorResourceFactory;

        FluxCache<T> cache = new FluxCacheImpl<>(reactorResourceFactory.getLoopResources());

        DeduplicateFlux<K, T> lookup = new DeduplicateFlux<>(reactorResourceFactory.getLoopResources(), this::doLookup);

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
                                lookup(batchFlux), 
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

        //Ensure single        
        private static class DeduplicateFlux<K, T> implements Disposable {
            
            @NonNull
            LoopResources loopResources = LoopResources.create("prefix");

            Map<K, Sinks.One<Map.Entry<K, T>>> resultSinkMap;            
            Sinks.Many<Map.Entry<K, Sinks.One<T>>> distinctSink;

            Disposable distinctResultFlux;

            SchedulerContext schedulerContext = SchedulerContext.EXECUTOR_BUILDER.apply(
                loopResources.onServer(true).next(),
                loopResources.onServer(true)
            );

            public DeduplicateFlux(LoopResources loopResources, Function<Flux<K>, Flux<Map.Entry<K, T>>> target) {
                this.loopResources = loopResources;
                distinctSink = Sinks.many().unicast().onBackpressureBuffer();                
                distinctResultFlux = 
                    distinctSink
                        .asFlux()
                        .map(
                            tuple -> {
                                tuple.getValue()
                                    .asMono()
                                    .subscribe(
                                        ignore -> {}, 
                                        ignore -> {}, 
                                        () -> resultSinkMap.remove(tuple.getKey())
                                    );
                                return tuple.getKey();
                            }
                        ).transform(target)
                        .subscribe(
                            tuple -> {
                                resultSinkMap.get(tuple.getKey()).emitValue(tuple, Sinks.EmitFailureHandler.FAIL_FAST);
                            }
                        );                
            }

            protected Flux<Map.Entry<K, T>> deduplicate(Flux<K> flux) {
                return flux.transform(
                    fluxBefore -> this.schedulerContext.transform(fluxBefore, inFlux -> dedup(resultSinkMap, fluxBefore, distinctSink))
                );
            }

            protected static <K, T> Flux<Map.Entry<K, T>> dedup(Map<K, Sinks.One<Map.Entry<K, T>>> resultSinkMap, Flux<K> inFlux, Sinks.Many<Map.Entry<K, Sinks.One<T>>> outSink) {
                return inFlux.map(key -> 
                    Tuples.of(key, resultSinkMap.containsKey(key))
                ).groupBy(
                    x -> x.getT2(),
                    x -> x.getT1()
                ).flatMap(x -> {                
                    if (x.key()) {                        
                        return x.flatMap(key -> resultSinkMap.get(key).asMono());
                    } else {
                        return x.flatMap(key -> {
                            Sinks.One<Map.Entry<K, T>> sink = Sinks.one();                            
                            sink = resultSinkMap.putIfAbsent(key, sink);
                            sink
                                .asMono()
                                .subscribe(
                                    ignore -> {}, 
                                    ignore -> {}, 
                                    () -> resultSinkMap.remove(key)
                                );
                            return sink.asMono();                            
                        });
                    }
                });
            }

            @Override
            public void dispose() {
                distinctResultFlux.dispose();
            }
        }
        
        public Flux<Map.Entry<K, T>> getAll(Flux<K> keyFlux) {
            return getFromCache(keyFlux
                    .map(key -> toCacheKey(key))
                ).groupBy(
                    cacheEntry -> cacheEntry.isEmpty() && cacheEntry.isValueExpired()
                ).flatMap(
                    groupedFlux -> {
                        if (groupedFlux.key()) {
                            return                                
                                lookup(
                                    groupedFlux.map(cacheEntry -> fromCacheKey(cacheEntry.getKey()))
                                ).transform(
                                    flux -> put(flux)
                                );
                        } else {
                            return groupedFlux;                                
                        }
                    }
                ).map(cacheEntry -> KeyValueHolder.of(fromCacheKey(cacheEntry.getKey()), cacheEntry.getValue()));
        }

        public Flux<CacheEntry<String, T>> getFromCache(Flux<String> keyFlux) {
            return cache.get(
                keyFlux                    
            );
        }

        protected Flux<CacheEntry<String, T>> put(Flux<Map.Entry<K, T>> entryFlux) {
            return cache.put(
                entryFlux
                    .map(entry -> KeyValueHolder.of(toCacheKey(entry.getKey()), entry.getValue()))
            );
        }

        protected Flux<Map.Entry<K, T>> lookup(Flux<K> keyFlux) {
            return lookup.deduplicate(keyFlux);
        }

        protected abstract Flux<Map.Entry<K, T>> doLookup(Flux<K> keyFlux);

        public abstract String toCacheKey(K key);
        public abstract K fromCacheKey(String key);

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
