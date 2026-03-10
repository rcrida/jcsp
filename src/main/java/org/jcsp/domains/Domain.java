package org.jcsp.domains;

import org.jspecify.annotations.Nullable;

public interface Domain {
    boolean contains(@Nullable Object value);
}
