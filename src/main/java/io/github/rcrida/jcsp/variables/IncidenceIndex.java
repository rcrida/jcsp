package io.github.rcrida.jcsp.variables;

import org.jspecify.annotations.NonNull;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Which positions of a list each variable appears at, found through {@link Variable#getIndex()}
 * rather than by hashing: a lookup is a bounds check, one array read and an identity comparison.
 * <p>
 * A variable is found only if it is exactly one the index was built from, like {@link Variable}
 * equality itself; any other, including one issued later, finds no positions.
 */
public final class IncidenceIndex {
    private static final int[] NONE = new int[0];

    /** Per {@link Variable#getIndex()}, the variable indexed there, or null. */
    private final Variable<?>[] variablesByIndex;
    /** Per {@link Variable#getIndex()}, the ascending positions its variable appears at, or null. */
    private final int[][] positionsByIndex;
    private final Set<Variable<?>> variables;

    private IncidenceIndex(Variable<?>[] variablesByIndex, int[][] positionsByIndex, int size) {
        this.variablesByIndex = variablesByIndex;
        this.positionsByIndex = positionsByIndex;
        this.variables = new Variables(size);
    }

    /**
     * Indexes {@code scopes}, where {@code scopes.get(i)} is the variables position {@code i}
     * covers; an empty scope covers nothing.
     *
     * @throws IllegalArgumentException if a variable was not issued by a {@link Variable.Factory}, or
     *                                  if two share an index
     */
    public static IncidenceIndex of(@NonNull List<? extends Collection<? extends Variable<?>>> scopes) {
        int highest = -1;
        for (Collection<? extends Variable<?>> scope : scopes) {
            for (Variable<?> variable : scope) {
                if (variable.getIndex() == Variable.UNINDEXED) {
                    throw new IllegalArgumentException("Variable was not issued by a Variable.Factory: " + variable);
                }
                highest = Math.max(highest, variable.getIndex());
            }
        }
        Variable<?>[] variables = new Variable<?>[highest + 1];
        int[] counts = new int[highest + 1];
        int size = 0;
        for (Collection<? extends Variable<?>> scope : scopes) {
            for (Variable<?> variable : scope) {
                int index = variable.getIndex();
                if (variables[index] == null) {
                    variables[index] = variable;
                    size++;
                } else if (variables[index] != variable) {
                    throw new IllegalArgumentException("Variables " + variables[index] + " and " + variable
                            + " share index " + index);
                }
                counts[index]++;
            }
        }
        int[][] positions = new int[highest + 1][];
        for (int index = 0; index <= highest; index++) {
            if (counts[index] > 0) positions[index] = new int[counts[index]];
        }
        int[] filled = new int[highest + 1];
        for (int position = 0; position < scopes.size(); position++) {
            for (Variable<?> variable : scopes.get(position)) {
                int index = variable.getIndex();
                positions[index][filled[index]++] = position;
            }
        }
        return new IncidenceIndex(variables, positions, size);
    }

    /** The positions {@code variable} appears at, ascending; empty for none. Not to be modified. */
    public int[] positionsOf(@NonNull Variable<?> variable) {
        return contains(variable) ? positionsByIndex[variable.getIndex()] : NONE;
    }

    /** Every variable appearing at some position, in {@link Variable#getIndex()} order. Unmodifiable. */
    public Set<Variable<?>> variables() {
        return variables;
    }

    private boolean contains(Variable<?> variable) {
        int index = variable.getIndex();
        return index >= 0 && index < variablesByIndex.length && variablesByIndex[index] == variable;
    }

    private final class Variables extends AbstractSet<Variable<?>> {
        private final int size;

        Variables(int size) {
            this.size = size;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object o) {
            return o instanceof Variable<?> variable && IncidenceIndex.this.contains(variable);
        }

        @Override
        public Iterator<Variable<?>> iterator() {
            return new Iterator<>() {
                private int next = advance(0);

                private int advance(int from) {
                    while (from < variablesByIndex.length && variablesByIndex[from] == null) from++;
                    return from;
                }

                @Override
                public boolean hasNext() {
                    return next < variablesByIndex.length;
                }

                @Override
                public Variable<?> next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    Variable<?> variable = variablesByIndex[next];
                    next = advance(next + 1);
                    return variable;
                }
            };
        }
    }
}
