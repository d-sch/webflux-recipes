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

package io.github.d_sch.webfluxcached.common;

import java.util.Map;

public class KeyValueHolder<K, T> implements Map.Entry<K, T> {

    private K key;
    private T value;

    public KeyValueHolder(K key, T value) {
        this.key = key;
        this.value = value;
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
        return null;
    }
    
    public static <K, T> Map.Entry<K, T> of (K key, T value) {
        return new KeyValueHolder<K, T>(key, value);
    }

}
