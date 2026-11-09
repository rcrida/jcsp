package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** {@link NeighbourhoodGenerator.Factory#RANDOM}: a uniformly random subset of the decisions. */
final class RandomNeighbourhood implements NeighbourhoodGenerator {

    @Override
    public Set<Variable<?>> free(@NonNull List<Variable<?>> decisions, @NonNull Assignment incumbent, int count,
                                 @NonNull Random random) {
        List<Variable<?>> shuffled = new ArrayList<>(decisions);
        Collections.shuffle(shuffled, random);
        return new LinkedHashSet<>(shuffled.subList(0, count));
    }
}
