package io.github.d_sch.webfluxcached.common.cache.internal;

import java.time.Instant;
import java.util.Map;

public interface CacheEntry<K, T> extends Map.Entry<K, T> {

    Instant getEntryExpiresAt();

    Instant getValueExpiresAt();

    default boolean isEmpty() {
        return getValue() == null;
    }

    default boolean isEntryExpired() {
        return Instant.now().isAfter(getEntryExpiresAt());
    }

    default boolean isValueExpired() {
        return Instant.now().isAfter(getValueExpiresAt());
    }

    static <K, T> CacheEntry<K, T> empty(K key) {
        return new CacheEntryImpl<>(Instant.now(), Instant.now(), key, null);
    }
}
