/*
 * Copyright 2021 - 2023 d-sch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and,
 * limitations under the License.
 */

package io.github.d_sch.webfluxcached.common.cache.internal;

import java.util.Iterator;

public class SimpleLinkedQueue<K, T> implements Iterable<InternalCacheEntry<K,T>> {
       
    private InternalCacheEntry<K,T> first;
    private InternalCacheEntry<K,T> last;

    public SimpleLinkedQueue() {
        this.first = new CacheEntryImpl<K,T>();
        this.last = new CacheEntryImpl<K,T>();
        this.first.setNext(this.last);
        this.last.setPrevious(this.first);
    }

    protected InternalCacheEntry<K,T> insertFirst(InternalCacheEntry<K,T> entry) {
        return insertBefore(first.getNext(), entry);
    }

    protected InternalCacheEntry<K,T> insertBefore(InternalCacheEntry<K, T> next, InternalCacheEntry<K, T> entry) {
        entry.setPrevious(next.getPrevious());
        next.setPrevious(entry);
        entry.setNext(next);
        return entry;
    }

    protected InternalCacheEntry<K,T> insertAfter(InternalCacheEntry<K, T> previous, InternalCacheEntry<K, T> entry) {
        entry.setNext(previous.getNext());
        previous.setNext(entry);
        entry.setPrevious(previous);
        return entry;
    }

    protected InternalCacheEntry<K,T> remove(InternalCacheEntry<K, T> entry) {
        if (entry.getPrevious() == null) {
            //first entry
            first = entry.getNext();
            first.setPrevious(null);
        } else if (entry.getNext() == null) {
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

    protected InternalCacheEntry<K,T> appendLast(InternalCacheEntry<K,T> entry) {
        return insertAfter(last.getPrevious(), entry);
    }

    @Override
    public Iterator<InternalCacheEntry<K, T>>  iterator() {
        return new Iterator<InternalCacheEntry<K, T>>() {
            
            InternalCacheEntry<K,T> current = first.getNext();

            @Override
            public boolean hasNext() {
                return current.getNext() != null && current.getNext() != last;
            }

            @Override
            public InternalCacheEntry<K, T> next() {
                var result = current;
                current = current.getNext();
                return result;
            }
            
        };
    }
}

