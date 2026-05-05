package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer;

import io.github.rcrida.jcsp.solver.tree.decomposition.TreeDecompositionSolver;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.SymmetricBinaryConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

/**
 * A {@link BinaryConstraint} used with {@link TreeDecompositionSolver} where each variable
 * represents a set or clique of variables from the original problem. This constraint is used to ensure that the original
 * problem assignment values are the same across all cliques in which the variable is present
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AssignmentVariableConsistencyConstraint extends SymmetricBinaryConstraint {
    @NonNull Variable cliqueVariable;

    @Override
    public boolean isSatisfiedBy(@NonNull Object leftValue, @NonNull Object rightValue) {
        val leftAssignment = (Assignment) leftValue;
        val rightAssignment = (Assignment) rightValue;
        return leftAssignment.getValue(cliqueVariable)
                .flatMap(lv -> rightAssignment.getValue(cliqueVariable)
                        .map(rv -> lv == rv))
                .orElse(true);
    }

    @Override
    public String getRelation() {
        return String.format("%s clique consistent", cliqueVariable, cliqueVariable);
    }
}
