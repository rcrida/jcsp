package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Keeps the tied variables that appear in the most structural constraints, counted once at
 * construction, and hands any still tied to the next tie-break. {@link NogoodConstraint}s are not
 * counted, for the same reason {@link DomWdegVariableSelector} does not weigh them.
 */
final class MaxDegreeTieBreak implements TieBreak {

    private final Map<Variable<?>, Integer> degrees = new HashMap<>();
    private final TieBreak then;

    MaxDegreeTieBreak(@NonNull Set<Constraint> constraints, @NonNull TieBreak then) {
        this.then = then;
        for (Constraint constraint : constraints) {
            if (constraint instanceof NogoodConstraint) continue;
            for (Variable<?> variable : constraint.getVariables()) {
                degrees.merge(variable, 1, Integer::sum);
            }
        }
    }

    @Override
    public @NonNull Variable<?> choose(@NonNull List<Variable<?>> tied) {
        int highest = -1;
        List<Variable<?>> most = new ArrayList<>();
        for (Variable<?> variable : tied) {
            int degree = degrees.getOrDefault(variable, 0);
            if (degree > highest) {
                highest = degree;
                most.clear();
            }
            if (degree == highest) most.add(variable);
        }
        return most.size() == 1 ? most.get(0) : then.choose(most);
    }

    @Override
    public void onRestart(@Nullable Random random) {
        then.onRestart(random);
    }
}
