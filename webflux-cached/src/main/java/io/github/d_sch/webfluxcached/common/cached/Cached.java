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

import java.util.Map;
import java.util.function.Function;

import org.springframework.http.client.reactive.ReactorResourceFactory;

import io.github.d_sch.webfluxcached.common.KeyValueHolder;
import io.github.d_sch.webfluxcached.common.cache.FluxCache;
import io.github.d_sch.webfluxcached.common.cache.impl.FluxCacheImpl;
import io.github.d_sch.webfluxcached.common.cache.internal.CacheEntry;
import lombok.NonNull;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class Cached<K, T> {
    @NonNull
    ReactorResourceFactory reactorResourceFactory;

    final FluxCache<T> cache;

    final DeduplicateFlux<K, T> lookup;

    Sinks.Many<K> expiredKeySink; 

    Disposable expiredKeyBatch;

    private Function<K, String> toCacheKey;
    private Function<String, K> fromCacheKey;

    private K fromCacheEntry(CacheEntry<String, T> entry) {
        return fromCacheKey.apply(entry.getKey());
    }

    private Cached(ReactorResourceFactory reactorResourceFactory, Function<K, String> toCacheKey, Function<String, K> fromCacheKey, Function<Flux<K>, Flux<Map.Entry<K,T>>> lookup) {
        this.reactorResourceFactory = reactorResourceFactory;
        this.toCacheKey = toCacheKey;
        this.fromCacheKey = fromCacheKey;
        this.lookup = new DeduplicateFlux<>(reactorResourceFactory.getLoopResources(), lookup);
        this.cache = new FluxCacheImpl<>(reactorResourceFactory.getLoopResources());
    }
    
    public static <K, T> Cached<K, T> build(ReactorResourceFactory reactorResourceFactory, Function<K, String> toCacheKey, Function<String, K> fromCacheKey, Function<Flux<K>, Flux<Map.Entry<K,T>>> lookup) {
        return new Cached<>(reactorResourceFactory, toCacheKey, fromCacheKey, lookup);
    }

    public Flux<Map.Entry<K, T>> getAll(Flux<K> keyFlux) {
        return keyFlux
            .map(toCacheKey::apply)
            .transform(this::getFromCache)
            .groupBy(
                cacheEntry -> cacheEntry.isEmpty() || cacheEntry.isValueExpired()
            ).flatMap(
                groupedFlux -> {
                    if (groupedFlux.key()) {
                        return  
                            groupedFlux.map(this::fromCacheEntry)
                                .transform(this::lookup)                              
                                .transform(
                                    this::put
                                );
                    } else {
                        return groupedFlux;                                
                    }
                }
            ).map(cacheEntry -> KeyValueHolder.of(fromCacheEntry(cacheEntry), cacheEntry.getValue()));
    }

    private Flux<CacheEntry<String, T>> getFromCache(Flux<String> keyFlux) {
        return keyFlux
                .transform(cache::get);
    }

    protected Flux<CacheEntry<String, T>> put(Flux<Map.Entry<K, T>> entryFlux) {
        return entryFlux
                .map(entry -> KeyValueHolder.of(toCacheKey.apply(entry.getKey()), entry.getValue()))
                .transform(cache::put);
    }

    protected Flux<Map.Entry<K, T>> lookup(Flux<K> keyFlux) {
        return lookup.deduplicate(keyFlux);
    }
}

