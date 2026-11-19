package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import io.github.rcrida.jcsp.variables.VariableMap;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * A {@link Valuation} plus the {@link Statistics}, {@link SolverListener} and {@link Cancellation}
 * of the solve it belongs to. Equality is the {@link Valuation}'s: statistics, listener and
 * cancellation play no part.
 */
public interface Assignment extends Valuation {

    static Assignment empty() {
        return builder().build();
    }

    static Assignment of(Map<? extends Variable<?>, ?> values) {
        return builder().values(values).build();
    }

    static AssignmentBuilder builder() {
        return new AssignmentBuilder();
    }

    /**
     * An {@link Assignment} over {@code values}, wrapped in an unmodifiable view rather than copied,
     * with the given statistics, listener and cancellation. The caller must never mutate {@code
     * values} afterwards.
     *
     * @deprecated it saves nothing over the builder, which hands its own map to the assignment it
     * builds without copying it: use {@link #builder}, or {@link #toBuilder} to extend an existing
     * assignment with its statistics, listener and cancellation carried over.
     */
    @Deprecated
    static Assignment ofTrusted(@NonNull Map<Variable<?>, Object> values, @NonNull Statistics statistics,
                                @NonNull SolverListener listener, @NonNull Cancellation cancellation) {
        return new MapAssignment(Collections.unmodifiableMap(values), statistics, listener, cancellation);
    }

    Statistics getStatistics();

    SolverListener listener();

    Cancellation cancellation();

    /**
     * A builder pre-loaded with this assignment's entries, statistics, listener and cancellation, sized
     * for one more entry than this holds, which is what most callers add; see {@link
     * AssignmentBuilder#copying}.
     */
    default AssignmentBuilder toBuilder() {
        return AssignmentBuilder.copying(this, 1);
    }

    /**
     * This assignment plus {@code variable = value}; counts as an explored node. Layered over this
     * assignment by {@link OverlayAssignment#over} where that copies nothing.
     */
    default Assignment withValue(@NonNull Variable<?> variable, @NonNull Object value) {
        Assignment next = OverlayAssignment.over(this, variable, value);
        next.getStatistics().incrementNodesExplored();
        next.listener().onNodeExplored(variable, value, next);
        return next;
    }

    /**
     * This assignment extended with every entry of {@code additions}: a few are layered over this
     * assignment as {@link #withValue} does, many are copied into one map once rather than once per
     * entry. Each entry still counts as an explored node and fires
     * {@link SolverListener#onNodeExplored}, but every event carries the finished assignment, not an
     * intermediate one.
     */
    default Assignment withValues(@NonNull Map<Variable<?>, Object> additions) {
        Assignment next;
        if (additions.size() <= OverlayAssignment.MAX_DEPTH) {
            Assignment layered = this;
            for (Map.Entry<Variable<?>, Object> addition : additions.entrySet()) {
                layered = OverlayAssignment.over(layered, addition.getKey(), addition.getValue());
            }
            next = layered;
        } else {
            next = AssignmentBuilder.copying(this, additions.size()).values(additions).build();
        }
        additions.forEach((variable, value) -> {
            getStatistics().incrementNodesExplored();
            listener().onNodeExplored(variable, value, next);
        });
        return next;
    }

    /**
     * This assignment overlaid with every entry of {@code another}, whose statistics are added in unless
     * they are this assignment's own: two solutions of one solve share its {@link Statistics}, which
     * already holds both searches' counts, and adding it to itself would double it.
     */
    default Assignment merge(@NonNull Assignment another) {
        AssignmentBuilder builder = AssignmentBuilder.copying(this, another.size());
        another.forEach(builder::value);
        Assignment merged = builder.build();
        if (another.getStatistics() != merged.getStatistics()) {
            merged.getStatistics().add(another.getStatistics());
        }
        return merged;
    }

    /**
     * Same lookup as {@link #partialValues} but wrapped in a full {@link Assignment} — needed by
     * callers that rely on {@link Assignment} identity/equality (e.g.
     * {@link io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint} comparing against a
     * table of tuples), unlike
     * {@link io.github.rcrida.jcsp.constraints.nary.UniformNaryConstraint#isSatisfiedBy}, which
     * only ever needs the raw value collection and calls {@link #partialValues} directly to skip
     * this wrapping.
     */
    default Assignment extractPartialAssignment(@NonNull Set<? extends Variable<?>> variables) {
        AssignmentBuilder builder = new AssignmentBuilder(VariableMap.withExpectedSize(variables.size()));
        for (Variable<?> variable : variables) {
            Object value = getValueOrNull(variable);
            if (value != null) builder.value(variable, value);
        }
        return builder.build();
    }

    default boolean isConsistent(ConstraintSatisfactionProblem csp) {
        AssignmentValidation.validate(this, csp);
        return isConsistentAmong(csp.getConstraints());
    }

