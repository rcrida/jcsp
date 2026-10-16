package io.github.rcrida.jcsp;

import io.github.rcrida.jcsp.variables.Variable;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The neighbours of one variable -- every variable that shares a constraint with it -- built from
 * the scopes of the constraints it is in rather than held. Read-only.
 * <p>
 * A constraint over {@code k} variables makes each of them the neighbour of the other {@code k - 1},
 * so holding every set costs {@code k²} entries; see {@link ConstraintGraph}. Every read here builds
 * the set afresh with exactly the operations the held one is built with ({@link #build}), so
 * iteration order is the same as the {@link HashSet} it stands in for, and nothing downstream that
 * depends on that order can tell the two apart. {@link #size} is remembered; the rest are answered
 * from the scopes where that is cheaper than building.
 */
final class LazyNeighbours extends AbstractSet<Variable<?>> {
    private final Variable<?> self;
    private final List<? extends Collection<Variable<?>>> scopes;
    private final boolean skipSelfWhenAdding;
    private int size = -1;

    LazyNeighbours(Variable<?> self, List<? extends Collection<Variable<?>>> scopes, boolean skipSelfWhenAdding) {
        this.self = self;
        this.scopes = scopes;
        this.skipSelfWhenAdding = skipSelfWhenAdding;
    }

    /** An upper bound on the neighbours: the scopes' sizes, counting a repeat and the variable itself. */
    static long upperBound(List<? extends Collection<Variable<?>>> scopes) {
        long bound = 0;
        for (Collection<Variable<?>> scope : scopes) {
            bound += scope.size();
        }
        return bound;
    }

    /**
     * The set of {@code self}'s neighbours, from the scopes of its constraints in constraint order.
     * Which variables it holds is the same either way; {@code skipSelfWhenAdding} picks which of the
     * two ways a {@link HashSet} can end up built is reproduced. Adding every scope whole and then
     * removing {@code self} can grow the table one element earlier than never adding it, so the two
     * can iterate in different orders.
     */
    static Set<Variable<?>> build(Variable<?> self, List<? extends Collection<Variable<?>>> scopes,
                                  boolean skipSelfWhenAdding) {
        Set<Variable<?>> neighbours = new HashSet<>();
        for (Collection<Variable<?>> scope : scopes) {
            if (skipSelfWhenAdding) {
                for (Variable<?> other : scope) {
                    if (other != self) {
                        neighbours.add(other);
                    }
                }
            } else {
                neighbours.addAll(scope);
            }
        }
        if (!skipSelfWhenAdding) {
            neighbours.remove(self);
        }
        return neighbours;
    }

    @Override
    public Iterator<Variable<?>> iterator() {
        return Collections.unmodifiableSet(build(self, scopes, skipSelfWhenAdding)).iterator();
    }

    @Override
    public int size() {
        if (size < 0) {
            size = build(self, scopes, skipSelfWhenAdding).size();
        }
        return size;
    }

    @Override
    public boolean contains(Object candidate) {
        if (self.equals(candidate)) {
            return false;
        }
        for (Collection<Variable<?>> scope : scopes) {
            if (scope.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
