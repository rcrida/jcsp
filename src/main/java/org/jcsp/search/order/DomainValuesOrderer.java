package org.jcsp.search.order;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;

@FunctionalInterface
public interface DomainValuesOrderer {
    List<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable variable, @NonNull Assignment assignment);
}
