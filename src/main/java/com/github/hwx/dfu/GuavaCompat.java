package com.github.hwx.dfu;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

public final class GuavaCompat {

    private GuavaCompat() {}

    public static <K, V> ImmutableMap.Builder<K, V> mapBuilder() {
        return new KeepingLastMapBuilder<>(4);
    }

    public static <K, V> ImmutableMap.Builder<K, V> mapBuilderWithExpectedSize(int expectedSize) {
        return new KeepingLastMapBuilder<>(expectedSize);
    }

    public static <E> ImmutableList.Builder<E> listBuilderWithExpectedSize(int expectedSize) {
        return ImmutableList.builder();
    }

    public static <K, V> ImmutableMap<K, V> buildKeepingLast(ImmutableMap.Builder<K, V> builder) {
        if (!(builder instanceof KeepingLastMapBuilder)) {
            throw new IllegalStateException("DFU map builder was not shimmed");
        }
        return builder.build();
    }

    public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
            Function<? super T, ? extends K> keyFunction, Function<? super T, ? extends V> valueFunction) {
        Supplier<LinkedHashMap<K, V>> supplier = LinkedHashMap::new;
        BiConsumer<LinkedHashMap<K, V>, T> accumulator = (map,
                element) -> putUnique(map, keyFunction.apply(element), valueFunction.apply(element));
        BinaryOperator<LinkedHashMap<K, V>> combiner = (left, right) -> {
            for (Map.Entry<K, V> entry : right.entrySet()) {
                putUnique(left, entry.getKey(), entry.getValue());
            }
            return left;
        };
        return Collector.of(supplier, accumulator, combiner, ImmutableMap::copyOf);
    }

    public static boolean isSupertypeOf(TypeToken<?> type, TypeToken<?> other) {
        return type.isAssignableFrom(other);
    }

    private static <K, V> void putUnique(Map<K, V> map, K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (map.containsKey(key)) {
            throw new IllegalArgumentException("Multiple entries with same key: " + key);
        }
        map.put(key, value);
    }

    private static int mapCapacity(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize cannot be negative");
        }
        if (expectedSize < 3) {
            return expectedSize + 1;
        }
        if (expectedSize < 1 << 30) {
            return expectedSize + expectedSize / 3;
        }
        return Integer.MAX_VALUE;
    }

    private static final class KeepingLastMapBuilder<K, V> extends ImmutableMap.Builder<K, V> {

        private final LinkedHashMap<K, V> entries;

        private KeepingLastMapBuilder(int expectedSize) {
            entries = new LinkedHashMap<>(mapCapacity(expectedSize));
        }

        @Override
        public KeepingLastMapBuilder<K, V> put(K key, V value) {
            entries.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
            return this;
        }

        @Override
        public KeepingLastMapBuilder<K, V> put(Map.Entry<? extends K, ? extends V> entry) {
            return put(entry.getKey(), entry.getValue());
        }

        @Override
        public KeepingLastMapBuilder<K, V> putAll(Map<? extends K, ? extends V> map) {
            for (Map.Entry<? extends K, ? extends V> entry : map.entrySet()) {
                put(entry);
            }
            return this;
        }

        @Override
        public ImmutableMap<K, V> build() {
            return ImmutableMap.copyOf(entries);
        }
    }
}
