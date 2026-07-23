package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link NogoodConstraint} forbidding a whole numeric range per variable: the clause
 * {@code OR(x1 ∉ R1, x2 ∉ R2, ..., xk ∉ Rk)} over {@code forbidden} — violated only when every
 * one of its variables currently holds a value inside its own forbidden {@link IntervalDomain}.
 * Generalises {@link GroundNogoodConstraint} (which forbids exactly one value per variable) to a
 * whole sub-domain per variable, letting a single learned nogood prune an entire forbidden
 * interval in one unit-propagation step instead of one value at a time.
 * <p>
 * Reuses {@link IntervalDomain} itself as the forbidden-region representation — it is already a
 * fully-formed closed range with {@code contains}/{@code getMin}/{@code getMax} — rather than
 * introducing a parallel range type. This targets {@link BoundedDomain} (continuous) and numeric
 * {@link DiscreteDomain} variables specifically; a non-numeric variable cited here degrades
 * gracefully (its literal is simply never satisfiable-via-narrowing, since
 * {@link IntervalDomain#contains} always returns {@code false} for non-{@link Number} values) but
 * is not rejected — same trust-the-caller contract {@link GroundNogoodConstraint} already has for
 * value/variable-type matching.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RangeNogoodConstraint extends NaryConstraint implements NogoodConstraint {
    @NonNull private final Map<Variable<?>, IntervalDomain> forbidden;

    public static RangeNogoodConstraint of(@NonNull Map<Variable<?>, IntervalDomain> forbidden) {
        // An empty forbidden map is the empty clause -- see GroundNogoodConstraint.of for why
        // callers must not record one.
        assert !forbidden.isEmpty() : "RangeNogoodConstraint requires at least one forbidden range";
        return RangeNogoodConstraint.builder()
                .variables(forbidden.keySet())
                .forbidden(Map.copyOf(forbidden))
                .build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (var entry : forbidden.entrySet()) {
            var value = assignment.getValue(entry.getKey());
            if (value.isEmpty() || !entry.getValue().contains(value.get())) return true;
        }
        return false;
    }

    /**
     * Builds a nogood citing every one of {@code variables}' current domain bounds (a degenerate
     * point range for an already-singleton domain). Sound whenever the constraint these variables
     * jointly belong to has just reported infeasibility via
     * {@link io.github.rcrida.jcsp.consistency.Propagatable#propagate}: that return value already
     * means no combination drawn from these exact current domains satisfies it, which is exactly
     * this class's falsified condition — no propagator-specific reasoning is needed beyond that.
     * Unlike a propagator's own {@code explainInfeasible}, this never requires any variable to be
     * singleton, so it can produce a real (if not always minimal) explanation in cases that would
     * otherwise fall through to the full-assignment fallback.
     * <p>
     * Returns {@link Optional#empty()} if any cited variable's domain can't be soundly stood in for
     * by its bounding {@link IntervalDomain} — a {@link BoundedDomain} always qualifies (min/max
     * already describe it exactly, with no possible gap), but a {@link DiscreteDomain} only
     * qualifies when it is <i>gapless</i> (every integer between its min and max is actually
     * present). A gapped discrete domain — e.g. {@code {1,5}} — must not be cited this way: its
     * bounding interval {@code [1,5]} is a strict superset, so a later, unrelated domain such as
     * {@code {2,3,4}} would satisfy "contained in {@code [1,5]}" without ever being a subset of the
     * domain that actually caused the original infeasibility, silently generalising the nogood past
     * what was proven and over-pruning the search (caught via a real regression: this exact gap
     * made {@code Prob054NQueensTest} drop from 92 solutions to 80 once {@link AllDiffConstraint} started
     * citing Hall-violating subsets — which are gapped almost by definition — through this method).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<NogoodConstraint> fromCurrentBounds(
            @NonNull Collection<? extends Variable<?>> variables, @NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, IntervalDomain> forbidden = new HashMap<>();
        for (Variable<?> variable : variables) {
            Domain<?> domain = domains.get(variable);
            if (!isSafeToCiteAsRange(domain)) return Optional.empty();
            Domain rawDomain = domain;
            forbidden.put(variable, IntervalDomain.of(NumericBounds.min(rawDomain), NumericBounds.max(rawDomain)));
        }
        return Optional.of(of(forbidden));
    }

    /**
     * Whether {@code domain} can be soundly replaced by its bounding interval: always true for a
     * {@link BoundedDomain} (continuous, so min/max already is the whole domain); for a
     * {@link DiscreteDomain}, only when every value is an {@link Integer} and the domain is
     * gapless — its size equals {@code max - min + 1} — so the interval and the domain denote
     * exactly the same set of values. {@code false} for any other {@link Domain} kind (e.g.
     * {@link io.github.rcrida.jcsp.domains.SetBoundedDomain}, which is neither {@link BoundedDomain}
     * nor {@link DiscreteDomain} — it isn't {@code Number}-based, so no {@link IntervalDomain} could
     * stand in for it at all).
     */
    private static boolean isSafeToCiteAsRange(Domain<?> domain) {
        if (domain instanceof BoundedDomain<?>) return true;
        if (!(domain instanceof DiscreteDomain<?> discrete)) return false;
        var values = discrete.toList();
        if (values.isEmpty() || !(values.get(0) instanceof Integer)) return false;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Object value : values) {
            int v = (Integer) value;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return discrete.size() == (max - min + 1);
    }

    private enum Literal { SATISFIED, FALSIFIED, UNDETERMINED }

    /**
     * Classifies a single literal ({@code variable ∉ range}) against the variable's current
     * domain: <b>satisfied</b> when the domain no longer overlaps {@code range} at all (the
     * literal is guaranteed true regardless of which value the variable eventually takes);
     * <b>falsified</b> when the domain is entirely contained within {@code range} (every
     * remaining value is forbidden); <b>undetermined</b> otherwise. For a {@link DiscreteDomain}
     * this is decided by exact enumeration (sound even when the domain has gaps within its own
     * min/max); for a {@link BoundedDomain} it is decided by bounds comparison alone, since
     * individual values can't be enumerated — exact for a genuinely continuous domain.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Literal classify(Domain<?> domain, IntervalDomain range) {
        if (domain instanceof DiscreteDomain<?> discrete) {
            boolean anyInside = false;
            boolean anyOutside = false;
            for (Object value : discrete.toList()) {
                if (range.contains(value)) anyInside = true; else anyOutside = true;
                if (anyInside && anyOutside) return Literal.UNDETERMINED;
            }
            return anyInside ? Literal.FALSIFIED : Literal.SATISFIED;
        }
        Domain rawDomain = domain;
        double domainMin = NumericBounds.min(rawDomain);
        double domainMax = NumericBounds.max(rawDomain);
        if (domainMax < range.min() || domainMin > range.max()) return Literal.SATISFIED;
        if (domainMin >= range.min() && domainMax <= range.max()) return Literal.FALSIFIED;
        return Literal.UNDETERMINED;
    }

    /**
     * Narrows {@code domain} to exclude {@code range}, given the pair is already known
     * {@link Literal#UNDETERMINED} (a genuine, non-total overlap). For a {@link DiscreteDomain},
     * every individual forbidden value is deleted — precise regardless of where the overlap
     * falls. For a {@link BoundedDomain}, only an overlap touching one edge of {@code domain} can
     * be represented as a single narrowed interval; an overlap strictly interior to {@code domain}
     * (a "hole") can't be expressed without splitting into two disjoint ranges, which
     * {@link BoundedDomain} doesn't support, so that case is left untouched — the falsified check
     * still catches it once {@code domain} itself narrows enough from other propagation.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<Domain<?>> pruneRange(Domain<?> domain, IntervalDomain range) {
        if (domain instanceof DiscreteDomain<?> discreteDomain) {
            DiscreteDomain<Object> discrete = (DiscreteDomain<Object>) discreteDomain;
            DiscreteDomain.Builder<Object> builder = discrete.toBuilder();
            for (Object value : discrete.toList()) {
                if (range.contains(value)) builder.delete(value);
            }
            return Optional.of(builder.build());
        }
        // Every Domain implementation is either DiscreteDomain or BoundedDomain (IntervalDomain is
        // the sole BoundedDomain implementor) -- having excluded DiscreteDomain above, this must be
        // one; cast directly instead of a second instanceof check, which JaCoCo would otherwise
        // require a structurally-unreachable "neither" test to cover.
        BoundedDomain<?> bounded = (BoundedDomain<?>) domain;
        double domainMin = bounded.getMin().doubleValue();
        double domainMax = bounded.getMax().doubleValue();
        Domain rawDomain = domain;
        if (range.min() <= domainMin) {
            return NumericBounds.narrow(rawDomain, range.max(), domainMax);
        }
        if (range.max() >= domainMax) {
            return NumericBounds.narrow(rawDomain, domainMin, range.min());
        }
        // range is strictly interior to domain: not representable as a single interval.
        return Optional.empty();
    }

    /**
     * Generalised unit propagation over {@code OR(x1 ∉ R1, ..., xk ∉ Rk)}, following the same
     * shape as {@link GroundNogoodConstraint#propagate}: all falsified → infeasible; exactly one
     * undetermined (every other literal falsified) → narrow that one variable's domain via
     * {@link #pruneRange} to exclude its forbidden range.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Variable<?> undeterminedVar = null;
        IntervalDomain undeterminedRange = null;
        int undeterminedCount = 0;

        for (var entry : forbidden.entrySet()) {
            Variable<?> var = entry.getKey();
            IntervalDomain range = entry.getValue();
            Literal literal = classify(domains.get(var), range);
            if (literal == Literal.SATISFIED) {
                return Optional.of(Map.of()); // this literal is guaranteed true: clause permanently satisfied
            }
            if (literal == Literal.UNDETERMINED) {
                undeterminedCount++;
                undeterminedVar = var;
                undeterminedRange = range;
            }
        }

        if (undeterminedCount == 0) return Optional.empty(); // every literal falsified

        if (undeterminedCount == 1) {
            Optional<Domain<?>> narrowed = pruneRange(domains.get(undeterminedVar), undeterminedRange);
            return narrowed.isPresent() ? Optional.of(Map.of(undeterminedVar, narrowed.get())) : Optional.of(Map.of());
        }

        return Optional.of(Map.of());
    }

    /**
     * All falsified means every cited variable's current domain is entirely within its own
     * forbidden range — the clause itself is already the (unconditionally sound) explanation, same
     * as {@link GroundNogoodConstraint#explainInfeasible}. No singleton requirement is needed here,
     * unlike citing individual ground values would require: "this variable's whole current domain
     * is forbidden" is exactly what falsified already established, regardless of whether that
     * domain happens to be a single point.
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
