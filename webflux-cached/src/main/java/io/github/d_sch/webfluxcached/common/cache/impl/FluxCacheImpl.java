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

package io.github.d_sch.webfluxcached.common.cache.impl;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import io.github.d_sch.webfluxcached.common.SchedulerContext;
import io.github.d_sch.webfluxcached.common.ThrowingRunnable;
import io.github.d_sch.webfluxcached.common.cache.FluxCache;
import io.github.d_sch.webfluxcached.common.cache.internal.CacheEntry;
import io.github.d_sch.webfluxcached.common.cache.internal.LRUCacheMap;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

@Slf4j
public class FluxCacheImpl<T> implements FluxCache<T> {

    @NonNull
    private LoopResources loopResources;

    private LRUCacheMap<String, T> cacheMap = 
            LRUCacheMap.<String, T>builder()
                .map(new HashMap<>())
                .entryExpirationChronoUnit(ChronoUnit.SECONDS)
                .entryExpirationDuration(60)
                .build();  
    
    private SchedulerContext schedulerContext;           
   
    private Disposable scheduledCleanUp;

    public FluxCacheImpl(LoopResources loopResources) {
        this.loopResources = loopResources;
        this.schedulerContext = SchedulerContext.EXECUTOR_BUILDER.apply(
            loopResources.onServer(true).next(),
            loopResources.onServer(true)
        );
    }

    protected void scheduleCleanUp() {
        if (scheduledCleanUp == null) {
            log.debug("Schedule cache cleanup.");
            scheduledCleanUp = this.schedulerContext.getScheduler().createWorker().schedule(ThrowingRunnable.wrap(
                () -> {
                    log.debug("Run cache cleanup.");                    
                    cacheMap.cleanUp();
                    scheduledCleanUp.dispose();
                    scheduledCleanUp = null;
                })
            );            
        }
    }

    protected Mono<CacheEntry<String, T>> get(String key) {
        log.debug("Get: Key: {}", key);
        scheduleCleanUp();
        return Mono.justOrEmpty(cacheMap.get(key));
    }

    protected Mono<CacheEntry<String, T>> put(String key, T value) {
        log.debug("Put: Key: {}, Value: {}", key, value);
        scheduleCleanUp();
        return Mono.justOrEmpty(cacheMap.put(key, value));
    }

    private Flux<CacheEntry<String, T>> getFromFlux(Flux<String> flux) {
        return flux
            .flatMap(key -> get(key));
    }

    private Flux<CacheEntry<String, T>> putFromFlux(Flux<Map.Entry<String, T>> flux) {
        return flux
            .flatMap(entry -> put(entry.getKey(), entry.getValue()));
    }

    @Override
    public Flux<CacheEntry<String, T>> get(Flux<String> keys) {
        return keys
            .transform(x -> schedulerContext.transform(x, this::getFromFlux));
    }

    @Override
    public Flux<CacheEntry<String, T>> put(Flux<Map.Entry<String, T>> entries) {
        return entries
            .transform(x -> schedulerContext.transform(x, this::putFromFlux));
    }
}
