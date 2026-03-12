package org.jcsp.solver;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public interface Solver {
    Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp);
}
