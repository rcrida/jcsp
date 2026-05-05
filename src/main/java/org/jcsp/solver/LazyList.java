package org.jcsp.solver;

import lombok.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * A lazily-evaluated, cached sequence. Elements are computed from the source stream on demand and
 * cached so that subsequent calls to {@link #stream()} replay from the cache without re-computing.
 * Not safe for concurrent use.
 */
@Value
class LazyList<T> {
    Iterator<T> source;
    List<T> cache = new ArrayList<>();

    LazyList(Stream<T> stream) {
        this.source = stream.iterator();
    }

    Stream<T> stream() {
        return StreamSupport.stream(spliteratorUnknownSize(new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < cache.size() || source.hasNext();
            }

            @Override
            public T next() {
                if (index < cache.size()) {
                    return cache.get(index++);
                }
                T val = source.next();
                cache.add(val);
                return cache.get(index++);
            }
        }, ORDERED), false);
    }
}
