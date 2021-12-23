package io.github.d_sch.webfluxcustomjacksonstream.common;

public interface ThrowingRunnable {

    void run();

    static <T> Runnable wrap(ThrowingRunnable throwingRunnable) {
        return () -> {
            try {
                throwingRunnable.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
    
}
