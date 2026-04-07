package org.jcsp.solver;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.solver.assignmentfactory.InitialAssignmentFactory;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Defines an interface for solving constraint satisfaction problems (CSPs) using local search techniques.
 * Implementations of this interface aim to find assignments of values to variables that satisfy the
 * constraints of a given CSP.
 * <p>
 * The interface relies on an {@link InitialAssignmentFactory} to generate an initial assignment for the CSP.
 * This initial assignment acts as the starting point for the search process. The search process then attempts
 * to refine this assignment by exploring the solution space to minimize conflicts or to satisfy the constraints.
 * <p>
 * Implementations may include various strategies for local search such as hill climbing, simulated annealing,
 * or the min-conflicts heuristic.
 */
public interface LocalSolver {
    Optional<Assignment> getLocalSolution(@NonNull ConstraintSatisfactionProblem csp, @NonNull InitialAssignmentFactory factory);
}
