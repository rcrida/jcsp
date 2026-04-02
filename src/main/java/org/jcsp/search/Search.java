package org.jcsp.search;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines a contract for implementing search algorithms for solving constraint satisfaction problems (CSPs).
 * <p>
 * A search algorithm systematically explores possible assignments of variables to find solutions that satisfy
 * all constraints defined in a given {@link ConstraintSatisfactionProblem}. Implementations must define how
 * solutions are generated and streamed to the caller.
 * <p>
 * This interface provides a stream-based API for generating solutions, allowing lazy evaluation of solutions
 * as they are discovered. Implementations may leverage various strategies for variable selection, domain
 * ordering, and inference to optimize the search process.
 */
public interface Search {
    Stream<Assignment> searchStream(ConstraintSatisfactionProblem csp);
}
