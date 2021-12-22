package io.github.d_sch.webfluxcustomjacksonstream.common.cache;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public class CacheMap<K, T> implements Iterable<CacheEntry<K,T>> {
       
    private CacheEntry<K,T> first;
    private CacheEntry<K,T> last;

    public CacheMap() {
        this.first = new CacheEntry<K,T>();
        this.last = new CacheEntry<K,T>();
        this.first.next = this.last;
        this.last.previous = this.first;
    }

    protected CacheEntry<K,T> insertFirst(CacheEntry<K,T> entry) {
        return insertBefore(first.next, entry);
    }

    protected CacheEntry<K,T> insertBefore(CacheEntry<K, T> next, CacheEntry<K, T> entry) {
        entry.previous = next.previous;
        next.previous = entry;
        entry.next = next;
        return entry;
    }

    protected CacheEntry<K,T> insertAfter(CacheEntry<K, T> previous, CacheEntry<K, T> entry) {
        entry.next = previous.next;
        previous.next = entry;
        entry.previous = previous;
        return entry;
    }

    protected CacheEntry<K,T> remove(CacheEntry<K, T> entry) {
        if (entry.previous == null) {
            //first entry
            first = entry.next;
            first.previous = null;
        } else if (entry.next == null) {
            //last entry
            last = entry.previous;
            last.next = null;
        } else {
            //concat entry
            entry.next.previous = entry.previous;
            entry.previous.next = entry.next;
        }
        entry.previous = null;
        entry.next = null;
        return entry;
    }

    protected CacheEntry<K,T> appendLast(CacheEntry<K,T> entry) {
        return insertAfter(last.previous, entry);
    }

    @Override
    public Iterator<CacheEntry<K, T>>  iterator() {
        return new Iterator<CacheEntry<K, T>>() {
            
            CacheEntry<K,T> current = first.next;

            @Override
            public boolean hasNext() {
                return current.next != null;
            }

            @Override
            public CacheEntry<K, T> next() {
                var result = current;
                current = current.next;
                return result;
            }
            
        };
    }
}

