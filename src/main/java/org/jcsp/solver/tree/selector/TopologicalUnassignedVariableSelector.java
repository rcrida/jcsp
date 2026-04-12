package org.jcsp.solver.tree.selector;

import lombok.Value;
import org.jcsp.TreeConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.Arc;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;

@Value
public class TopologicalUnassignedVariableSelector implements TreeUnassignedVariableSelector {
    @NonNull List<Arc> topoligicallySortedArcs;

    @Override
    public Variable select(@NonNull TreeConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        return topoligicallySortedArcs.stream()
                .map(Arc::getTo)
                .filter(node -> assignment.getValue(node).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No unassigned arc found"));
    }
}
