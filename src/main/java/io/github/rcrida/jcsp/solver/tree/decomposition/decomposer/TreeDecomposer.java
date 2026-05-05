package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public interface TreeDecomposer {
    Optional<ConstraintSatisfactionProblem> decompose(@NonNull ConstraintSatisfactionProblem csp, int maxDomainSize);
}
