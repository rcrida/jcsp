package io.github.rcrida.jcsp.solver;

import java.util.function.LongSupplier;

/**
 * How many failed candidates each restart of {@link DomWdegLubySearch#getSolution} may spend before
 * it is abandoned. A restart that exhausts its tree within budget is a proof; one that does not
 * restarts with its learned weights, so the schedule trades diversification against a long enough
 * run to finish a refutation. See ADR-0054 for the measurements.
 */
public enum RestartSchedule {
    /**
     * The Luby sequence 1, 1, 2, 1, 1, 2, 4, … times {@code lubyUnit}.
     * Restarts stay frequent, which is what satisfiable instances with a heavy-tailed solve time
     * need, but reaching one budget large enough to refute a hard unsatisfiable instance takes
     * about a thousand shorter ones first.
     */
    LUBY {
        @Override
        LongSupplier budgets(int lubyUnit) {
            long[] restart = {0};
            return () -> lubyUnit * DomWdegLubySearch.luby(++restart[0]);
        }
    },
    /**
     * {@link #LUBY} and a geometric sequence, {@link #GEOMETRIC_BASE} growing by {@link
     * #GEOMETRIC_GROWTH} each time, taking turns by work: whichever has spent fewer failures so far
     * runs next. Each gets about half the effort, so a long enough run for a refutation arrives
     * after a bounded number of restarts, at the price of up to twice the time to a solution on a
     * satisfiable instance.
     */
    INTERLEAVED {
        @Override
        LongSupplier budgets(int lubyUnit) {
            double[] spent = {0, 0};
            long[] restart = {0, 0};
            return () -> {
                if (spent[0] <= spent[1]) {
                    long budget = lubyUnit * DomWdegLubySearch.luby(++restart[0]);
                    spent[0] += budget;
                    return budget;
                }
                long budget = (long) (GEOMETRIC_BASE * Math.pow(GEOMETRIC_GROWTH, restart[1]++));
                spent[1] += budget;
                return budget;
            };
        }
    };

    /** First budget of {@link #INTERLEAVED}'s geometric track. */
    static final long GEOMETRIC_BASE = 100;
    /** Factor by which {@link #INTERLEAVED}'s geometric budget grows from one restart to the next. */
    static final double GEOMETRIC_GROWTH = 1.5;

    /**
     * A fresh, stateful sequence of budgets, one per restart of a single {@link
     * DomWdegLubySearch#getSolution} call. {@code lubyUnit} scales the Luby sequence.
     */
    abstract LongSupplier budgets(int lubyUnit);
}
