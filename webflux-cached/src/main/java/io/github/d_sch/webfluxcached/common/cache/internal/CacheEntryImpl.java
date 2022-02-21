/*
 * Copyright 2021 - 2021 d-sch
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

import java.time.Instant;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class CacheEntryImpl<K, T> implements InternalCacheEntry<K, T> {
    
    @Getter
    @Setter
    InternalCacheEntry<K, T> next;

    @Getter
    @Setter
    InternalCacheEntry<K, T> previous;
    
    CacheEntryImpl() {
        this.entryExpiresAt = Instant.MAX;
        this.valueExpiresAt = Instant.MAX;
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

