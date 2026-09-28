package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Random;
import java.util.Set;

/**
 * An {@link UnassignedVariableSelector} that also watches the search it is ordering, so it can
 * learn from conflicts and shed what it learned when restarts stop making progress.
 * {@link io.github.rcrida.jcsp.solver.DomWdegLubySearch} drives one of these;
 * {@link MinimumRemainingValuesSelector} and the other plain selectors stay on the base interface,
 * which is why these hooks are declared here rather than added there.
 * <p>
 * They are named for the event rather than for {@link DomWdegVariableSelector}'s response to it,
 * because the point of naming this contract at all is to admit a selector built on something other
 * than constraint weights -- one ranking variables by the propagation an assignment actually
 * causes, say, for which "increment weights" would describe nothing. Each hook defaults to ignoring
 * its event, so an implementation interested only in ranking writes {@link #select} and no more.
 * <p>
 * Whatever it does, a selector cannot affect soundness or completeness: a complete search visits
 * the same assignments whatever order it picks variables in, so an ordering changes only how
 * quickly a solution is found. That is what makes an alternative safe to drop in and measure.
 */
public interface AdaptiveVariableSelector extends UnassignedVariableSelector {

    /**
     * Builds a selector for one solve, from the problem's structural constraints. A selector of
     * this kind accumulates state about the search it is watching, so each solve needs its own --
     * which is why a search takes a factory rather than a ready-made selector. Mirrors
     * {@link io.github.rcrida.jcsp.solver.tree.selector.TreeUnassignedVariableSelector.Factory}.
     */
    @FunctionalInterface
    interface Factory {

        /** dom/wdeg, the satisfaction chain's default -- see {@link DomWdegVariableSelector}. */
        Factory INSTANCE = DomWdegVariableSelector::new;

        AdaptiveVariableSelector createSelector(@NonNull Set<Constraint> constraints);
    }

    /**
     * Inference wiped out a domain after {@code variable} was assigned, producing
     * {@code nextAssignment} -- the conflict is attributable to the constraints on {@code variable}
     * that still have some other unassigned variable to be wrong about. Raised only for an
     * inference failure, not for a value the search rejected by checking it directly, and always
     * alongside {@link #onValueRejected} for the same rejection: an implementation that reacts to
     * both should expect to see both.
     */
    default void onConflict(@NonNull Variable<?> variable, @NonNull Assignment nextAssignment) {
    }

    /**
     * One candidate value for {@code variable} was rejected, leaving the rest of its domain still
     * to try. Raised for every rejection however it was detected -- a direct consistency check or
     * an inference failure -- so it fires far more often than {@link #onConflict}, and says nothing
     * about {@code variable} being exhausted.
     */
    default void onValueRejected(@NonNull Variable<?> variable) {
    }

    /**
     * A restart is beginning. {@code tieBreak} is the source to draw from when several variables
     * rank equally, or {@code null} to keep taking the first of them -- see
     * {@link io.github.rcrida.jcsp.solver.RestartRandomization}.
     */
    default void onRestart(@Nullable Random tieBreak) {
    }

    /**
     * Restarts have stopped reaching new ground, so whatever this selector has accumulated is
     * steering all of them the same unproductive way and should be discarded.
     */
    default void onStagnation() {
    }
}
