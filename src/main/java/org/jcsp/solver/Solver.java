package org.jcsp.solver;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Represents a generic interface responsible for solving constraint satisfaction problems (CSPs).
 * A CSP consists of a set of variables, their potential values (domains), and constraints
 * that specify relationships between these variables.
 * <p>
 * Implementations of the Solver interface provide methods to derive solutions for a given CSP,
 * either as a single solution or as a stream of solutions.
 */
public interface Solver {
    Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp);
    Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp);
}
