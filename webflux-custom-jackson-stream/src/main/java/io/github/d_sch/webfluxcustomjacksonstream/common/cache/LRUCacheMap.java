package io.github.d_sch.webfluxcustomjacksonstream.common.cache;

import java.time.Duration;
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
    Duration valueExpirationDuration = Duration.of(0, ChronoUnit.FOREVER);

    @Getter
    @Default
    Duration entryExpirationDuration = Duration.of(0, ChronoUnit.FOREVER);

    @Getter
    @Default
    Duration cacheCleanupDuration = Duration.of(0, ChronoUnit.FOREVER);

    @NonNull
    Map<K, CacheEntry<K, T>> map;

    public CacheEntry<K, T> add(K key, T value) {
        return appendLast(
            new CacheEntry<>(
                    Instant.now().plus(entryExpirationDuration),
                    Instant.now().plus(valueExpirationDuration),
                    key, 
                    value
                )
        );
    }

    public CacheEntry<K, T> touch(CacheEntry<K, T> entry) {
        entry.entryExpiresAt = Instant.now().plus(entryExpirationDuration);
        return appendLast(entry);
    }

    public CacheEntry<K, T> update(CacheEntry<K, T> entry, T value) {
        entry.valueExpiresAt = Instant.now().plus(valueExpirationDuration);
        entry.value = value;
        return this.touch(entry);
    }

    public CacheEntry<K, T> remove(CacheEntry<K, T> entry) {
        remove(entry);
        return map.remove(entry.key);
    }

    protected T get(K key) {
        log.debug("Get: Key: {}", key);
        if (map.containsKey(key)) {
            var entry = map.get(key);
            if (Instant.now().isBefore(entry.entryExpiresAt)) {
                entry = this.touch(entry);
                log.debug("Get: Key: {}, Return value: {}", entry.key, entry.value);
                return entry.value;
            } else {
                remove(entry);
                log.debug("Get: Key: {}, Entry expired", entry.key);
                return null;
            }
        }
        log.debug("Get Key: {}, Not available");
        return null;
    }

    protected T put(K key, T value) {
        log.debug("Put: Key: {}, Value: {}", key, value);
        if (map.containsKey(key)) {
            var entry = map.get(key);                
            log.debug("Put: Key: {}, Value: {}, Replacing current entry value: {}", key, value, entry.value);
            update(entry, value);
            return entry.value;
        } else {
            log.debug("Put: Key: {}, Value: {}, Adding new entry", key, value);
            var entry = map.put(key, this.add(key, value));
            return entry.value;
        }
    }

    protected void cleanUp() {
        var iterator = this.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.isEntryExpired()) {
                remove(entry);
            } else {
                break;
            }
        }
    }

}

