package io.github.d_sch.webfluxcustomjacksonstream.common.cache;

import io.github.d_sch.webfluxcustomjacksonstream.common.cache.impl.LRUCacheMap;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

public interface FluxCache<T> {

    Flux<T> get(Flux<String> keys);
    Flux<T> put(Flux<Tuple2<String, T>> entries);

    default <K> LRUCacheMap.LRUCacheMapBuilder<K, T> lruCache() {
        return LRUCacheMap.builder();
    }

}
