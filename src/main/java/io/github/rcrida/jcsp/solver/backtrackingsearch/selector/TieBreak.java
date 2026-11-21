package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Chooses one of the variables a selector's ranking left tied, kept apart from the ranking so that
 * a selector and a way of breaking its ties can be combined freely. {@link DomWdegVariableSelector}
 * consults one whenever several variables share the best ratio.
 * <p>
 * Like a selector, a tie-break can only change the order in which a complete search visits
 * assignments, never which ones it visits, so it cannot affect soundness or completeness.
 */
@FunctionalInterface
public interface TieBreak {

    /**
     * Builds a tie-break for one solve, from the problem's structural constraints. A tie-break may
     * keep state across the restarts of the search it serves, so each solve needs its own.
     */
    @FunctionalInterface
    interface Factory {

        /**
         * A uniformly random pick, drawn from the source each restart supplies, or the first tied
         * variable when there is none -- see {@link io.github.rcrida.jcsp.solver.RestartRandomization}.
         */
        Factory RANDOM = constraints -> new RandomTieBreak();

        /**
         * The default: during the first run the most constrained of the tied variables ({@link
         * #maxDegree}), then {@link #RANDOM}. See
         * <a href="../../../../../../../../docs/adr/0073-break-the-first-runs-ties-by-degree.md">ADR-0073</a>.
         */
        Factory DEFAULT = firstRun(maxDegree(RANDOM), RANDOM);

        /**
         * Keeps the tied variables that appear in the most structural constraints, and hands any that
         * remain tied to {@code then}.
         */
        static @NonNull Factory maxDegree(@NonNull Factory then) {
            return constraints -> new MaxDegreeTieBreak(constraints, then.create(constraints));
        }

        /** Breaks ties with {@code first} until the second restart begins, then with {@code later}. */
        static @NonNull Factory firstRun(@NonNull Factory first, @NonNull Factory later) {
            return constraints -> new FirstRunTieBreak(first.create(constraints), later.create(constraints));
        }

        @NonNull TieBreak create(@NonNull Set<Constraint> constraints);
    }

    /**
     * One of {@code tied}, which holds at least two variables, in the order the selector ranked them.
     */
    @NonNull Variable<?> choose(@NonNull List<Variable<?>> tied);

    /**
     * A restart is beginning, with {@code random} as its source of randomness, or {@code null} for
     * none -- forwarded from {@link AdaptiveVariableSelector#onRestart}.
     */
    default void onRestart(@Nullable Random random) {
    }
}
