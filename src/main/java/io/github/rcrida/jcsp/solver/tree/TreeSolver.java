package io.github.rcrida.jcsp.solver.tree;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.consistency.arc.Arc;
import io.github.rcrida.jcsp.solver.backtrackingsearch.order.DomainValuesOrderer;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import io.github.rcrida.jcsp.solver.tree.sorter.TopologicalSorter;
import io.github.rcrida.jcsp.variables.Variable;
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
        var current = tcsp;
        for (Arc arc : X.reversed()) {
            val result = makeArcConsistent(current, arc.getFrom(), arc.getTo());
            if (result.isEmpty()) return Stream.empty();
            current = result.get();
        }
        val finalTcsp = current;
        val unassignedVariableSelector = selectorFactory.createSelector(X);
        val domain = finalTcsp.getDomain(root);
        log.info("Domain {}", domain);
        val start = Assignment.empty();
        return domain.stream()
                .<Assignment>map(value -> start.withValue(root, value))
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
