package io.github.d_sch.webfluxcustomjacksonstream.common.cache.internal;

import java.time.Instant;
import java.util.Map;

public interface CacheEntry<K, T> extends Map.Entry<K, T> {

    Instant getEntryExpiresAt();

    Instant getValueExpiresAt();

    default boolean isEntryExpired() {
        return Instant.now().isAfter(getEntryExpiresAt());
    }

    default boolean isValueExpired() {
        return Instant.now().isAfter(getValueExpiresAt());
    }
}
