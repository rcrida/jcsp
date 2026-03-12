package org.jcsp.search.selector;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public class MinimumRemainingValuesSelector implements UnassignedVariableSelector {
    @Override
    public Variable select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        val unassignedVariableValueCounts = csp.getVariableDomains().entrySet().stream()
                .filter(entry -> assignment.getValue(entry.getKey()).isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream().count()));
        return unassignedVariableValueCounts.entrySet().stream()
                .min(Comparator.comparingLong(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("No unassigned variable found"));
    }
}
