package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shared pairwise compulsory-part separation logic for {@link DiffnConstraint} (fixed
 * widths/heights) and {@link DiffnVariableConstraint} (variable widths/heights): for a pair of
 * rectangles whose compulsory parts overlap on the primary axis, tightens (or proves infeasible)
 * their separation on the secondary axis. Parameterized by a {@link SizeLookup} rather than a
 * plain {@code List<Double>} so both callers share the exact same math -- a fixed size is a
 * constant, a variable size is its current domain minimum (the guaranteed-occupied bound, sound
 * because a rectangle's actual size can never be smaller than its domain minimum), and every use
 * site downstream is unaffected by which one it is. {@link SizeLookup#variable(int)} additionally
 * names the {@link Variable} (if any) a size came from, purely so {@link #buildReason} can cite it
 * alongside the four origin variables: for {@link DiffnVariableConstraint}, an infeasibility can
 * depend on a width/height variable's own bound just as much as on the origins, so omitting it
 * from the nogood would be unsound, not merely imprecise (a fixed size has no such variable, so
 * {@link DiffnConstraint}'s citations are exactly the four origins, unchanged from before this
 * class existed).
 */
@Slf4j
final class DiffnPropagation {
    private DiffnPropagation() {
    }

    interface SizeLookup {
        double bound(int index, Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated);

        @Nullable
        Variable<?> variable(int index);
    }

    static SizeLookup fixed(List<Double> sizes) {
        return new SizeLookup() {
            @Override
            public double bound(int index, Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
                return sizes.get(index);
            }

            @Override
            public @Nullable Variable<?> variable(int index) {
                return null;
            }
        };
    }

    static SizeLookup variable(List<Variable<? extends Number>> sizeVariables) {
        return new SizeLookup() {
            @Override
            public double bound(int index, Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
                return boundMin(sizeVariables.get(index), domains, updated);
            }

            @Override
            public Variable<?> variable(int index) {
                return sizeVariables.get(index);
            }
        };
    }

    record Failure(List<Variable<?>> culprits) {
    }

    /**
     * If rectangles {@code i} and {@code j} have overlapping compulsory parts on the primary axis,
     * tighten their origin domains on the secondary axis so they remain separated there.
     *
     * @return the variables responsible when no separation is possible on the secondary axis
     *         (infeasible), or {@link Optional#empty()} when {@code i} and {@code j} are already
     *         separated or have been narrowed to remain separable
     */
    static Optional<Failure> separateOnOverlap(int i, int j,
            List<Variable<? extends Number>> pOrigins, SizeLookup pSizes,
            List<Variable<? extends Number>> sOrigins, SizeLookup sSizes,
            Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        double piMin = boundMin(pOrigins.get(i), domains, updated);
        double piMax = boundMax(pOrigins.get(i), domains, updated);
        double pjMin = boundMin(pOrigins.get(j), domains, updated);
        double pjMax = boundMax(pOrigins.get(j), domains, updated);
        double wi = pSizes.bound(i, domains, updated);
        double wj = pSizes.bound(j, domains, updated);

        // Compulsory part of i is [piMax, piMin+wi); of j is [pjMax, pjMin+wj). They overlap iff
        // both are non-empty and the intervals intersect.
        boolean mandatoryOverlap = piMax < piMin + wi
                && pjMax < pjMin + wj
                && piMax < pjMin + wj
                && pjMax < piMin + wi;
        if (!mandatoryOverlap) return Optional.empty();

        double siMin = boundMin(sOrigins.get(i), domains, updated);
        double siMax = boundMax(sOrigins.get(i), domains, updated);
        double sjMin = boundMin(sOrigins.get(j), domains, updated);
        double sjMax = boundMax(sOrigins.get(j), domains, updated);
        double hi = sSizes.bound(i, domains, updated);
        double hj = sSizes.bound(j, domains, updated);

        boolean caseA = siMin + hi <= sjMax; // i precedes j on the secondary axis
        boolean caseB = sjMin + hj <= siMax; // j precedes i on the secondary axis
        if (!caseA && !caseB) {
            List<Variable<?>> culprits = Stream.of(
                            pOrigins.get(i), pOrigins.get(j), sOrigins.get(i), sOrigins.get(j),
                            pSizes.variable(i), pSizes.variable(j), sSizes.variable(i), sSizes.variable(j))
                    .filter(Objects::nonNull)
                    .toList();
            return Optional.of(new Failure(culprits));
        }

        if (!caseA) {
            // Only B possible: s[i] >= s[j] + hj.
            applyBound(sOrigins.get(i), Math.max(siMin, sjMin + hj), siMax, domains, updated);
            applyBound(sOrigins.get(j), sjMin, Math.min(sjMax, siMax - hj), domains, updated);
        }
        if (!caseB) {
            // Only A possible: s[i] + hi <= s[j].
            applyBound(sOrigins.get(i), siMin, Math.min(siMax, sjMax - hi), domains, updated);
            applyBound(sOrigins.get(j), Math.max(sjMin, siMin + hi), sjMax, domains, updated);
        }
        return Optional.empty();
    }

    static Map<Variable<?>, Object> buildReason(Failure failure,
            Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        Map<Variable<?>, Domain<?>> current = new HashMap<>(domains);
        current.putAll(updated);
        return Propagatable.allSingletonReason(failure.culprits(), current);
    }

    @SuppressWarnings("unchecked")
    static double boundMin(Variable<?> var, Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        return NumericBounds.min((Domain<Number>) updated.getOrDefault(var, domains.get(var)));
    }

    @SuppressWarnings("unchecked")
    static double boundMax(Variable<?> var, Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        return NumericBounds.max((Domain<Number>) updated.getOrDefault(var, domains.get(var)));
    }

    @SuppressWarnings("unchecked")
    static void applyBound(Variable<?> var, double newMin, double newMax,
            Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        Domain<?> current = updated.getOrDefault(var, domains.get(var));
        double curMin = NumericBounds.min((Domain<Number>) current);
        double curMax = NumericBounds.max((Domain<Number>) current);
        double lo = Math.max(curMin, newMin);
        double hi = Math.min(curMax, newMax);
        if (lo == curMin && hi == curMax) return; // no tightening
        log.debug("diffn tightening {} from [{}, {}] to [{}, {}]", var, curMin, curMax, lo, hi);
        updated.put(var, current instanceof BoundedDomain<?>
                ? IntervalDomain.of(lo, hi)
                : IntRangeDomain.of((int) lo, (int) hi));
    }
}
