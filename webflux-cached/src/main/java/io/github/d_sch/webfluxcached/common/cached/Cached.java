/*
 * Copyright 2021 - 2021 d-sch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and,
 * limitations under the License.
 */

package io.github.d_sch.webfluxcached.common.cached;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Function;

import org.springframework.http.client.reactive.ReactorResourceFactory;
import io.github.d_sch.webfluxcached.common.KeyValueHolder;
import io.github.d_sch.webfluxcached.common.SchedulerContext;
import io.github.d_sch.webfluxcached.common.cache.FluxCache;
import io.github.d_sch.webfluxcached.common.cache.impl.FluxCacheImpl;
import io.github.d_sch.webfluxcached.common.cache.internal.CacheEntry;
import lombok.Builder;
import lombok.NonNull;
import lombok.Builder.Default;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.netty.resources.LoopResources;
import reactor.util.concurrent.Queues;
import reactor.util.function.Tuples;

@Slf4j
public abstract class Cached<K, T> implements Disposable {
    @NonNull
    CachedConfig cachedConfig;

    @NonNull
    ReactorResourceFactory reactorResourceFactory;

    FluxCache<T> cache = new FluxCacheImpl<>(reactorResourceFactory.getLoopResources());

    DeduplicateFlux<K, T> lookup = new DeduplicateFlux<>(reactorResourceFactory.getLoopResources(), this::doLookup);

    Sinks.Many<K> expiredKeySink; 

    Disposable expiredKeyBatch;

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

