package io.github.rcrida.jcsp.solver.tree;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
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
        log.debug("Searching {}", tcsp);
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
        Domain<?> rootDomain = finalTcsp.getDomain(root);
        log.debug("Domain {}", rootDomain);
        val start = Assignment.empty();
        // Pre-filtered once, outside the per-candidate-value loop below, so checking each candidate
        // root value doesn't re-scan the tree's entire constraint list (Assignment#isConsistent's
        // own filter) just to find the handful of constraints that touch root.
        val rootConstraints = finalTcsp.getConstraints().stream()
                .filter(constraint -> constraint.getVariables().contains(root))
                .toList();
        return (rootDomain instanceof DiscreteDomain<?> dd ? dd.stream() : rootDomain.singleValue().stream())
                .<Assignment>map(value -> start.withValue(root, value))
                // populateAssignment only calls isConsistent for variables assigned *after* the root
                // (each recursive step filters its own candidate); a constraint that touches only the
                // root itself -- e.g. a single-variable PredicateConstraint, which NodeConsistency
                // doesn't prune because it isn't a UnaryConstraint -- would otherwise never be checked
                // at all when the tree is exactly one node, since populateAssignment then returns
                // immediately via the isComplete(tcsp) branch below without ever filtering.
                .filter(rootAssignment -> rootAssignment.isConsistentAmong(rootConstraints))
                .flatMap(rootAssignment -> populateAssignment(finalTcsp, rootAssignment, unassignedVariableSelector));
    }

    Optional<ConstraintSatisfactionProblem> makeArcConsistent(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Variable parent, @NonNull Variable node) {
        return AC3.INSTANCE.revise(tcsp, Arc.of(parent, node));
    }

    Stream<Assignment> populateAssignment(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Assignment assignment, @NonNull TreeUnassignedVariableSelector selector) {
        log.debug("Searching tree with assignment: {}", assignment);
        if (assignment.isComplete(tcsp)) {
            log.debug("Found tree solution {}", assignment);
            return Stream.of(assignment);
        }
        val variable = selector.select(tcsp, assignment);
        return orderer.order(tcsp, variable, assignment)
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> next.isConsistent(tcsp))
                .flatMap(next -> populateAssignment(tcsp, next, selector));
    }
}
