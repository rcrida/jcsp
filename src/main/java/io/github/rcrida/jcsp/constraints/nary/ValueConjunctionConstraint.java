package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code AND(v1 <op> target1, v2 <op> target2, ..., vn <op> targetn)}: satisfied only when every
 * variable relates to its own designated target value under {@code operator}. The positive dual of
 * {@link GroundNogoodConstraint} (which forbids one specific ground combination via
 * {@code OR(x_i != v_i)}) — this requires one instead. Generalizes beyond pure equality the same
 * way {@link OrderedConstraint} generalizes {@link IncreasingConstraint}/{@link
 * DecreasingConstraint}'s two fixed directions into one runtime-chosen operator, rather than
 * needing a separate class per relation.
 * <p>
 * Built specifically to replace two places that used to assemble this same "several ground pins
 * must all hold" shape ad hoc out of weaker pieces: {@link
 * io.github.rcrida.jcsp.parser.xcsp3.Xcsp3CallbackHandler#buildCtrInstantiation}'s reified case
 * (previously {@code AndConstraint.of(Set.of(UnaryValueConstraint...))} — {@link AndConstraint}
 * skips any conjunct that isn't itself {@link Propagatable}, and {@code UnaryConstraint} isn't, so
 * that combination got zero incremental narrowing, only a final {@code isSatisfiedBy} check), and
 * a new {@code iff(eq(...), eq(...))} recognizer for XCSP3's {@code intension} construct (see
 * {@code Xcsp3CallbackHandler#recognizeGroundEquality}), where this is the reified body on each
 * side of the biconditional.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ValueConjunctionConstraint<T> extends NaryConstraint implements Propagatable {
    @Getter @NonNull private final Map<Variable<T>, T> literals;
    @Getter @NonNull private final Operator operator;

    public static <T> ValueConjunctionConstraint<T> of(@NonNull Map<Variable<T>, T> literals, @NonNull Operator operator) {
        assert !literals.isEmpty() : "ValueConjunctionConstraint requires at least one literal";
        return ValueConjunctionConstraint.<T>builder()
                .variables(Set.copyOf(literals.keySet()))
                .literals(Map.copyOf(literals))
                .operator(operator)
                .build();
    }

    /**
     * Unlike {@link ValueDisjunctionConstraint}'s OR-shape (which must wait for every literal to be
     * assigned before it can be disproven), a single literal already known to violate {@code
     * operator} dooms the whole conjunction immediately, regardless of whether the others are even
     * assigned yet — so this returns {@code false} as soon as one is found, with no
     * {@code allAssigned} bookkeeping needed.
     */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (Map.Entry<Variable<T>, T> literal : literals.entrySet()) {
            Optional<T> value = assignment.getValue(literal.getKey());
            if (value.isEmpty()) continue;
            if (!operator.compare(value.get(), literal.getValue())) return false;
        }
        return true;
    }

    /**
     * Real propagation only for {@link Operator#EQ}/{@link Operator#NEQ} — both are unconditional,
     * independent per-literal operations (unlike {@link ValueDisjunctionConstraint}'s OR-shape,
     * every literal here is required regardless of the others, so there's no "wait until only one
     * is undetermined" step): {@code EQ} narrows each variable straight to the singleton
     * {@code {target}} (infeasible immediately if {@code target} isn't even in the domain);
     * {@code NEQ} deletes {@code target} from each variable's domain (infeasible if that empties
     * it). The four ordering operators return a no-op update — sound but not maximally tight,
     * mirroring the same "only a subset of operators propagate" pattern already used elsewhere in
     * this codebase (e.g. {@link io.github.rcrida.jcsp.constraints.NumericBounds#propagateWeightedSumVsTarget}) —
     * no real XCSP3 instance in this corpus currently needs ordering-operator propagation here.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        if (operator != Operator.EQ && operator != Operator.NEQ) return Optional.of(Map.of());

        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (Map.Entry<Variable<T>, T> literal : literals.entrySet()) {
            DiscreteDomain<T> domain = (DiscreteDomain<T>) domains.get(literal.getKey());
            if (operator == Operator.EQ) {
                if (!domain.contains(literal.getValue())) return Optional.empty();
                if (domain.isSingleton()) continue;
                var narrowed = domain.toBuilder();
                for (T value : domain.toList()) {
                    if (!value.equals(literal.getValue())) narrowed.delete(value);
                }
                updated.put(literal.getKey(), narrowed.build());
            } else {
                if (!domain.contains(literal.getValue())) continue;
                var narrowed = domain.toBuilder().delete(literal.getValue()).build();
                if (narrowed.isEmpty()) return Optional.empty();
                updated.put(literal.getKey(), narrowed);
            }
        }
        return Optional.of(updated);
    }

    /**
     * Re-derives the same scan {@link #propagate} did (no state threaded between the two calls,
     * matching this codebase's established convention) to find the one literal already
     * unsatisfiable on its own under the current domains, then cites <em>just that one variable</em>
     * via {@link ValueSetNogoodConstraint#fromCurrentState} — sound without requiring it to be
     * singleton, and tighter than {@link ValueDisjunctionConstraint}'s own citation (which needs
     * every literal collectively, since any one succeeding would save an OR): here, one failing
     * literal alone dooms the whole conjunction regardless of what any other variable does.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        for (Map.Entry<Variable<T>, T> literal : literals.entrySet()) {
            if (literalImpossible(domains.get(literal.getKey()), literal.getValue())) {
                return ValueSetNogoodConstraint.fromCurrentState(Set.of(literal.getKey()), domains);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether {@code domain} already rules out {@code target} under {@link #operator}. Only {@code
     * EQ}/{@code NEQ} are handled -- the four ordering operators never trigger this: {@link
     * #propagate} always returns a no-op for them, so this constraint never itself reports
     * infeasible under one, meaning this method is only ever reached via {@code EQ}/{@code NEQ} in
     * the real call path from {@link #propagate} -- but must still answer safely (not throw) if
     * called standalone with an ordering operator, matching this codebase's "safe to call
     * explainInfeasible even when not preceded by a failing propagate()" convention.
     */
    private boolean literalImpossible(Domain<?> domain, T target) {
        if (operator == Operator.EQ) return !domain.contains(target);
        if (operator == Operator.NEQ) return domain.isSingleton() && domain.contains(target);
        return false;
    }

    @Override
    public String getRelation() {
        return literals.entrySet().stream()
                .map(e -> e.getKey() + " " + operator.symbol + " " + e.getValue())
                .sorted()
                .collect(Collectors.joining(" AND "));
    }
}
