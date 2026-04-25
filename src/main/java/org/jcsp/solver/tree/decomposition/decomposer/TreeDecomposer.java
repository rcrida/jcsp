package org.jcsp.solver.tree.decomposition.decomposer;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public interface TreeDecomposer {
    Optional<ConstraintSatisfactionProblem> decompose(@NonNull ConstraintSatisfactionProblem csp, int maxDomainSize);
}
