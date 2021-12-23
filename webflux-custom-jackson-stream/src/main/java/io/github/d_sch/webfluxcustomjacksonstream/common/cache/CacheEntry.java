package io.github.d_sch.webfluxcustomjacksonstream.common.cache;

import java.time.Instant;
import java.util.Map;

public interface CacheEntry<K, T> extends Map.Entry<K, T> {

    Instant getEntryExpiresAt();
    void setEntryExpiresAt(Instant entryExpiresAt);

    Instant getValueExpiresAt();
    void setValueExpiresAt(Instant valueExpiresAt);

    CacheEntry<K, T> getNext();
    void setNext(CacheEntry<K, T> next);
    CacheEntry<K, T> getPrevious();
    void setPrevious(CacheEntry<K, T> previous);

    default boolean isEntryExpired() {
        return Instant.now().isAfter(getEntryExpiresAt());
    }

    default boolean isValueExpired() {
        return Instant.now().isAfter(getValueExpiresAt());
    }
}