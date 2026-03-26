package org.jcsp.domains;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

public interface Domain {
    boolean contains(@Nullable Object value);
    boolean isEmpty();
    long size();
    Stream<?> stream();
    Builder toBuilder();

    interface Builder {
        Builder delete(@NonNull Object value);
        Domain build();
    }
}
