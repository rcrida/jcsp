package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link NogoodConstraint} forbidding a whole discrete value set per variable: the clause
 * {@code OR(x1 ∉ S1, x2 ∉ S2, ..., xk ∉ Sk)} over {@link #forbidden} — violated only when every
 * one of its variables currently holds a value inside its own forbidden {@link Set}. Sits between
 * {@link GroundNogoodConstraint} (forbids exactly one value per variable) and
 * {@link RangeNogoodConstraint} (forbids a whole numeric interval per variable, but only when that
 * interval exactly reproduces the domain — see {@link RangeNogoodConstraint}'s own Javadoc on
 * gaplessness): this type cites a variable's <em>exact</em> current {@link DiscreteDomain} value
 * set, gaps and all, so it never needs a gaplessness gate the way {@link RangeNogoodConstraint}
 * does — the forbidden set always denotes precisely the domain it was built from, never a
 * superset.
 * <p>
 * Added specifically to close a real unsoundness: several propagators (see
 * {@link NaryElementConstraint#explainInfeasible}, and the binary comparator family's former use
 * of {@link Propagatable#addIfSingleton} in isolation on each side) used to omit a non-singleton
 * variable from their citation entirely, silently assuming its current domain was unconditionally
 * available — unsound whenever that domain had actually been narrowed by a <em>different</em>
 * constraint. Citing the variable's exact current value set here, instead of omitting it, closes
 * that gap without requiring the domain to be singleton or gapless.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ValueSetNogoodConstraint extends NaryConstraint implements NogoodConstraint {
    @NonNull private final Map<Variable<?>, Set<Object>> forbidden;

    public static ValueSetNogoodConstraint of(@NonNull Map<Variable<?>, Set<Object>> forbidden) {
        // An empty forbidden map is the empty clause -- see GroundNogoodConstraint.of for why
        // callers must not record one.
        assert !forbidden.isEmpty() : "ValueSetNogoodConstraint requires at least one forbidden set";
        return ValueSetNogoodConstraint.builder()
                .variables(forbidden.keySet())
                .forbidden(Map.copyOf(forbidden))
                .build();
    }

    /**
     * Builds a nogood citing every one of {@code variables}' exact current {@link DiscreteDomain}
     * value set. Sound whenever the constraint these variables jointly belong to has just reported
     * infeasibility via {@link Propagatable#propagate}, the same soundness argument
     * {@link RangeNogoodConstraint#fromCurrentBounds} uses — no propagator-specific reasoning is
     * needed beyond that. Returns {@link Optional#empty()} if any cited variable's domain isn't a
     * {@link DiscreteDomain} (e.g. a {@link io.github.rcrida.jcsp.domains.BoundedDomain}, for which
     * an exact finite value set isn't meaningful — {@link RangeNogoodConstraint#fromCurrentBounds}
     * is the right citation there instead).
     */
    public static Optional<NogoodConstraint> fromCurrentValues(
            @NonNull Collection<? extends Variable<?>> variables, @NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Set<Object>> forbidden = new HashMap<>();
        for (Variable<?> variable : variables) {
            Domain<?> domain = domains.get(variable);
            if (!(domain instanceof DiscreteDomain<?> discrete)) return Optional.empty();
            forbidden.put(variable, new HashSet<>(discrete.toList()));
        }
        return Optional.of(of(forbidden));
    }

    /**
     * Tries a {@link GroundNogoodConstraint} first, via {@link Propagatable#allSingletonReason} —
     * tighter and cheaper to check than a value-set citation, and applicable whenever every one of
     * {@code variables} already happens to be singleton. Falls back to {@link #fromCurrentValues}
     * (a genuine value-set citation, sound regardless of whether any variable is singleton or
     * gapless) otherwise. Returns {@link Optional#empty()} only if that also fails (some variable
     * isn't a {@link DiscreteDomain} at all) — callers wanting a citation for a possibly-continuous
     * variable set should fall back further to {@link RangeNogoodConstraint#fromCurrentBounds}.
     */
    public static Optional<NogoodConstraint> fromCurrentState(
            @NonNull Collection<? extends Variable<?>> variables, @NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> ground = Propagatable.allSingletonReason(variables, domains);
        if (!ground.isEmpty()) return GroundNogoodConstraint.fromReason(ground);
        return fromCurrentValues(variables, domains);
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (var entry : forbidden.entrySet()) {
            var value = assignment.getValue(entry.getKey());
            if (value.isEmpty() || !entry.getValue().contains(value.get())) return true;
        }
        return false;
    }

    private enum Literal { SATISFIED, FALSIFIED, UNDETERMINED }

    /**
     * Classifies a single literal ({@code variable ∉ forbiddenSet}) against the variable's current
     * domain: <b>satisfied</b> when the domain doesn't overlap {@code forbiddenSet} at all (the
     * literal is guaranteed true regardless of which value the variable eventually takes);
     * <b>falsified</b> when the domain is entirely contained within {@code forbiddenSet} (every
     * remaining value is forbidden); <b>undetermined</b> otherwise. Only meaningful for a
     * {@link DiscreteDomain} (this type's sole intended domain kind, per {@link
     * #fromCurrentValues}); any other {@link Domain} kind degrades gracefully to permanently
     * undetermined (no information, never wrongly resolved) rather than being rejected, matching
     * {@link RangeNogoodConstraint}'s own graceful-degradation contract for a mismatched domain
     * kind.
     */
    private static Literal classify(Domain<?> domain, Set<Object> forbiddenSet) {
        if (!(domain instanceof DiscreteDomain<?> discrete)) return Literal.UNDETERMINED;
        boolean anyInside = false;
        boolean anyOutside = false;
        for (Object value : discrete.toList()) {
            if (forbiddenSet.contains(value)) anyInside = true; else anyOutside = true;
            if (anyInside && anyOutside) return Literal.UNDETERMINED;
        }
        return anyInside ? Literal.FALSIFIED : Literal.SATISFIED;
    }

    /**
     * Generalised unit propagation over {@code OR(x1 ∉ S1, ..., xk ∉ Sk)}, following the same shape
     * as {@link GroundNogoodConstraint#propagate}/{@link RangeNogoodConstraint#propagate}: all
     * falsified → infeasible; exactly one undetermined (every other literal falsified) → prune that
     * one variable's domain by deleting every value in its own forbidden set — but only when its
     * domain is a {@link DiscreteDomain} (mirroring {@link GroundNogoodConstraint#propagate}'s own
     * guard): {@link #classify} already degrades a non-{@link DiscreteDomain} to permanently
     * undetermined, so this guard is what keeps that case a safe no-op instead of an invalid cast.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Variable<?> undeterminedVar = null;
        int undeterminedCount = 0;

        for (var entry : forbidden.entrySet()) {
            Variable<?> var = entry.getKey();
            Literal literal = classify(domains.get(var), entry.getValue());
            if (literal == Literal.SATISFIED) {
                return Optional.of(Map.of()); // this literal is guaranteed true: clause permanently satisfied
            }
            if (literal == Literal.UNDETERMINED) {
                undeterminedCount++;
                undeterminedVar = var;
            }
        }

        if (undeterminedCount == 0) return Optional.empty(); // every literal falsified

        if (undeterminedCount == 1 && domains.get(undeterminedVar) instanceof DiscreteDomain<?> dom) {
            // classify() already established this domain overlaps forbiddenSet partially (that's
            // what makes it UNDETERMINED rather than SATISFIED/FALSIFIED), so at least one value
            // is always deleted below -- unconditionally returning the pruned domain here (rather
            // than guarding on a "did anything change" flag) is provably safe, not an approximation.
            DiscreteDomain<Object> discreteDom = (DiscreteDomain<Object>) dom;
            Set<Object> forbiddenSet = forbidden.get(undeterminedVar);
            var builder = discreteDom.toBuilder();
            for (Object value : discreteDom.toList()) {
                if (forbiddenSet.contains(value)) {
                    builder.delete(value);
                }
            }
            return Optional.of(Map.of(undeterminedVar, builder.build()));
        }

        return Optional.of(Map.of());
    }

    /**
     * All falsified means every cited variable's current domain is entirely within its own
     * forbidden set — the clause itself is already the (unconditionally sound) explanation, same
     * as {@link RangeNogoodConstraint#explainInfeasible}. No singleton requirement is needed here:
     * "this variable's whole current domain is forbidden" is exactly what falsified already
     * established, regardless of whether that domain happens to be a single point.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return Optional.of(this);
    }

    @Override
    public String getRelation() {
        return forbidden.entrySet().stream()
                .map(e -> e.getKey() + " not in " + e.getValue())
                .sorted()
                .collect(Collectors.joining(" OR ", "nogood(", ")"));
    }
}
