package io.github.d_sch.webfluxcached.common.cached;

import java.util.Map;
import java.util.function.Function;

import io.github.d_sch.webfluxcached.common.SchedulerContext;
import lombok.NonNull;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.netty.resources.LoopResources;
import reactor.util.function.Tuples;

//Ensure single        
public class DeduplicateFlux<K, T> implements Disposable {
        
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
