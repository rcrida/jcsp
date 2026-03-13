package org.jcsp.search.selector;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A strategy for selecting the next unassigned variable in a constraint satisfaction problem (CSP)
 * based on the Minimum Remaining Values (MRV) heuristic. This heuristic prioritizes variables
 * with the smallest number of remaining legal values, thereby attempting to identify constraints
 * as early as possible and reduce the search space.
 * <p>
 * The selection process performs the following steps:
 * 1. Retrieves all variables in the CSP along with their respective variable domains.
 * 2. Filters out any variables that are already assigned a value based on the provided assignment.
 * 3. Computes the number of potential legal values remaining in the domain for each unassigned variable.
 * 4. Selects the variable with the smallest number of remaining legal values.
 * <p>
 * If no unassigned variables are found, the method will throw an {@code IllegalStateException}.
 */
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
