package io.github.d_sch.webfluxcustomjacksonstream.common.cache.internal;

import java.time.Instant;

interface InternalCacheEntry<K, T> extends CacheEntry<K, T> {
    void setEntryExpiresAt(Instant entryExpiresAt);
    void setValueExpiresAt(Instant valueExpiresAt);

    InternalCacheEntry<K, T> getNext();
    void setNext(InternalCacheEntry<K, T> next);
    
    InternalCacheEntry<K, T> getPrevious();
    void setPrevious(InternalCacheEntry<K, T> previous);
}