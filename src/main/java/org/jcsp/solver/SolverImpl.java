package org.jcsp.solver;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.Inference;
import org.jcsp.consistency.arc.AC3;
import org.jcsp.consistency.node.NodeConsistency;
import org.jcsp.search.BacktrackingSearch;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Value
public class SolverImpl implements Solver {
    @NonNull
    Inference inference;
    @NonNull
    BacktrackingSearch backtrackingSearch;

    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return getSolutions(csp).findFirst();
    }

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem csp) {
        return inference.apply(csp).stream()
                .flatMap(arcConsistent -> {
                    log.info("Searching arc-consistent problem {}", arcConsistent);
                    return backtrackingSearch.searchStream(arcConsistent);
                });
    }
}
