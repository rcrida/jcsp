package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
     * Constructs an {@link Assignment} directly from an already-built values map, statistics,
     * listener, and cancellation token, skipping the builder's copies entirely — for hot
     * per-candidate construction paths (e.g.
     * {@link io.github.rcrida.jcsp.solver.LargeNeighborhoodSolver}'s per-combo enumeration) where
     * the caller already owns a freshly-built map it will never mutate again. {@code values} is
     * wrapped in an unmodifiable view (an O(1) wrap, not a copy). {@code statistics},
     * {@code listener}, and {@code cancellation} are required, not defaulted, so a caller deriving
     * from an existing {@link Assignment} must explicitly carry its {@link #getStatistics}/
     * {@link #listener}/{@link #cancellation} forward rather than silently starting a fresh,
     * disconnected one for the derived {@link Assignment}.
     */
    static Assignment ofTrusted(@NonNull Map<Variable<?>, Object> values, @NonNull Statistics statistics,
                                @NonNull SolverListener listener, @NonNull Cancellation cancellation) {
        return new MapAssignment(Collections.unmodifiableMap(values), statistics, listener, cancellation);
    }

    Statistics getStatistics();

    SolverListener listener();

    Cancellation cancellation();

    /** This assignment plus {@code variable = value}; counts as an explored node. */
    Assignment withValue(@NonNull Variable<?> variable, @NonNull Object value);

    /**
     * This assignment extended with every entry of {@code additions}, copying the values once
     * rather than once per entry as repeated {@link #withValue} would. Each entry still counts as
     * an explored node and fires {@link SolverListener#onNodeExplored}, but every event carries the
     * finished assignment, not an intermediate one.
     */
    Assignment withValues(@NonNull Map<Variable<?>, Object> additions);

    /** This assignment overlaid with every entry of {@code another}, whose statistics are added in. */
    Assignment merge(@NonNull Assignment another);

    /** A builder pre-loaded with this assignment's entries, statistics, listener and cancellation. */
    AssignmentBuilder toBuilder();

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
        return Assignment.of(partialValues(variables));
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
     * Accumulates entries into a {@link HashMap}, which {@link #build} hands to the assignment it
     * returns rather than copying; the next mutation after a build copies it first, so the builder
     * stays reusable and a {@link #toBuilder} followed by one {@link #build} copies the entries once.
     * Entry order is unspecified, as for {@link Assignment}.
     */
    final class AssignmentBuilder {
        private Map<Variable<?>, Object> values = new HashMap<>();
        private boolean shared;
        private Statistics statistics;
        private SolverListener listener;
        private Cancellation cancellation;

        private AssignmentBuilder() {
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
                values = new HashMap<>(values);
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
                    yield Collections.unmodifiableMap(values);
                }
            };
            return new MapAssignment(built, statistics, listener, cancellation);
        }
    }
}
