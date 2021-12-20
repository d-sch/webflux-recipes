package io.github.d_sch.webfluxcustomjacksonstream.common;
import java.util.function.Supplier;

public interface ThrowingSupplier<T> {
    T get() throws Exception;

    static <T> Supplier<T> wrap(ThrowingSupplier<T> consumer) {
        return () -> {
            try {
                return consumer.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
