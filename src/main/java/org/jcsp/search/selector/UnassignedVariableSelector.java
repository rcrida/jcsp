package org.jcsp.search.selector;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

@FunctionalInterface
public interface UnassignedVariableSelector {
    Variable select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment);
}
