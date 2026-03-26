package org.jcsp.search;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.Inference;
import org.jcsp.search.order.DomainValuesOrderer;
import org.jcsp.search.selector.UnassignedVariableSelector;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Value
public class BacktrackingSearch implements Search {
    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;
    @NonNull Inference inference;

    @Override
    public Stream<Assignment> searchStream(ConstraintSatisfactionProblem csp) {
        return searchStream(csp, new Assignment(Map.of()));
    }

    private Stream<Assignment> searchStream(ConstraintSatisfactionProblem csp, Assignment assignment) {
        log.debug("Searching with assignment: {}", assignment);
//        log.info("Search space: {}", csp.getSearchSpace());
        if (assignment.isComplete(csp)) {
            log.info("Found solution: {}", assignment);
            return Stream.of(assignment);
        }
        val variable = unassignedVariableSelector.select(csp, assignment);
        return domainValuesOrderer.order(csp, variable, assignment).stream()
                .map(value -> assignment.withValue(variable, value))
                .filter(next -> next.isConsistent(csp))
                .flatMap(next -> inference.apply(csp, variable, next).stream()
                        .flatMap(c -> searchStream(c, next)));
    }
}
