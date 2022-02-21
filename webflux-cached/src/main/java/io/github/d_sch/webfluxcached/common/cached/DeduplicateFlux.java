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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.github.d_sch.webfluxcached.common.SchedulerContext;
import lombok.NonNull;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.netty.resources.LoopResources;

//Ensure single        
public class DeduplicateFlux<K, T> implements Disposable {
        
    @NonNull
    LoopResources loopResources = LoopResources.create("prefix");

    Map<K, Sinks.One<Map.Entry<K, T>>> resultSinkMap = new HashMap<>();            
 
    Function<Flux<K>, Flux<Map.Entry<K, T>>> target;

    Disposable distinctResultFlux;

    SchedulerContext schedulerContext = SchedulerContext.EXECUTOR_BUILDER.apply(
        loopResources.onServer(true).next(),
        loopResources.onServer(true)
    );

    public DeduplicateFlux(LoopResources loopResources, Function<Flux<K>, Flux<Map.Entry<K, T>>> target) {
        this.loopResources = loopResources;
        this.target = target;
    }

    protected Flux<Map.Entry<K, T>> deduplicate(Flux<K> flux) {
        return flux.transform(
            fluxBefore -> this.schedulerContext.transform(fluxBefore, inFlux -> dedup(resultSinkMap, fluxBefore, target))
        );
    }

    protected static <K, T> Flux<Map.Entry<K, T>> dedup(Map<K, Sinks.One<Map.Entry<K, T>>> resultSinkMap, Flux<K> inFlux, Function<Flux<K>, Flux<Map.Entry<K, T>>> target) {
        return inFlux.groupBy(
            key -> resultSinkMap.containsKey(key)
        ).concatMap(group -> {                
            if (group.key()) {                        
                return group
                    .concatMap(key -> resultSinkMap.get(key).asMono());
            } else {
                return group
                    .transform(flux -> {
                        return target.apply(
                            flux.doOnNext(key -> {
                                Sinks.One<Map.Entry<K, T>> sink = Sinks.one();                           
                                var oldSink = resultSinkMap.putIfAbsent(key, sink);
                                if (oldSink != null)  {
                                    sink = oldSink;
                                }
                                sink
                                    .asMono()
                                    .subscribe(
                                        ignore -> {}, 
                                        ignore -> {}, 
                                        () -> resultSinkMap.remove(key)
                                    );                                                                                        
                            })
                        ).doOnNext(entry -> {
                            var sink = resultSinkMap.get(entry.getKey());
                            sink.emitValue(entry, Sinks.EmitFailureHandler.FAIL_FAST);                        
                        });
                    });
            }
        });
    }

    @Override
    public void dispose() {
        distinctResultFlux.dispose();
    }
}
