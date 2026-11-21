package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Breaks ties with one tie-break during a search's first run and with another from the second
 * restart on. A search that never restarts stays with the first. Both are told of every restart.
 */
final class FirstRunTieBreak implements TieBreak {

    private final TieBreak first;
    private final TieBreak later;
    private int restarts;

    FirstRunTieBreak(@NonNull TieBreak first, @NonNull TieBreak later) {
        this.first = first;
        this.later = later;
    }

    @Override
    public @NonNull Variable<?> choose(@NonNull List<Variable<?>> tied) {
        return (restarts <= 1 ? first : later).choose(tied);
    }

    @Override
    public void onRestart(@Nullable Random random) {
        restarts++;
        first.onRestart(random);
        later.onRestart(random);
    }
}
