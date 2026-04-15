package org.jcsp.solver.tree;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.AC3;
import org.jcsp.consistency.arc.Arc;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.search.order.DomainValuesOrderer;
import org.jcsp.solver.Solver;
import org.jcsp.solver.tree.selector.TreeUnassignedVariableSelector;
import org.jcsp.solver.tree.sorter.TopologicalSorter;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
        var assignment = Assignment.EMPTY;
        val rootVariableDomain = tcsp.getVariableDomains().entrySet().iterator().next();
        val root = rootVariableDomain.getKey();
        val X = topologicalSorter.sort(tcsp, root);
        for (Arc arc_j : X.reversed()) {
            val parentX_j = arc_j.getFrom();
            val X_j = arc_j.getTo();
            val optionalTcsp = makeArcConsistent(tcsp, parentX_j, X_j);
            if (optionalTcsp.isPresent()) {
                tcsp = optionalTcsp.get();
            } else {
                return Stream.empty();
            }
        }

        val finalTcsp = tcsp;
        val unassignedVariableSelector = selectorFactory.createSelector(X);
        val domain = rootVariableDomain.getValue();
        return domain.stream()
                .map(value -> assignment.withValue(root, value))
                .flatMap(rootAssignment -> populateAssignment(finalTcsp, rootAssignment, unassignedVariableSelector));
    }

    Optional<ConstraintSatisfactionProblem> makeArcConsistent(@NonNull ConstraintSatisfactionProblem tcsp, @NonNull Variable parent, @NonNull Variable node) {
        val allBinaryConstraints = tcsp.getAllBinaryConstraints();
        val arcConstraints = allBinaryConstraints.stream()
                .flatMap(binaryConstraint -> binaryConstraint.getArcs()
                        .map(arc -> new AbstractMap.SimpleEntry<>(arc, binaryConstraint)))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        val arc = Arc.of(parent, node);
        val variableDomains = new HashMap<>(tcsp.getVariableDomains());
        for (BinaryConstraint binaryConstraint : arcConstraints.get(arc)) {
            val optionalRevisedD = AC3.INSTANCE.revise(tcsp, arc, binaryConstraint);
            if (optionalRevisedD.isPresent()) {
                val revisedD = optionalRevisedD.get();
                if (revisedD.isEmpty()) {
                    log.warn("Domain of variable {} is empty after MAKE-ARC-CONSISTENT({}, {})", arc.getTo(), arc.getFrom(), arc.getTo());
                    return Optional.empty();
                }
                variableDomains.put(parent, revisedD);
            }
        }
        return Optional.of(tcsp.toBuilder().clearVariableDomains().variableDomains(variableDomains).build());
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
