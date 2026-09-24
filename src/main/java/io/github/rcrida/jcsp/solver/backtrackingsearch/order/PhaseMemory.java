package io.github.rcrida.jcsp.solver.backtrackingsearch.order;

import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the value each variable most recently descended into, so a later visit tries that value
 * first -- phase saving (Pipatsrisawat &amp; Darwiche 2007), in its solution-guided form.
 * <p>
 * Exists for {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution}, which restarts on a
 * Luby schedule and otherwise throws away everything it learned about <em>which values</em> worked:
 * weights and the shared nogood store already survive a restart, but the value ordering resets to
 * whatever {@link DomainValuesOrderer} says, so the search re-derives the same partial assignment
 * from scratch each time. Replaying the remembered values steers it back down the deepest path it
 * had found before spending nodes rediscovering it.
 * <p>
 * Only ever reorders candidate values, so it cannot affect soundness or completeness -- every value
 * is still tried, just in a different order. It does change <em>which</em> solution a
 * satisfiable problem returns first, which is why {@link
 * io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolutions} deliberately does not consult one:
 * that method promises a complete stream, never restarts, and so has nothing to recover.
 * <p>
 * Keyed by {@link IdentityHashMap}: the variables recorded here are the exact objects the solver
 * holds for the whole solve, so identity is both sufficient and cheaper than
 * {@link Variable}'s own {@code equals}/{@code hashCode}, which hash a {@link String} name.
 * <p>
 * Not thread-safe, and not meant to be: one instance belongs to one {@code getSolution} call, whose
 * search is single-threaded, exactly like the {@code DomWdegVariableSelector} it sits beside.
 */
public final class PhaseMemory {
    private final Map<Variable<?>, Object> bestPath = new IdentityHashMap<>();
    private int bestDepth;

    /**
     * Size of the deepest assignment recorded so far, or {@code 0} before anything is. Read by
     * {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch#getSolution} as its progress signal:
     * a restart that fails to raise it explored no further than its predecessors did.
     */
    public int bestDepth() {
        return bestDepth;
    }

    /**
     * Adopts {@code assignment}'s values as the path to replay, but only when it is strictly deeper
     * than any seen before -- solution-guided search (Demirović et al.), not the every-descent
     * variant of classic phase saving.
     * <p>
     * That distinction is the whole design, and was measured: recording on <em>every</em> branch that
     * survived propagation made {@code MagicSquare-6-sum} 5x faster and {@code driverlogw-09} 7x
     * faster, but stopped {@code Bibd-sum-06-050-25-03-10} and {@code Bibd-sc-06-050-25-03-10}
     * solving at all -- from under a second to not finishing in 40. Every-descent recording keeps
     * pointing the next restart back down branches that were locally consistent but later refuted,
     * which on a highly symmetric problem is a trap the restart exists to escape. Depth is a
     * monotone proxy for progress, so only a genuinely new best path can overwrite the memory.
     */
    public void recordIfDeepest(@NonNull Map<Variable<?>, Object> assignment) {
        if (assignment.size() <= bestDepth) return;
        bestDepth = assignment.size();
        bestPath.putAll(assignment);
    }

    /**
     * Adopts a complete solution's values as the path to replay, overwriting whatever was there.
     * <p>
     * The counterpart to {@link #recordIfDeepest} for optimization, where depth cannot be the test:
     * every solution is complete, so they all tie on size and {@code recordIfDeepest} would keep the
     * first one forever. The ordering that matters there is by objective instead, and the caller
     * ({@link io.github.rcrida.jcsp.solver.BranchAndBoundSolver}) only reaches this after the
     * incumbent has strictly improved -- so the newest solution is always the better guide and an
     * unconditional overwrite is the right rule rather than a laxer one.
     */
    public void recordSolution(@NonNull Map<Variable<?>, Object> solution) {
        bestDepth = solution.size();
        bestPath.putAll(solution);
    }

    /**
     * {@code values} with this variable's remembered value moved to the front, or {@code values}
     * itself when there is nothing remembered, the remembered value is no longer in the candidate
     * list (propagation has since pruned it), or it is already first -- the common case once the
     * search settles, and the reason this allocates nothing in steady state.
     */
    public @NonNull List<Object> prioritise(@NonNull Variable<?> variable, @NonNull List<Object> values) {
        Object saved = bestPath.get(variable);
        if (saved == null) return values;
        int index = values.indexOf(saved);
        // <= 0 covers both "propagation has since pruned it" (-1) and "already first" (0), the
        // latter being the common case once the search settles -- so this allocates nothing then.
        if (index <= 0) return values;
        List<Object> reordered = new ArrayList<>(values.size());
        reordered.add(saved);
        for (int i = 0; i < values.size(); i++) {
            if (i != index) reordered.add(values.get(i));
        }
        return reordered;
    }
}
