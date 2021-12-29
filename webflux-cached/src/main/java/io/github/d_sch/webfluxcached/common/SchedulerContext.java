package io.github.d_sch.webfluxcached.common;

import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

import lombok.Builder;
import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Builder
public final class SchedulerContext {

    @Getter
    private Scheduler scheduler;
    private Scheduler publishOnScheduler;

    public static final Function<String, SchedulerContext> SINGLE_BUILDER = 
            name -> {
                return SchedulerContext
                    .builder()
                    .scheduler(Schedulers.newSingle(name))
                    .publishOnScheduler(Schedulers.single())
                    .build();
            };

    public static final BiFunction<Executor, Executor, SchedulerContext> EXECUTOR_BUILDER = 
    (scheduler, publishOnScheduler) -> {
        return SchedulerContext
            .builder()
            .scheduler(Schedulers.fromExecutor(scheduler))
            .publishOnScheduler(Schedulers.fromExecutor(publishOnScheduler))
            .build();
    };    

    public <R, V> Flux<V> transform(Flux<R> flux, Function<Flux<R>, Flux<V>> transformer) {
        return transform(scheduler, flux, transformer, publishOnScheduler);
    }

    private <R, V> Flux<V> transform(Scheduler schedulerBefore, Flux<R> flux, Function<Flux<R>, Flux<V>> transformer, Scheduler schedulerAfter) {
        return flux
            //Ensure single cache thread
            .publishOn(schedulerBefore)
            .transform(transformer)
            //Publish result outside of cache thread
            .publishOn(schedulerAfter)
            .subscribeOn(schedulerAfter);
    }

}
