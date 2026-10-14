package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer;

import io.github.rcrida.jcsp.solver.tree.decomposition.TreeDecompositionSolver;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.SymmetricBinaryConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * A {@link BinaryConstraint} used with {@link TreeDecompositionSolver} where each variable
 * represents a set or clique of variables from the original problem. This constraint is used to ensure that the original
 * problem assignment values are the same across all cliques in which the variable is present
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AssignmentVariableConsistencyConstraint extends SymmetricBinaryConstraint<Assignment> {
    /**
     * Every variable the two cliques share. One constraint carries all of them, rather than one
     * constraint per shared variable: arc consistency revises each constraint on an arc separately,
     * so a value would survive on the strength of a different supporting neighbour value for each
     * shared variable instead of a single neighbour value agreeing on all of them. That is weaker
     * than the arc consistency a tree needs to be backtrack-free, and the search thrashed.
     */
    @Singular @NonNull Set<Variable<?>> cliqueVariables;

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment leftValue, @NonNull Assignment rightValue) {
        for (val cliqueVariable : cliqueVariables) {
            val lv = leftValue.getValue(cliqueVariable);
            val rv = rightValue.getValue(cliqueVariable);
            if (lv.isPresent() && rv.isPresent() && !lv.get().equals(rv.get())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getRelation() {
        return String.format("%s clique consistent", cliqueVariables);
    }
}
