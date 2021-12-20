package io.github.d_sch.webfluxcustomjacksonstream.common;
import java.util.function.Consumer;

public interface ThrowingConsumer<T> {
    void accept(T value) throws Exception;

    static <T> Consumer<T> wrap(ThrowingConsumer<T> consumer) {
        return (value) -> {
            try {
                consumer.accept(value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
