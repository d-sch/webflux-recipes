package io.github.d_sch.webfluxcached.common.cache;

import java.util.Map;

import io.github.d_sch.webfluxcached.common.cache.internal.CacheEntry;
import io.github.d_sch.webfluxcached.common.cache.internal.LRUCacheMap;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

public interface FluxCache<T> {

    Flux<CacheEntry<String, T>> get(Flux<String> keys);
    Flux<CacheEntry<String, T>> put(Flux<Map.Entry<String, T>> entries);

    default <K> LRUCacheMap.LRUCacheMapBuilder<K, T> lruCache() {
        return LRUCacheMap.builder();
    }

}
