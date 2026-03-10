package org.jcsp.relations;

import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

public interface Relation {
    boolean isSatisfied(@NonNull Assignment assignment);
}
