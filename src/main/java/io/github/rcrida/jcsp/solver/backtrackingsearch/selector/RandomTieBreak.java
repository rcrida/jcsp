package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Random;

/**
 * Picks a tied variable uniformly at random from the source the current restart supplied, or the
 * first tied variable when it supplied none.
 */
final class RandomTieBreak implements TieBreak {

    private @Nullable Random random;

    @Override
    public @NonNull Variable<?> choose(@NonNull List<Variable<?>> tied) {
        return random == null ? tied.get(0) : tied.get(random.nextInt(tied.size()));
    }

    @Override
    public void onRestart(@Nullable Random random) {
        this.random = random;
    }
}
