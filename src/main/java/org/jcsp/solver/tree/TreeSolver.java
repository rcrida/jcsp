package org.jcsp.solver.tree;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.AC3;
import org.jcsp.consistency.arc.Arc;
import org.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import org.jcsp.solver.Solver;
import org.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import org.jcsp.solver.tree.sorter.TopologicalSorter;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Value
public class TreeSolver implements Solver {
    @NonNull TopologicalSorter topologicalSorter;
    @NonNull DomainValuesOrderer orderer;
    TreeUnassignedVariableSelector.Factory selectorFactory;

    @Override
    public Stream<Assignment> getSolutions(@NonNull ConstraintSatisfactionProblem tcsp) {
        assert tcsp.isTree();
        log.info("Searching {}", tcsp);
        val root = tcsp.getVariableDomains().entrySet().iterator().next().getKey();
        val X = topologicalSorter.sort(tcsp, root);
        val arcConsistentTcsp = X.reversed().stream()
                .reduce(
                        Optional.of(tcsp),
                        (optCsp, arc) -> optCsp.flatMap(csp -> makeArcConsistent(csp, arc.getFrom(), arc.getTo())),
                        (a, b) -> b);
        if (arcConsistentTcsp.isEmpty()) return Stream.empty();
        val finalTcsp = arcConsistentTcsp.get();
        val unassignedVariableSelector = selectorFactory.createSelector(X);
        val domain = finalTcsp.getDomain(root).get();
        log.info("Domain {}", domain);
        return domain.stream()
                .map(value -> Assignment.of(root, value))
                .flatMap(rootAssignment -> populateAssignment(finalTcsp, rootAssignment, unassignedVariableSelector));
    }

    Optional<ConstraintSatisfactionProblem> makeArcConsistent(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Variable parent, @NonNull Variable node) {
        return AC3.INSTANCE.revise(tcsp, Arc.of(parent, node));
    }

    Stream<Assignment> populateAssignment(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Assignment assignment, @NonNull TreeUnassignedVariableSelector selector) {
        log.debug("Searching tree with assignment: {}", assignment);
        if (assignment.isComplete(tcsp)) {
            log.info("Found tree solution {}", assignment);
            return Stream.of(assignment);
        }
        val variable = selector.select(tcsp, assignment);
        return orderer.order(tcsp, variable, assignment)
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> next.isConsistent(tcsp))
                .flatMap(next -> populateAssignment(tcsp, next, selector));
    }
}
