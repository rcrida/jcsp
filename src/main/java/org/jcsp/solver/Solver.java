package org.jcsp.solver;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.stream.Stream;

public interface Solver {
    Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp);
    Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp);
}
