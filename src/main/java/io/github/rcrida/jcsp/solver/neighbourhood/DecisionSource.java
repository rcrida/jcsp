package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.solver.backtrackingsearch.selector.AdaptiveVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

/**
 * Which variables of a solution a neighbourhood search may fix or free: its <em>decisions</em>, the
 * variables that determine the rest. Freeing a variable that is only a function of the decisions, a
 * channelled cost for instance, frees nothing, because propagation sets it again from the decisions that
 * remain, and fixing one over-constrains the decisions it depends on. So the search fixes and frees
 * decisions only, and everything else follows by propagation.
 * <p>
 * The default finds them by replaying the solution ({@link Factory#REPLAY}). A model's author often knows
 * them directly, and may say so with {@link Factory#declared}.
 */
@FunctionalInterface
public interface DecisionSource {

    /**
     * The decisions of {@code solution}, a complete solution of {@code csp}, in the order they would be made.
     * Free to be any subset of the variables: the ones not named are never fixed or freed by a neighbourhood
     * run, so they are searched in every run, which stays sound.
     */
    List<Variable<?>> decisionsOf(@NonNull ConstraintSatisfactionProblem csp, @NonNull Assignment solution);

    /**
     * Where a solve gets its {@link DecisionSource}: once per solve, handed the solver's propagation and
     * variable ordering so that a source can use them.
     */
    @FunctionalInterface
    interface Factory {

        /**
         * The source for one solve.
         *
         * @param inference the solver's propagation
         * @param selectors the solver's variable ordering, which a source may use to stage its work
         * @param root      an assignment with nothing assigned, carrying the solve's statistics and listener
         */
        DecisionSource create(@NonNull Inference inference, AdaptiveVariableSelector.@NonNull Factory selectors,
                              @NonNull Assignment root);

        /**
         * Replays the solution from the root under a fresh variable selector: the variables the search
         * branches on to reach it are its decisions, and what propagation fixes along the way is not. The
         * default.
         */
        Factory REPLAY = ReplayDecisions::new;

        /** The given variables, in the given order, for every solution. */
        static Factory declared(@NonNull Collection<? extends Variable<?>> decisions) {
            List<Variable<?>> declared = List.copyOf(decisions);
            return (inference, selectors, root) -> (csp, solution) -> declared;
        }
    }
}
