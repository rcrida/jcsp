package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.SetBoundedDomain;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
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
 * A {@link NogoodConstraint} forbidding a whole set-interval region per variable: the clause
 * {@code OR(x1 ∉ R1, x2 ∉ R2, ..., xk ∉ Rk)} over {@code forbidden} — violated only when every one
 * of its variables' current {@link SetBoundedDomain} lies entirely within its own forbidden
 * region. The set-CP analogue of {@link RangeNogoodConstraint}: generalises {@link
 * GroundNogoodConstraint} (one forbidden ground {@code Set<E>} value per variable) to a whole
 * sub-lattice per variable, the same way {@link RangeNogoodConstraint} generalises it for numeric
 * domains.
 * <p>
 * Reuses {@link SetIntervalDomain} itself as the forbidden-region representation — already a
 * fully-formed "set interval" with bound/cardinality accessors and its own {@code contains} — the
 * same choice {@link RangeNogoodConstraint} makes for {@link io.github.rcrida.jcsp.domains.IntervalDomain}
 * rather than introducing a parallel range type.
 * <p>
 * Unlike {@link RangeNogoodConstraint}'s numeric case, where a {@link
 * io.github.rcrida.jcsp.domains.BoundedDomain} genuinely denotes every real number in
 * {@code [min, max]}, a {@link SetBoundedDomain}'s bound pair plus cardinality range only
 * <em>describes</em> its value set rather than enumerating it, so "is this domain entirely inside
 * (or entirely outside) the forbidden region" can only be answered with sound, not always
 * maximally tight, sufficient conditions — see {@link #classify}. A domain that's genuinely
 * disjoint from or contained in the forbidden region but doesn't happen to satisfy one of those
 * conditions is left {@link Literal#UNDETERMINED} rather than misclassified, the same principle
 * {@link RangeNogoodConstraint#pruneRange} already uses for an interior "hole" it can't represent.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SetBoundsNogoodConstraint extends NaryConstraint implements NogoodConstraint {
    @NonNull private final Map<Variable<?>, SetIntervalDomain<?>> forbidden;

    public static SetBoundsNogoodConstraint of(@NonNull Map<Variable<?>, SetIntervalDomain<?>> forbidden) {
        // An empty forbidden map is the empty clause -- see GroundNogoodConstraint.of for why
        // callers must not record one.
        assert !forbidden.isEmpty() : "SetBoundsNogoodConstraint requires at least one forbidden region";
        return SetBoundsNogoodConstraint.builder()
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
     * Builds a nogood citing every one of {@code variables}' current {@link SetBoundedDomain}
     * bound/cardinality state verbatim (a degenerate singleton region for an already-resolved
     * domain). Sound whenever the constraint these variables jointly belong to has just reported
     * infeasibility via {@link Propagatable#propagate}, same reasoning as {@link
     * RangeNogoodConstraint#fromCurrentBounds}: that return value alone already means no
     * combination drawn from exactly these current domains satisfies it. Returns {@link
     * Optional#empty()} if any cited variable's domain isn't a {@link SetBoundedDomain}, or is one
     * but already empty — citing it would violate {@link SetIntervalDomain#of}'s own consistency
     * asserts (e.g. {@code minCardinality <= maxCardinality}), which an empty domain's bound/
     * cardinality state doesn't satisfy by definition.
     */
    public static Optional<NogoodConstraint> fromCurrentBounds(
            @NonNull Collection<? extends Variable<?>> variables, @NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, SetIntervalDomain<?>> forbidden = new HashMap<>();
        for (Variable<?> variable : variables) {
            if (!(domains.get(variable) instanceof SetBoundedDomain<?> bounded) || bounded.isEmpty()) {
                return Optional.empty();
            }
            forbidden.put(variable, toRegion(bounded));
        }
        return Optional.of(of(forbidden));
    }

    private static <E> SetIntervalDomain<E> toRegion(SetBoundedDomain<E> domain) {
        return SetIntervalDomain.of(domain.getLowerBound(), domain.getUpperBound(),
                domain.getMinCardinality(), domain.getMaxCardinality(), domain.getComparator());
    }

    /**
     * Two-tier explanation shared by every set constraint whose own {@code explainInfeasible}
     * only ever needs this shape ({@link io.github.rcrida.jcsp.constraints.binary.SubsetConstraint},
     * {@link io.github.rcrida.jcsp.constraints.binary.DisjointConstraint}, {@link
     * io.github.rcrida.jcsp.constraints.binary.IntersectionCardinalityConstraint}): (1) a ground
     * reason via {@link GroundNogoodConstraint}, sound only when every one of {@code variables} is
     * already singleton; (2) failing that, {@link #fromCurrentBounds} citing their current
     * bound/cardinality state verbatim — tighter than falling all the way back to the full
     * assignment (see {@link Propagatable#explainInfeasible}'s own Javadoc) since it still
     * excludes every other unrelated variable in the search.
     */
    public static Optional<NogoodConstraint> explainViaGroundOrBounds(
            @NonNull Collection<? extends Variable<?>> variables, @NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Object> reason = Propagatable.allSingletonReason(variables, domains);
        Optional<NogoodConstraint> ground = GroundNogoodConstraint.fromReason(reason);
        return ground.isPresent() ? ground : fromCurrentBounds(variables, domains);
    }

    private enum Literal { SATISFIED, FALSIFIED, UNDETERMINED }

    /**
     * Classifies a single literal ({@code variable ∉ region}) against the variable's current
     * {@link SetBoundedDomain}: <b>satisfied</b> when an element already forced into one side's
     * lower bound is absent from the other side's upper bound (either direction — either alone
     * proves no shared value is possible), or the two cardinality ranges don't overlap at all;
     * <b>falsified</b> when the region's lower bound is already a subset of the domain's lower
     * bound, the domain's upper bound is already a subset of the region's upper bound, and the
     * domain's cardinality range nests inside the region's — every value the domain could still
     * take already satisfies the region on both axes; <b>undetermined</b> otherwise.
     */
    private static Literal classify(SetBoundedDomain<?> domain, SetIntervalDomain<?> region) {
        if (domain.isEmpty()) return Literal.SATISFIED;
        if (!region.upperBound().containsAll(domain.getLowerBound())) return Literal.SATISFIED;
        if (!domain.getUpperBound().containsAll(region.lowerBound())) return Literal.SATISFIED;
        if (domain.getMaxCardinality() < region.minCardinality() || region.maxCardinality() < domain.getMinCardinality()) {
            return Literal.SATISFIED;
        }
        boolean boundsNested = domain.getLowerBound().containsAll(region.lowerBound())
                && region.upperBound().containsAll(domain.getUpperBound());
        boolean cardinalityNested = region.minCardinality() <= domain.getMinCardinality()
                && domain.getMaxCardinality() <= region.maxCardinality();
        return (boundsNested && cardinalityNested) ? Literal.FALSIFIED : Literal.UNDETERMINED;
    }

    /**
     * Narrows {@code domain}'s cardinality range to exclude {@code region}'s, given the pair is
     * already known {@link Literal#UNDETERMINED}. Only representable when the bound pairs are
     * already nested (the only remaining way for {@code domain} to escape {@code region} is
     * cardinality) <em>and</em> {@code region}'s cardinality range touches one edge of {@code
     * domain}'s rather than sitting strictly inside it — an interior overlap would leave a
     * cardinality "hole" that a single contiguous range can't represent, the same limitation
     * {@link RangeNogoodConstraint#pruneRange} has for a numeric domain overlap interior to it.
     * Package-visible (rather than {@code private}) so a fully-nested cardinality pair — which
     * {@link #classify} would already report {@link Literal#FALSIFIED} for, so {@link #propagate}
     * never actually calls this method with one — can still be exercised directly by a test.
     */
    static Optional<Domain<?>> pruneCardinality(SetBoundedDomain<?> domain, SetIntervalDomain<?> region) {
        boolean boundsNested = domain.getLowerBound().containsAll(region.lowerBound())
                && region.upperBound().containsAll(domain.getUpperBound());
        if (!boundsNested) return Optional.empty();

        int domainMin = domain.getMinCardinality();
        int domainMax = domain.getMaxCardinality();
        int regionMin = region.minCardinality();
        int regionMax = region.maxCardinality();

        if (regionMin <= domainMin && regionMax < domainMax) {
            return Optional.of(domain.withCardinality(regionMax + 1, domainMax));
        }
        if (regionMin > domainMin && regionMax >= domainMax) {
            return Optional.of(domain.withCardinality(domainMin, regionMin - 1));
        }
        return Optional.empty();
    }

    /**
     * Generalised unit propagation over {@code OR(x1 ∉ R1, ..., xk ∉ Rk)}, following the same
     * shape as {@link RangeNogoodConstraint#propagate}: all falsified → infeasible; exactly one
     * undetermined (every other literal falsified) → narrow that one variable's cardinality range
     * via {@link #pruneCardinality} to exclude its forbidden region where representable.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Variable<?> undeterminedVar = null;
        SetIntervalDomain<?> undeterminedRegion = null;
        int undeterminedCount = 0;

        for (var entry : forbidden.entrySet()) {
            Variable<?> var = entry.getKey();
            SetIntervalDomain<?> region = entry.getValue();
            SetBoundedDomain<?> dom = (SetBoundedDomain<?>) domains.get(var);
            Literal literal = classify(dom, region);
            if (literal == Literal.SATISFIED) {
                return Optional.of(Map.of()); // this literal is guaranteed true: clause permanently satisfied
            }
            if (literal == Literal.UNDETERMINED) {
                undeterminedCount++;
                undeterminedVar = var;
                undeterminedRegion = region;
            }
        }

        if (undeterminedCount == 0) return Optional.empty(); // every literal falsified

        if (undeterminedCount == 1) {
            SetBoundedDomain<?> dom = (SetBoundedDomain<?>) domains.get(undeterminedVar);
            Optional<Domain<?>> narrowed = pruneCardinality(dom, undeterminedRegion);
            return narrowed.isPresent() ? Optional.of(Map.of(undeterminedVar, narrowed.get())) : Optional.of(Map.of());
        }

        return Optional.of(Map.of());
    }

    /**
     * All falsified means every cited variable's current domain is already sufficiently nested
     * inside its own forbidden region — the clause itself is already the (unconditionally sound)
     * explanation, same as {@link RangeNogoodConstraint#explainInfeasible}.
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
