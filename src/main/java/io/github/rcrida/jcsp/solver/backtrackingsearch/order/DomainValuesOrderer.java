package io.github.rcrida.jcsp.solver.backtrackingsearch.order;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A functional interface that defines a strategy for ordering the domain values of a variable
 * in the context of a constraint satisfaction problem (CSP). The order in which domain values
 * are considered can significantly impact the efficiency of solving the CSP.
 * <p>
 * Implementations of this interface typically apply heuristics to prioritize domain values
 * in a way that reduces the search space or satisfies constraints more effectively.
 */
@FunctionalInterface
public interface DomainValuesOrderer {
    Stream<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable<?> variable, @NonNull Assignment assignment);

    /**
     * Told that restart {@code restart} (counting from 1) is about to begin, once per restart and
     * before any value is asked for in it. An orderer whose answer depends on which restart it is in
     * -- one that alternates between orders, say -- keeps that state here; the default is stateless
     * and does nothing. Never called by a search that does not restart, such as {@code getSolutions}.
     * <p>
     * An orderer that keeps state belongs to one search, which is single-threaded: see {@link
     * Factory}, which hands every search its own.
     */
    default void onRestart(int restart) {
    }

    /**
     * Where a search gets its {@link DomainValuesOrderer}: {@code SolverConfig}'s {@code valueOrder}.
     * A factory rather than an orderer because an order may keep state (which restart it is in), and
     * each search needs its own: {@link #create} is called once for every search a solve runs, and
     * what it returns is never shared.
     * <p>
     * The order changes only the sequence in which assignments are visited, never which ones are, so a
     * complete search still finds a solution if there is one and still proves there is none. What it
     * changes is how quickly, and no order wins everywhere (see ADR-0063), which is why this is a
     * setting and the default stays {@link #LEAST_CONSTRAINING}.
     * <p>
     * A new order is a new constant or a lambda returning a {@link DomainValuesOrderer}, and {@link
     * #reversed} and {@link #cycle} take any factory, so it composes with the existing ones for free.
     */
    @FunctionalInterface
    interface Factory {
        /** The orderer for one search. Called once per search, so it may hold that search's state. */
        DomainValuesOrderer create();

        /**
         * Fewest conflicts with the neighbours first, ties in domain order: the library's own default.
         * On a problem without binary constraints this is {@link #DOMAIN_ORDER}.
         */
        Factory LEAST_CONSTRAINING = () -> LeastConstrainingValueOrderer.INSTANCE;

        /**
         * The domain's own order, which is increasing for ranges and for the sorted lists the XCSP3
         * parser builds, but is whatever order a domain was given in otherwise.
         */
        Factory DOMAIN_ORDER = () -> DefaultValueOrderer.INSTANCE;

        /**
         * {@link #DOMAIN_ORDER} backwards. Packing problems, where a large item placed early leaves
         * less to fix later, are the measured case for it (ADR-0063).
         */
        Factory REVERSED_DOMAIN_ORDER = reversed(DOMAIN_ORDER);

        /**
         * {@link #DOMAIN_ORDER} in the first restart, {@link #REVERSED_DOMAIN_ORDER} in the second,
         * and so on in turn: whichever suits the problem gets half the restarts without anyone having
         * to know which. The cheapest way to hedge between the two. To alternate the default order
         * instead, {@code cycle(LEAST_CONSTRAINING, reversed(LEAST_CONSTRAINING))}.
         */
        Factory ALTERNATING = cycle(DOMAIN_ORDER, REVERSED_DOMAIN_ORDER);

        /** {@code factory}'s orders with every list of values reversed. */
        static Factory reversed(@NonNull Factory factory) {
            return () -> {
                DomainValuesOrderer inner = factory.create();
                return new DomainValuesOrderer() {
                    @Override
                    public Stream<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable<?> variable,
                                           @NonNull Assignment assignment) {
                        List<Object> values = new ArrayList<>(inner.order(csp, variable, assignment).toList());
                        Collections.reverse(values);
                        return values.stream();
                    }

                    @Override
                    public void onRestart(int restart) {
                        inner.onRestart(restart);
                    }
                };
            };
        }

        /**
         * Restart <i>k</i> (counting from 1) uses the <i>k</i>-th of the orders in turn, wrapping
         * round: with two it alternates, with three it cycles. Every order is told about every
         * restart, so one that keeps its own state stays in step.
         */
        static Factory cycle(@NonNull Factory first, @NonNull Factory... rest) {
            List<Factory> factories = new ArrayList<>(List.of(first));
            factories.addAll(List.of(rest));
            return () -> {
                List<DomainValuesOrderer> orderers = factories.stream().map(Factory::create).toList();
                return new DomainValuesOrderer() {
                    private int current;

                    @Override
                    public Stream<?> order(@NonNull ConstraintSatisfactionProblem csp, @NonNull Variable<?> variable,
                                           @NonNull Assignment assignment) {
                        return orderers.get(current).order(csp, variable, assignment);
                    }

                    @Override
                    public void onRestart(int restart) {
                        current = Math.floorMod(restart - 1, orderers.size());
                        orderers.forEach(orderer -> orderer.onRestart(restart));
                    }
                };
            };
        }
    }
}
