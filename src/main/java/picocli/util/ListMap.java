package picocli.util;

import java.util.Collection;
import java.util.function.Function;

import org.apache.commons.collections4.map.ListOrderedMap;

public class ListMap<K, V> extends ListOrderedMap<K, V> {
    private static final long serialVersionUID = 1L;

    Function<V, K> keyGetter;

    public ListMap(Function<V, K> keyGetter) {
        this.keyGetter = keyGetter;
    }

    public V add(V value) {
        return put(keyGetter.apply(value), value);
    }

    public V add(int index, V value) {
        return put(index, keyGetter.apply(value), value);
    }

    public void addAll(Collection<? extends V> values) {
        for (V value : values) {
            add(value);
        }
    }

    public void addAll(int index, Collection<? extends V> values) {
        for (V value : values) {
            add(index++, value);
        }
    }

    public V set(int index, V value) {
        return put(index, keyGetter.apply(value), value);
    }
}