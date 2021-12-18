import java.util.function.Consumer;

public interface ThrowableConsumer<T> {
    accept(T value) throws Exception;

    static <T> Consumer<T> wrap(ThrowableConsumer<T> consumer) {
        return (value) -> {
            try {
                return consumer.accept(value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
