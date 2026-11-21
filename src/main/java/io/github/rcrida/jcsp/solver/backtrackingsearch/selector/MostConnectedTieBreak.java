package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.assignments.Valuation;
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
 * Keeps the tied variables with the most structural constraints that also hold an assigned
 * variable, and hands any still tied to the next tie-break. Asked without an assignment, it has
 * nothing to count and hands every tied variable on. {@link NogoodConstraint}s are not counted.
 */
final class MostConnectedTieBreak implements TieBreak {

    /** Per variable, the scopes of the structural constraints it appears in. */
    private final Map<Variable<?>, List<List<? extends Variable<?>>>> scopes = new HashMap<>();
    private final TieBreak then;

    MostConnectedTieBreak(@NonNull Set<Constraint> constraints, @NonNull TieBreak then) {
        this.then = then;
        for (Constraint constraint : constraints) {
            if (constraint instanceof NogoodConstraint) continue;
            List<? extends Variable<?>> scope = List.copyOf(constraint.getVariables());
            for (Variable<?> variable : scope) {
                scopes.computeIfAbsent(variable, v -> new ArrayList<>()).add(scope);
            }
        }
    }

    @Override
    public @NonNull Variable<?> choose(@NonNull List<Variable<?>> tied) {
        return then.choose(tied);
    }

    @Override
    public @NonNull Variable<?> choose(@NonNull List<Variable<?>> tied, @NonNull Valuation assignment) {
        int highest = -1;
        List<Variable<?>> most = new ArrayList<>();
        for (Variable<?> variable : tied) {
            int connected = connected(variable, assignment);
            if (connected > highest) {
                highest = connected;
                most.clear();
            }
            if (connected == highest) most.add(variable);
        }
        return most.size() == 1 ? most.get(0) : then.choose(most, assignment);
    }

    /** How many of {@code variable}'s constraints hold a variable {@code assignment} has assigned. */
    private int connected(Variable<?> variable, Valuation assignment) {
        int count = 0;
        for (List<? extends Variable<?>> scope : scopes.getOrDefault(variable, List.of())) {
            for (Variable<?> other : scope) {
                if (other != variable && assignment.isAssigned(other)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    @Override
    public void onRestart(@Nullable Random random) {
        then.onRestart(random);
    }
}
