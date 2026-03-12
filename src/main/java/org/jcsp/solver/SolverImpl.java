package org.jcsp.solver;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.AC3;
import org.jcsp.consistency.node.NodeConsistency;
import org.jcsp.search.BacktrackingSearch;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

@Slf4j
@Value
public class SolverImpl implements Solver {
    @NonNull
    BacktrackingSearch backtrackingSearch;

    @Override
    public Optional<Assignment> getSolution(@NonNull ConstraintSatisfactionProblem csp) {
        return NodeConsistency.INSTANCE.apply(csp)
                .flatMap(nodeConsistent -> {
                    log.info("Applying AC3 to node consistent problem {}", nodeConsistent);
                    return AC3.INSTANCE.apply(nodeConsistent);
                })
                .flatMap(arcConsistent -> {
                    log.info("Searching arc-consistent problem {}", arcConsistent);
                    return backtrackingSearch.search(arcConsistent);
                });
    }
}
