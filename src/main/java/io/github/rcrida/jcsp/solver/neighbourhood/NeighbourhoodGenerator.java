package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Chooses which of an incumbent's branching decisions a neighbourhood search leaves free. Every other
 * decision is fixed to its incumbent value, and the search below looks for a better solution among the
 * assignments that remain.
 * <p>
 * A good choice frees decisions that interact, so that the freed part is a real sub-problem rather than
 * a handful of unrelated variables with nothing to trade against each other, and few enough that the
 * sub-problem can be searched in a short run. How a generator balances that is its own business; the
 * solver only asks for {@code count} of them.
 * <p>
 * A generator belongs to one solve and may remember what it has chosen: see {@link Factory}.
 */
public interface NeighbourhoodGenerator {

    /**
     * {@code count} of {@code decisions} to leave free, for the search around {@code incumbent}.
     *
     * @param decisions the branching decisions that reproduce the incumbent, in the order the search
     *                  makes them; never fewer than two
     * @param incumbent the solution being improved
     * @param count     how many to free, between 1 and one fewer than {@code decisions}
     * @param random    the source of every random choice, so that a seeded solve repeats
     * @return exactly {@code count} distinct members of {@code decisions}
     */
    Set<Variable<?>> free(@NonNull List<Variable<?>> decisions, @NonNull Assignment incumbent, int count,
                          @NonNull Random random);

    /**
     * Where a solve gets its {@link NeighbourhoodGenerator}: once per solve, because a generator may keep
     * state about the problem it is working on or about its earlier choices.
     */
    @FunctionalInterface
    interface Factory {

        /** The generator for the solve of {@code csp} minimising {@code objective}. */
        NeighbourhoodGenerator create(@NonNull ConstraintSatisfactionProblem csp,
                                      @NonNull ToDoubleFunction<Assignment> objective);

        /** A uniformly random subset. Cheap, and the baseline the others are measured against. */
        Factory RANDOM = (csp, objective) -> new RandomNeighbourhood();

        /**
         * A tournament that favours decisions costing the objective most and decisions related by
         * shared constraints to the one freed before, and avoids freeing the same decision every time
         * (after the iVRG operator of Souza, Grimes and O'Sullivan, CP 2024).
         */
        Factory COST_WEIGHTED = CostWeightedNeighbourhood::new;
    }
}