    /**
     * Sibling of {@link #isConsistent}, checked against a caller-supplied {@code candidateConstraints}
     * rather than {@code csp.getConstraints()} -- for a caller that has already narrowed the full
     * constraint set down to the ones that could possibly be affected (e.g. {@link
     * io.github.rcrida.jcsp.solver.tree.TreeSolver} pre-filtering to just the constraints touching a
     * single newly-assigned variable), so repeated calls don't re-scan the whole set every time.
     * Skips the domain check, since callers using this narrower form are typically
     * re-checking the same {@link ConstraintSatisfactionProblem} they already validated against once.
     * <p>
     * Runs on every node of every terminal solver, so the per-constraint "does it reference an
     * assigned variable" test is {@link #containsAny} over the constraint's own variables in a
     * plain loop over {@code candidateConstraints}, rather than a nested {@link java.util.stream.Stream}
     * pipeline built per candidate -- JFR profiling found the same stream-construction cost here
     * that {@code NogoodFixpointConsistency#relevant} had, fixed the same way there first.
     * <p>
     * The per-evaluation {@link Statistics#incrementConstraintChecks} below is deliberate despite
     * this loop being that counter's only writer and running tens of millions of times per solve --
     * batching it per call was measured as neutral and reverted; see {@link Statistics}' own Javadoc.
     */
    default boolean isConsistentAmong(@NonNull Collection<? extends Constraint> candidateConstraints) {
        for (Constraint constraint : candidateConstraints) {
            if (!containsAny(constraint.getVariables())) continue;
            getStatistics().incrementConstraintChecks();
            if (!constraint.isSatisfiedBy(this)) {
                if (constraint instanceof NogoodConstraint) getStatistics().incrementNogoodRejections();
                return false;
            }
        }
        return true;
    }

    default boolean isComplete(ConstraintSatisfactionProblem csp) {
        AssignmentValidation.validate(this, csp);
        return csp.getVariableDomains().keySet().stream().allMatch(this::isAssigned);
    }

    default boolean isSolution(ConstraintSatisfactionProblem csp) {
        return isComplete(csp) && isConsistent(csp);
    }

    /**
     * Accumulates entries into a {@link VariableMap}, which {@link #build} hands to the assignment it
     * returns rather than copying; the next mutation after a build copies it first, so the builder
     * stays reusable and a {@link #toBuilder} followed by one {@link #build} copies the entries once.
     * Entry order is unspecified, as for {@link Assignment}.
     */
    final class AssignmentBuilder {
        private VariableMap<Object> values;
        private boolean shared;
        private Statistics statistics;
        private SolverListener listener;
        private Cancellation cancellation;

        private AssignmentBuilder() {
            this(new VariableMap<>());
        }

        private AssignmentBuilder(VariableMap<Object> values) {
            this.values = values;
        }

        /**
         * A builder pre-loaded with {@code source}'s entries, statistics, listener and cancellation, with
         * room for {@code extraEntries} more. An assignment over a {@link VariableMap} is copied by
         * {@link VariableMap#copy} as whole arrays; any other is copied through {@link #forEach}, which
         * hands over each key and value without the entry objects iterating {@link #entries} would make.
         * {@link OverlayAssignment} copies its base this way and then adds its layers.
         */
        static AssignmentBuilder copying(Assignment source, int extraEntries) {
            AssignmentBuilder builder;
            if (source instanceof MapAssignment map && map.values() instanceof VariableMap<Object> entries) {
                builder = new AssignmentBuilder(entries.copy(extraEntries));
            } else {
                builder = new AssignmentBuilder(VariableMap.withExpectedSize(source.size() + extraEntries));
                source.forEach(builder::value);
            }
            return builder.statistics(source.getStatistics()).listener(source.listener())
                    .cancellation(source.cancellation());
        }

        public AssignmentBuilder value(Variable<?> variable, Object value) {
            owned().put(variable, value);
            return this;
        }

        public AssignmentBuilder values(Map<? extends Variable<?>, ?> additions) {
            owned().putAll(additions);
            return this;
        }

        private Map<Variable<?>, Object> owned() {
            if (shared) {
                values = values.copy(1);
                shared = false;
            }
            return values;
        }

        public AssignmentBuilder statistics(Statistics statistics) {
            this.statistics = statistics;
            return this;
        }

        public AssignmentBuilder listener(SolverListener listener) {
            this.listener = listener;
            return this;
        }

        public AssignmentBuilder cancellation(Cancellation cancellation) {
            this.cancellation = cancellation;
            return this;
        }

        public Assignment build() {
            Map<Variable<?>, Object> built = switch (values.size()) {
                case 0 -> Collections.emptyMap();
                case 1 -> {
                    Map.Entry<Variable<?>, Object> only = values.entrySet().iterator().next();
                    yield Collections.singletonMap(only.getKey(), only.getValue());
                }
                default -> {
                    shared = true;
                    yield values;
                }
            };
            return new MapAssignment(built, statistics, listener, cancellation);
        }
    }
}
