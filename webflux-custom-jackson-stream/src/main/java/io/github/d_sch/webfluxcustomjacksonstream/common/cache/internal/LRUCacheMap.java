package io.github.d_sch.webfluxcustomjacksonstream.common.cache.internal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Builder.Default;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class LRUCacheMap<K, T> extends CacheMap<K, T> {

    @Getter
    @Default
    private ChronoUnit valueExpirationChronoUnit = ChronoUnit.FOREVER;

    @Getter
    @Default
    private long valueExpirationDuration = 0;

    @Getter
    @Default
    private ChronoUnit entryExpirationChronoUnit = ChronoUnit.FOREVER;

    @Getter
    @Default
    private long entryExpirationDuration = 0;

    @Getter
    @Default
    private ChronoUnit cacheCleanupChronoUnit = ChronoUnit.FOREVER;
    
    @Getter
    @Default
    private long cacheCleanupDuration = 0;

    @NonNull
    private Map<K, InternalCacheEntry<K, T>> map;

    @Default
    private boolean cleanUpExpiredValue = true;

    protected InternalCacheEntry<K, T> putNew(K key, T value) {
        var entry = appendLast(
            new CacheEntryImpl<>(
                    entryExpirationChronoUnit.equals(ChronoUnit.FOREVER) 
                        ? Instant.MAX 
                        : Instant.now().plus(entryExpirationDuration, entryExpirationChronoUnit),
                    valueExpirationChronoUnit.equals(ChronoUnit.FOREVER) 
                        ? Instant.MAX
                        : Instant.now().plus(valueExpirationDuration, valueExpirationChronoUnit),
                    key, 
                    value
                )
        );
        map.put(key, entry);
        return entry;
    }

    public static Instant calculateExpirationTime(Instant instant, long duration, ChronoUnit chronoUnit) {
        return chronoUnit.equals(ChronoUnit.FOREVER) 
            ? Instant.MAX 
            : instant.plus(duration, chronoUnit);
    }

    protected InternalCacheEntry<K, T> touch(InternalCacheEntry<K, T> entry) {
        entry.setEntryExpiresAt(calculateExpirationTime(Instant.now(), entryExpirationDuration, entryExpirationChronoUnit));
        return appendLast(entry);
    }

    protected InternalCacheEntry<K, T> update(InternalCacheEntry<K, T> entry, T value) {
        entry.setValueExpiresAt(calculateExpirationTime(Instant.now(), valueExpirationDuration, valueExpirationChronoUnit));
        entry.setValue(value);
        return this.touch(entry);
    }

    protected InternalCacheEntry<K, T> remove(InternalCacheEntry<K, T> entry) {
        super.remove(entry);
        return map.remove(entry.getKey());
    }

    public CacheEntry<K, T> get(K key) {
        log.debug("Get: Key: {}", key);
        //Lookup in map
        if (map.containsKey(key)) {
            var entry = map.get(key);
            entry = this.touch(entry);
            //Check value expiration
            if (entry.isValueExpired()) {
                log.debug("Get: Key: {}, Return value: {}", entry.getKey(), entry.getValue());
                return entry;
            } else {
                log.debug("Get: Key: {}, Entry value expired", entry.getKey());
                if (cleanUpExpiredValue) {
                    remove(entry);
                    return null;
                }
                return entry;
            }
        }
        log.debug("Get Key: {}, Not available");
        return null;
    }

    public CacheEntry<K, T> put(K key, T value) {
        log.debug("Put: Key: {}, Value: {}", key, value);
        //Lookup in map
        if (map.containsKey(key)) {
            var entry = map.get(key);                
            log.debug("Put: Key: {}, Value: {}, Replacing current entry value: {}", key, value, entry.getValue());
            //Update entry
            update(entry, value);
            return entry;
        } else {
            //New entry
            log.debug("Put: Key: {}, Value: {}, Adding new entry", key, value);
            return putNew(key, value);
        }
    }

    public void cleanUp() {
        var iterator = this.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.isEntryExpired()) {
                log.debug("Clean up: Key: {} Value: {}", entry.getKey(), entry.getValue());
                remove(entry);
            } else {
                break;
            }
        }
    }

}

