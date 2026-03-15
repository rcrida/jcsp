package org.jcsp.search;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.search.order.DomainValuesOrderer;
import org.jcsp.search.selector.UnassignedVariableSelector;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Value
public class BacktrackingSearch implements Search {
    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;

    @Override
    public Optional<Assignment> search(ConstraintSatisfactionProblem csp) {
        return searchStream(csp).findFirst();
    }

    @Override
    public Stream<Assignment> searchStream(ConstraintSatisfactionProblem csp) {
        return searchStream(csp, new Assignment(Map.of()));
    }

    private Stream<Assignment> searchStream(ConstraintSatisfactionProblem csp, Assignment assignment) {
        log.info("Searching with assignment: {}", assignment);
        if (assignment.isComplete(csp)) {
            return Stream.of(assignment);
        }
        val variable = unassignedVariableSelector.select(csp, assignment);
        return domainValuesOrderer.order(csp, variable, assignment).stream()
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> next.isConsistent(csp))
                .flatMap(next -> searchStream(csp, next));
    }
}
