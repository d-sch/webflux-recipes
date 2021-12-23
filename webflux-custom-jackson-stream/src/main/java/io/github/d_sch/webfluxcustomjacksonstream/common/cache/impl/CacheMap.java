package io.github.d_sch.webfluxcustomjacksonstream.common.cache.impl;

import java.util.Iterator;

import io.github.d_sch.webfluxcustomjacksonstream.common.cache.CacheEntry;

public abstract class CacheMap<K, T> implements Iterable<CacheEntry<K,T>> {
       
    private CacheEntry<K,T> first;
    private CacheEntry<K,T> last;

    public CacheMap() {
        this.first = new CacheEntryImpl<K,T>();
        this.last = new CacheEntryImpl<K,T>();
        this.first.setNext(this.last);
        this.last.setPrevious(this.first);
    }

    protected CacheEntry<K,T> insertFirst(CacheEntry<K,T> entry) {
        return insertBefore(first.getNext(), entry);
    }

    protected CacheEntry<K,T> insertBefore(CacheEntry<K, T> next, CacheEntry<K, T> entry) {
        entry.setPrevious(next.getPrevious());
        next.setPrevious(entry);
        entry.setNext(next);
        return entry;
    }

    protected CacheEntry<K,T> insertAfter(CacheEntry<K, T> previous, CacheEntry<K, T> entry) {
        entry.setNext(previous.getNext());
        previous.setNext(entry);
        entry.setPrevious(previous);
        return entry;
    }

    protected CacheEntry<K,T> remove(CacheEntry<K, T> entry) {
        if (entry.getPrevious() == null) {
            //first entry
            first = entry.getNext();
            first.setPrevious(null);
        } else if (entry.getNext()   == null) {
            //last entry
            last = entry.getPrevious();
            last.setNext(null);
        } else {
            //concat entry
            entry.getNext().setPrevious(entry.getPrevious());
            entry.getPrevious().setNext(entry.getNext());
        }
        entry.setPrevious(null);
        entry.setNext(null);
        return entry;
    }

    protected CacheEntry<K,T> appendLast(CacheEntry<K,T> entry) {
        return insertAfter(last.getPrevious(), entry);
    }

    @Override
    public Iterator<CacheEntry<K, T>>  iterator() {
        return new Iterator<CacheEntry<K, T>>() {
            
            CacheEntry<K,T> current = first.getNext();

            @Override
            public boolean hasNext() {
                return current.getNext() != null;
            }

            @Override
            public CacheEntry<K, T> next() {
                var result = current;
                current = current.getNext();
                return result;
            }
            
        };
    }
}

