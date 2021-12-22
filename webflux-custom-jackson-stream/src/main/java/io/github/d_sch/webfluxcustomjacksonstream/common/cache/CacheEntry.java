package io.github.d_sch.webfluxcustomjacksonstream.common.cache;

import java.time.Instant;
import java.util.Map;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CacheEntry<K, T> implements Map.Entry<K, T> {
    CacheEntry<K, T> next;
    CacheEntry<K, T> previous;
    
    CacheEntry() {
    }

    @Getter
    @NonNull
    Instant entryExpiresAt;

    @Getter
    @NonNull
    Instant valueExpiresAt;

    @NonNull
    K key;

    @NonNull
    T value;

    public boolean isEntryExpired() {
        return Instant.now().isAfter(entryExpiresAt);
    }

    public boolean isValueExpired() {
        return Instant.now().isAfter(valueExpiresAt);
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public T setValue(T value) {
        this.value = value;
        return this.value;
    }
}

