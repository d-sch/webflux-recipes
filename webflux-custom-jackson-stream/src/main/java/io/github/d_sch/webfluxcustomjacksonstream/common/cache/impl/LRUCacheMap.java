package io.github.d_sch.webfluxcustomjacksonstream.common.cache.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import io.github.d_sch.webfluxcustomjacksonstream.common.cache.CacheEntry;
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
    ChronoUnit valueExpirationChronoUnit = ChronoUnit.FOREVER;

    @Getter
    @Default
    long valueExpirationDuration = 0;

    @Getter
    @Default
    ChronoUnit entryExpirationChronoUnit = ChronoUnit.FOREVER;

    @Getter
    @Default
    long entryExpirationDuration = 0;

    @Getter
    @Default
    ChronoUnit cacheCleanupChronoUnit = ChronoUnit.FOREVER;
    
    @Getter
    @Default
    long cacheCleanupDuration = 0;

    @NonNull
    Map<K, CacheEntry<K, T>> map;

    protected CacheEntry<K, T> putNew(K key, T value) {
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

    protected CacheEntry<K, T> touch(CacheEntry<K, T> entry) {
        entry.setEntryExpiresAt(calculateExpirationTime(Instant.now(), entryExpirationDuration, entryExpirationChronoUnit));
        return appendLast(entry);
    }

    protected CacheEntry<K, T> update(CacheEntry<K, T> entry, T value) {
        entry.setValueExpiresAt(calculateExpirationTime(Instant.now(), valueExpirationDuration, valueExpirationChronoUnit));
        entry.setValue(value);
        return this.touch(entry);
    }

    protected CacheEntry<K, T> remove(CacheEntry<K, T> entry) {
        super.remove(entry);
        return map.remove(entry.getKey());
    }

    protected T get(K key) {
        log.debug("Get: Key: {}", key);
        //Lookup in map
        if (map.containsKey(key)) {
            var entry = map.get(key);
            //Check expiration
            if (Instant.now().isBefore(entry.getEntryExpiresAt())) {
                entry = this.touch(entry);
                log.debug("Get: Key: {}, Return value: {}", entry.getKey(), entry.getValue());
                return entry.getValue();
            } else {
                remove(entry);
                log.debug("Get: Key: {}, Entry expired", entry.getKey());
                return null;
            }
        }
        log.debug("Get Key: {}, Not available");
        return null;
    }

    protected T put(K key, T value) {
        log.debug("Put: Key: {}, Value: {}", key, value);
        //Lookup in map
        if (map.containsKey(key)) {
            var entry = map.get(key);                
            log.debug("Put: Key: {}, Value: {}, Replacing current entry value: {}", key, value, entry.getValue());
            //Update entry
            update(entry, value);
            return entry.getValue();
        } else {
            //New entry
            log.debug("Put: Key: {}, Value: {}, Adding new entry", key, value);
            return putNew(key, value).getValue();
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

