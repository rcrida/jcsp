package org.jcsp.solver;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jspecify.annotations.NonNull;

/**
 * Factory interface for creating initial assignments in a constraint satisfaction problem (CSP).
 * Implementations of this interface are responsible for generating an {@link Assignment}
 * that assigns values to variables within the CSP's variable domains.
 * <p>
 * The generated assignment may be partial or complete and does not necessarily guarantee
 * consistency with the CSP's constraints. This interface can serve as a foundation for strategies
 * to initialize solutions for CSP algorithms.
 */
public interface InitialAssignmentFactory {
    Assignment getAssignment(@NonNull ConstraintSatisfactionProblem csp);
}
