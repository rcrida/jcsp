package org.jcsp.consistency.arc;

import org.jcsp.ConstraintSatisfactionProblem;

import java.util.Optional;

public interface ArcConsistency {
    /**
     * Applies arc consistency algorithm to the given constraint satisfaction problem.
     *
     * @param problem The constraint satisfaction problem to apply arc consistency to.
     * @return An Optional containing the updated problem if arc consistency was applied successfully, or empty if an inconsistency was found.
     */
    Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem);
}
