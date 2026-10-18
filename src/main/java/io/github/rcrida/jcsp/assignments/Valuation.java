package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An immutable mapping from variables to values, and nothing else: the part of an {@link
 * Assignment} that can be kept, compared and shared without dragging along the {@link Statistics},
 * {@link io.github.rcrida.jcsp.solver.listener.SolverListener} and {@link
 * io.github.rcrida.jcsp.solver.Cancellation} of the solve it came from.
 * <p>
 * The values are reached only through the accessors below, never as a {@link Map}, so an
 * implementation is free to store them however is cheapest to extend. Entry order is unspecified.
 * Two valuations are equal when they hold the same variable→value entries, whatever their
 * implementations, and then must have the same {@code hashCode}: the sum over entries of {@code
 * variable.hashCode() ^ (value.hashCode() * 0x9E3779B1)}. An implementation never maps a variable
 * to {@code null}.
 */
public interface Valuation {

    /** This valuation's value for {@code variable}, or {@code null} if it has none. */
    @Nullable Object getValueOrNull(@NonNull Variable<?> variable);

    /**
     * Whether {@code variable} has a value here — exactly equivalent to {@code
     * getValue(variable).isPresent()}, but without the {@link Optional} wrapper.
     * <p>
     * Exists because the "is this assigned yet?" question is asked far more often than the value
     * itself is wanted, in loops whose iteration count is the whole point: {@link
     * io.github.rcrida.jcsp.solver.backtrackingsearch.selector.DomWdegVariableSelector} asks it once
     * per (unassigned variable × incident constraint × node), where allocating an {@link Optional}
     * per question made it one of the largest allocation sources in the solver.
     */
    boolean isAssigned(@NonNull Variable<?> variable);

    /** The number of assigned variables. */
    int size();

    /** The assigned variables, as an unmodifiable set. */
    Set<Variable<?>> variables();

    /**
     * The entries, read-only: the entries themselves reject {@link Map.Entry#setValue}. For loops
     * that need every entry and may exit early, where {@link #variables} plus {@link
     * #getValueOrNull} would pay a second lookup per entry and {@link #entryStream} an allocation.
     */
    Iterable<Map.Entry<Variable<?>, Object>> entries();

    /** Calls {@code action} once per assigned variable and its value. */
    default void forEach(@NonNull BiConsumer<? super Variable<?>, ? super Object> action) {
        for (Map.Entry<Variable<?>, Object> entry : entries()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    /** {@link #entries} as a {@link Stream}, for the paths where allocating one does not matter. */
    default Stream<Map.Entry<Variable<?>, Object>> entryStream() {
        return StreamSupport.stream(entries().spliterator(), false);
    }

    @SuppressWarnings("unchecked")
    default <T> Optional<T> getValue(@NonNull Variable<T> variable) {
        return Optional.ofNullable((T) getValueOrNull(variable));
    }

    /** Whether every one of {@code variables} is assigned. */
    default boolean containsAll(@NonNull Collection<? extends Variable<?>> variables) {
        for (Variable<?> variable : variables) {
            if (!isAssigned(variable)) return false;
        }
        return true;
    }

    /** Whether at least one of {@code variables} is assigned. */
    default boolean containsAny(@NonNull Collection<? extends Variable<?>> variables) {
        for (Variable<?> variable : variables) {
            if (isAssigned(variable)) return true;
        }
        return false;
    }

    /**
     * Iterates {@code variables} and looks each one up directly, rather than scanning every entry
     * and filtering by membership in {@code variables} — {@code variables} is typically a single
     * constraint's own handful of variables, far smaller than the full valuation (profiling
     * {@link io.github.rcrida.jcsp.constraints.nary.UniformNaryConstraint#isSatisfiedBy}, this
     * method's dominant caller, found the original O(|values|)-per-call scan to be the runaway
     * cost in {@link io.github.rcrida.jcsp.solver.LargeNeighborhoodSolver}'s per-combo,
     * per-constraint violation checking).
     * Backed by a plain {@link java.util.HashMap}: entry order is unspecified.
     */
    default Map<Variable<?>, Object> partialValues(@NonNull Set<? extends Variable<?>> variables) {
        Map<Variable<?>, Object> partial = new HashMap<>();
        for (Variable<?> variable : variables) {
            Object value = getValueOrNull(variable);
            if (value != null) partial.put(variable, value);
        }
        return Collections.unmodifiableMap(partial);
    }
}
