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

@Slf4j
@Value
public class BacktrackingSearch implements Search {
    @NonNull UnassignedVariableSelector unassignedVariableSelector;
    @NonNull DomainValuesOrderer domainValuesOrderer;

    @Override
    public Optional<Assignment> search(ConstraintSatisfactionProblem csp) {
        return search(csp, new Assignment(Map.of()));
    }

    private Optional<Assignment> search(ConstraintSatisfactionProblem csp, Assignment assignment) {
        log.info("Searching with assignment: {}", assignment);
        if (assignment.isComplete(csp)) {
            return Optional.of(assignment);
        }
        val variable = unassignedVariableSelector.select(csp, assignment);
        for (var value : domainValuesOrderer.order(csp, variable, assignment)) {
            val assignmentWithValue = assignment.withValue(variable, value);
            // TODO support inferences
            if (assignmentWithValue.isConsistent(csp)) {
                val result = search(csp, assignmentWithValue);
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        return Optional.empty();
    }
}
