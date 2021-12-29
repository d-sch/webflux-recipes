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
