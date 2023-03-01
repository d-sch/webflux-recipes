package io.github.d_sch.webfluxcommon.common;

import java.util.function.Predicate;

import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;

public final class Predicates {
    public static <T> void set(T value, Predicate<T> condition, Consumer<T> action) {
        if(condition.test(value)){
            action.accept(value);
        }
    }
}
