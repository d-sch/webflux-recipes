package io.github.d_sch.webfluxcustomjacksonstream.common.cache;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

public interface Cache<T> {

    Flux<T> get(Flux<String> keys);
    Flux<T> put(Flux<Tuple2<String, T>> entries);

}
