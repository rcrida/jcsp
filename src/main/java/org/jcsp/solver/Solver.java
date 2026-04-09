package org.jcsp.solver;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.MAC;
import org.jcsp.search.BacktrackingSearch;
import org.jcsp.search.order.LeastConstrainingValueOrderer;
import org.jcsp.search.selector.MinimumRemainingValuesSelector;
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
    default Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return getSolutions(csp).findFirst();
    }
    Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp);

    interface Factory {
        Factory INSTANCE = () -> {
            val subproblemSolver = new SolverImpl(new BacktrackingSearch(new MinimumRemainingValuesSelector(), new LeastConstrainingValueOrderer(), MAC.INSTANCE));
            return new IndependentSubproblemSolver(subproblemSolver);
        };

        Solver createSolver();
    }
}
