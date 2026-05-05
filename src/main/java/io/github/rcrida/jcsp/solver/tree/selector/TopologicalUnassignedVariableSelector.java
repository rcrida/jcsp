package io.github.rcrida.jcsp.solver.tree.selector;

import lombok.Value;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.arc.Arc;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;

@Value
public class TopologicalUnassignedVariableSelector implements TreeUnassignedVariableSelector {
    @NonNull List<Arc> topoligicallySortedArcs;

    @Override
    public Variable select(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment assignment) {
        assert csp.isTree();
        return topoligicallySortedArcs.stream()
                .map(Arc::getTo)
                .filter(node -> assignment.getValue(node).isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No unassigned arc found"));
    }
}
