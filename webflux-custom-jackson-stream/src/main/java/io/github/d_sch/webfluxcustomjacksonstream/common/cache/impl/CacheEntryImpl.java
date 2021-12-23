package io.github.d_sch.webfluxcustomjacksonstream.common.cache.impl;

import java.time.Instant;

import io.github.d_sch.webfluxcustomjacksonstream.common.cache.CacheEntry;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class CacheEntryImpl<K, T> implements CacheEntry<K, T> {
    
    @Getter
    @Setter
    CacheEntry<K, T> next;

    @Getter
    @Setter
    CacheEntry<K, T> previous;
    
    CacheEntryImpl() {
    }

    @Getter
    @Setter
    @NonNull
    Instant entryExpiresAt;

    @Getter
    @Setter
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

