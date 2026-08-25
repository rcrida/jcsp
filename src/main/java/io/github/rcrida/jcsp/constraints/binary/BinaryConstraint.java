package io.github.rcrida.jcsp.constraints.binary;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.arc.Arc;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Represents a binary constraint in a constraint satisfaction problem (CSP).
 * A binary constraint defines a condition or restriction that involves two variables.
 * It specifies the relationship between the values of the left and right variables
 * that must be satisfied in order for the constraint to hold.
 */
@Value
@NonFinal
@SuperBuilder
public abstract class BinaryConstraint<L, R> implements Constraint {
    @NonNull Variable<L> left;
    @NonNull Variable<R> right;

    /**
     * Lazily-built, immutable pair of arcs for this constraint -- {@code (left, right)} and
     * {@code (right, left)} -- cached per instance rather than reallocated on every call. Computed
     * once (a pure function of the immutable {@link #left}/{@link #right} fields) and read-only
     * thereafter, so it's safe to share across concurrently-executing search threads (e.g. {@link
     * io.github.rcrida.jcsp.solver.RaceLocalSolver} runs multiple local-search strategies over the
     * same constraint objects in parallel) with no synchronization beyond the initial
     * compare-and-set -- a benign race if two threads build it concurrently on first use, since both
     * computations are equivalent. Every caller of {@link #getArcs} for a given constraint instance
     * now observes the exact same {@link Arc} objects, which matters beyond simple allocation
     * pressure: {@link io.github.rcrida.jcsp.consistency.arc.AC3BitRm} keys some of its own internal
     * structures by {@link Arc} identity (see its own top-level Javadoc and ADR-0022), and depends on
     * every caller-supplied {@link Arc} sourced from {@link #getArcs} (e.g. {@code MAC}'s own queue
     * construction) already matching its internal canonical instance without needing translation.
     * Excluded from {@code equals}/{@code hashCode} (mirrors {@link #left}/{@link #right} themselves
     * already determining constraint identity) and from the {@code @SuperBuilder}-generated builder,
     * since a field with a direct initializer and no {@code @Builder.Default} is a
     * Lombok-builder-invisible derived field, not a settable one -- exactly what's wanted here (every
     * instance starts with its own fresh, empty cache).
     */
    @EqualsAndHashCode.Exclude
    AtomicReference<List<Arc>> arcsCache = new AtomicReference<>();

    @Override
    public final boolean isSatisfiedBy(@NonNull Assignment assignment) {
        return assignment.getValue(left)
                .flatMap(leftValue -> assignment.getValue(right)
                        .map(rightValue -> isSatisfiedBy(leftValue, rightValue)))
                .orElse(true);
    }

    public Variable<?> getNeighbour(@NonNull Variable<?> variable) {
        assert variable == left || variable == right;
        return variable == left ? right : left;
    }

    public abstract boolean isSatisfiedBy(@NonNull L leftValue, @NonNull R rightValue);

    /**
     * Checks satisfaction directly from two raw values keyed by {@code arc}'s own endpoints,
     * without constructing an {@link Assignment} — {@code arc} is assumed to be one of this
     * constraint's own two arcs (see {@link #getArcs}), i.e. {@code {arc.getFrom(), arc.getTo()}
     * == {left, right}} in some order, so which of {@code fromValue}/{@code toValue} is the left
     * vs. right value can be determined directly rather than needing an {@link Assignment} to look
     * them up by variable. Exists for {@link io.github.rcrida.jcsp.consistency.arc.AC3#revise},
     * which checks every value pair in a domain product during arc revision — profiling found
     * building a fresh {@link Assignment} (with its own {@code @Singular} map and a new {@link
     * io.github.rcrida.jcsp.assignments.Statistics} instance) per pair to be the dominant cost there.
     */
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedByArcValues(@NonNull Arc arc, @NonNull Object fromValue, @NonNull Object toValue) {
        return arc.getFrom().equals(left)
                ? isSatisfiedBy((L) fromValue, (R) toValue)
                : isSatisfiedBy((L) toValue, (R) fromValue);
    }

    @Override
    public Set<Variable<?>> getVariables() {
        return Set.of(left, right);
    }

    public Stream<Arc> getArcs() {
        List<Arc> existing = arcsCache.get();
        if (existing != null) return existing.stream();
        List<Arc> built = List.of(Arc.of(left, right), Arc.of(right, left));
        arcsCache.compareAndSet(null, built);
        return arcsCache.get().stream();
    }

    @Override
    public String toString() {
        return "<(" + left + ", " + right + "), " + getRelation() + ">";
    }
}
