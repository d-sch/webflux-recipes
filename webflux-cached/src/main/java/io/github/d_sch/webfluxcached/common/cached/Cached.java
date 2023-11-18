/*
 * Copyright 2021 - 2023 d-sch
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
import io.github.d_sch.webfluxcached.common.cache.internal.LRUCacheMap;
import lombok.NonNull;
import reactor.core.publisher.Flux;

/** 
 * Reactive cache interface.
 * Cache only supports {@link java.lang.String} as key value.
 * This class requires to provide converter from and to key value type.
 * Use the {@link LRUCacheMap.LRUCacheMapBuilder} to customize the cache parameters.
 * This way a custom {@link Map} implementation can be used as cache store.
*/
public class Cached<K, T> {
    @NonNull
    ReactorResourceFactory reactorResourceFactory;

    final FluxCache<T> cache;

    final Function<Flux<K>, Flux<Map.Entry<K,T>>> lookup;

    private Function<K, String> toCacheKey;
    private Function<String, K> fromCacheKey;

    private K fromCacheEntry(CacheEntry<String, T> entry) {
        return fromCacheKey.apply(entry.getKey());
    }

    /**
     * 
     * @param reactorResourceFactory
     * @param toCacheKey
     * @param fromCacheKey
     * @param lookup
     * @param cache
     */
    private Cached(
        ReactorResourceFactory reactorResourceFactory, 
        Function<K, String> toCacheKey, 
        Function<String, K> fromCacheKey, 
        Function<Flux<K>, Flux<Map.Entry<K,T>>> lookup,
        FluxCache<T> cache  
    ) {
        this.reactorResourceFactory = reactorResourceFactory;
        this.toCacheKey = toCacheKey;
        this.fromCacheKey = fromCacheKey;
        this.lookup = lookup;
        this.cache = cache;
    }

    private Cached(
        ReactorResourceFactory reactorResourceFactory, 
        Function<K, String> toCacheKey, 
        Function<String, K> fromCacheKey, 
        Function<Flux<K>, Flux<Map.Entry<K,T>>> lookup
    ) {
        this(
            reactorResourceFactory, 
            toCacheKey, 
            fromCacheKey, 
            lookup,
            new FluxCacheImpl<>(reactorResourceFactory.getLoopResources())
        );
    }

    private Cached(
        ReactorResourceFactory reactorResourceFactory, 
        Function<K, String> toCacheKey, 
        Function<String, K> fromCacheKey, 
        Function<Flux<K>, Flux<Map.Entry<K,T>>> lookup,
        LRUCacheMap<String, T> lruCacheMap
    ) {
        this(
            reactorResourceFactory, 
            toCacheKey, 
            fromCacheKey, 
            lookup,
            new FluxCacheImpl<>(reactorResourceFactory.getLoopResources(), lruCacheMap)
        );
    }

    /**
     * Build a Cache instance
     * @param <K> key type of cache value
     * @param <T> cache value type
     * @param reactorResourceFactory Used for cache access synchronization 
     * @param toCacheKey key type <code>T</code> to cache key type <code>String</code> transformation
     * @param fromCacheKey cache key type <code>String</code> to key type <code>T</code> transformation
     * @param lookup Function that is providing missing or expired data to be cached
     * @return The build {@link Cached} instance
     */
    public static <K, T> Cached<K, T> build(
        ReactorResourceFactory reactorResourceFactory, 
        Function<K, String> toCacheKey, 
        Function<String, K> fromCacheKey, 
        Function<Flux<K>, Flux<Map.Entry<K,T>>> lookup
    ) {
        return new Cached<>(reactorResourceFactory, toCacheKey, fromCacheKey, lookup);
    }

    /**
     * Build a Cache instance
     * @param <K> key type of cache value
     * @param <T> cache value type
     * @param reactorResourceFactory Used for cache access synchronization 
     * @param toCacheKey key type <code>T</code> to cache key type <code>String</code> transformation
     * @param fromCacheKey cache key type <code>String</code> to key type <code>T</code> transformation
     * @param lookup Function that is providing missing or expired data to be cached
     * @param  lruCacheMap Provide map used for caching
     * @return The build {@link Cached} instance
     */
    public static <K, T> Cached<K, T> build(
        ReactorResourceFactory reactorResourceFactory, 
        Function<K, String> toCacheKey, 
        Function<String, K> fromCacheKey, 
        Function<Flux<K>, Flux<Map.Entry<K,T>>> lookup,
        LRUCacheMap<String, T> lruCacheMap
    ) {
        return new Cached<>(reactorResourceFactory, toCacheKey, fromCacheKey, lookup, lruCacheMap);
    }

    /**
     * Get values for all keys of supplied Flux.
     * The stream first tries a lookup from the cache.
     * All missing or expired items are resolved by using the supplied <code>lookup</code> function and finally stored in the cache.
     * The order of the produced entries is not guaranteed.
     * 
     * @param keyFlux Flux of type <code>K</code>
     * @return Flux of resolved key of type <code>K</code> value of type <code>T</code> {@link Map.Entry} pairs
     */
    public Flux<Map.Entry<K, T>> getAll(Flux<K> keyFlux) {
        return keyFlux
            .map(toCacheKey::apply)
            .transform(this::getFromCache)
            .groupBy(
                cacheEntry -> cacheEntry.isEmpty() || cacheEntry.isEntryExpired()
            ).flatMap(
                groupedFlux -> {
                    if (groupedFlux.key()) {
                        //Lookup values for keys
                        return  
                            groupedFlux.map(this::fromCacheEntry)
                                .distinct()
                                .transform(lookup)                              
                                .transform(
                                    this::put
                                );
                    } else {
                        //Values
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
}

