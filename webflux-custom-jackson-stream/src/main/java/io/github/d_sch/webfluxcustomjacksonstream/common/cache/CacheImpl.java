package io.github.d_sch.webfluxcustomjacksonstream.common.cache;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

@Slf4j
public class CacheImpl<T> implements Cache<T> {

    private LRUCacheMap<String, T> cacheMap = new LRUCacheMap<>(new HashMap<>());
    private Scheduler scheduler = Schedulers.newSingle(CacheImpl.class.getName() + "-" + UUID.randomUUID());

    protected Mono<T> get(String key) {
        return Mono.justOrEmpty(cacheMap.get(key));
    }

    protected Mono<T> put(String key, T value) {
        log.debug("Put: Key: {}, Value: {}", key, value);
        return Mono.justOrEmpty(cacheMap.put(key, value));
    }

    private <R, V> Flux<V> publishOnCacheScheduler(Flux<R> flux, Function<Flux<R>, Flux<V>> transformer) {
        return flux
            //Ensure single cache thread
            .publishOn(scheduler)
            .transform(transformer)
            //Publish result outside of cache thread
            .publishOn(Schedulers.single());
    }

    private Flux<T> getFromFlux(Flux<String> flux) {
        return flux
            .flatMap(key -> get(key));
    }

    private Flux<T> putFromFlux(Flux<Tuple2<String, T>> flux) {
        return flux
            .flatMap(entry -> put(entry.getT1(), entry.getT2()));
    }

    @Override
    public Flux<T> get(Flux<String> keys) {
        return keys
            .transform(x -> publishOnCacheScheduler(x, this::getFromFlux));
    }

    @Override
    public Flux<T> put(Flux<Tuple2<String, T>> entries) {
        return entries
            .transform(x -> publishOnCacheScheduler(x, this::putFromFlux));
    }
}
