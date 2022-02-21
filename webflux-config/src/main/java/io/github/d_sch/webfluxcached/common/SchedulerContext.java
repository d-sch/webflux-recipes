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
/**
 * SchedulerContext
 * 
 * Ensure specific scheduler for publisher and subscriber thread execution on entry
 * and specific scheduler for publisher and subscriber afterwards.
 * 
 * If you depend on global resources and synchronized access is required.
 * 
 * One example whould be to create a single thread scheduler to make sure only one
 * thread is able to access a resource sequentially.
 * 
 */
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

    private static <R, V> Flux<V> transform(Scheduler schedulerBefore, Flux<R> flux, Function<Flux<R>, Flux<V>> transformer, Scheduler schedulerAfter) {
        return flux
            //Ensure custom scheduler before transformation
            .publishOn(schedulerBefore)
            .subscribeOn(schedulerBefore)
            .transform(transformer)
            //Ensure custom scheduler after transformation
            .publishOn(schedulerAfter)
            .subscribeOn(schedulerAfter);
    }

}
