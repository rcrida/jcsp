package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.NumericBounds;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An n-ary constraint that places axis-aligned rectangles so that none overlap. Rectangle
 * {@code i} occupies {@code [x[i], x[i] + widths[i]) × [y[i], y[i] + heights[i])}; every pair
 * of rectangles must be pairwise non-overlapping (they may touch at edges or corners).
 * <p>
 * Origin variables may be backed by either {@link IntRangeDomain} (integer placement) or
 * {@link IntervalDomain} (continuous placement). Widths and heights are stored as {@code double}
 * in both cases.
 * <p>
 * Beyond the basic satisfiability check, {@link #propagate} implements pairwise compulsory-part
 * reasoning: when two rectangles' compulsory parts overlap on one axis they must be separated on
 * the other, so the unforced axis domains are tightened accordingly.
 * <p>
 * Equivalent to MiniZinc's {@code diffn(x, y, dx, dy)} constraint.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DiffnConstraint extends NaryConstraint implements Propagatable {
    @Singular("xOrigin") private final List<Variable<?>> xOrigins;
    @Singular("yOrigin") private final List<Variable<?>> yOrigins;
    @Singular("width")   private final List<Double> widths;
    @Singular("height")  private final List<Double> heights;

    public static DiffnConstraint of(@NonNull List<Variable<?>> xs, @NonNull List<Variable<?>> ys,
            @NonNull List<Double> widths, @NonNull List<Double> heights) {
        assert xs.size() == ys.size() && ys.size() == widths.size() && widths.size() == heights.size()
                : "xs, ys, widths and heights must have equal length";
        var builder = builder();
        for (var v : xs) builder.variable(v).xOrigin(v);
        for (var v : ys) builder.variable(v).yOrigin(v);
        widths.forEach(builder::width);
        heights.forEach(builder::height);
        return builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        int n = xOrigins.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            var xv = assignment.getValue((Variable<Number>) (Variable<?>) xOrigins.get(i));
            var yv = assignment.getValue((Variable<Number>) (Variable<?>) yOrigins.get(i));
            if (xv.isEmpty() || yv.isEmpty()) return true; // optimistic for partial assignments
            xs[i] = xv.get().doubleValue();
            ys[i] = yv.get().doubleValue();
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean separated = xs[i] + widths.get(i) <= xs[j]
                        || xs[j] + widths.get(j) <= xs[i]
                        || ys[i] + heights.get(i) <= ys[j]
                        || ys[j] + heights.get(j) <= ys[i];
                if (!separated) return false;
            }
        }
        return true;
    }

    /**
     * Pairwise compulsory-part propagator. For every pair of rectangles, if their compulsory parts
     * are forced to overlap on one axis, they cannot overlap on the other, so the perpendicular
     * origin domains are tightened to the still-feasible separation.
     *
     * @param domains current variable domains
     * @return updated domains for origin variables whose bounds were tightened,
     *         or {@link Optional#empty()} if the constraint is infeasible
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(
            @NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = xOrigins.size();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // Mandatory x-overlap forces separation on y.
                if (separateOnOverlap(i, j, xOrigins, widths, yOrigins, heights, domains, updated).isPresent()) {
                    return Optional.empty();
                }
                // Mandatory y-overlap forces separation on x.
                if (separateOnOverlap(i, j, yOrigins, heights, xOrigins, widths, domains, updated).isPresent()) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(updated);
    }

    /**
     * The four origin variables whose current bounds jointly proved a pair of rectangles cannot be
     * separated on either axis: {@code p1}/{@code p2} on the primary axis (their mandatory-overlap
     * check) and {@code s1}/{@code s2} on the secondary axis (their no-separation check).
     */
    private record Failure(Variable<?> p1, Variable<?> p2, Variable<?> s1, Variable<?> s2) {}

    /**
     * If rectangles {@code i} and {@code j} have overlapping compulsory parts on the primary axis,
     * tighten their origin domains on the secondary axis so they remain separated there.
     *
     * @return the four variables responsible when no separation is possible on the secondary axis
     *         (infeasible), or {@link Optional#empty()} when {@code i} and {@code j} are already
     *         separated or have been narrowed to remain separable
     */
    private Optional<Failure> separateOnOverlap(int i, int j,
            List<Variable<?>> pOrigins, List<Double> pSizes,
            List<Variable<?>> sOrigins, List<Double> sSizes,
            Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        double piMin = boundMin(pOrigins.get(i), domains, updated);
        double piMax = boundMax(pOrigins.get(i), domains, updated);
        double pjMin = boundMin(pOrigins.get(j), domains, updated);
        double pjMax = boundMax(pOrigins.get(j), domains, updated);
        double wi = pSizes.get(i);
        double wj = pSizes.get(j);

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
        double hi = sSizes.get(i);
        double hj = sSizes.get(j);

        boolean caseA = siMin + hi <= sjMax; // i precedes j on the secondary axis
        boolean caseB = sjMin + hj <= siMax; // j precedes i on the secondary axis
        if (!caseA && !caseB) {
            return Optional.of(new Failure(pOrigins.get(i), pOrigins.get(j), sOrigins.get(i), sOrigins.get(j)));
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

    /**
     * On infeasibility, replays {@link #propagate}'s pairwise scan (threading the same narrowed
     * {@code current} domains across pairs, since separating an earlier pair can change a later
     * pair's compulsory-part bounds) until the same failing pair and axis order is found, then
     * attributes the conflict to the four responsible origin variables — the primary axis pair
     * whose compulsory parts were forced to overlap, and the secondary axis pair that could not be
     * separated. Unlike {@link io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint},
     * no single variable (or subset) here is independently sufficient: the mandatory-overlap and
     * no-separation checks are joint conditions over all four bounds, so a partial citation would
     * be unsound — a different configuration of the omitted variables could still separate the
     * pair. The reason is therefore only ever the fully collective set, via
     * {@link Propagatable#allSingletonReason}: non-empty solely when all four are singleton.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = xOrigins.size();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                var failure = separateOnOverlap(i, j, xOrigins, widths, yOrigins, heights, domains, updated);
                if (failure.isPresent()) return GroundNogoodConstraint.fromReason(buildReason(failure.get(), domains, updated));
                failure = separateOnOverlap(i, j, yOrigins, heights, xOrigins, widths, domains, updated);
                if (failure.isPresent()) return GroundNogoodConstraint.fromReason(buildReason(failure.get(), domains, updated));
            }
        }
        return Optional.empty();
    }

    private Map<Variable<?>, Object> buildReason(Failure failure,
            Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        Map<Variable<?>, Domain<?>> current = new HashMap<>(domains);
        current.putAll(updated);
        return Propagatable.allSingletonReason(
                List.of(failure.p1(), failure.p2(), failure.s1(), failure.s2()), current);
    }

    @SuppressWarnings("unchecked")
    private double boundMin(Variable<?> var,
            Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        return NumericBounds.min((Domain<Number>) updated.getOrDefault(var, domains.get(var)));
    }

    @SuppressWarnings("unchecked")
    private double boundMax(Variable<?> var,
            Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        return NumericBounds.max((Domain<Number>) updated.getOrDefault(var, domains.get(var)));
    }

    @SuppressWarnings("unchecked")
    private void applyBound(Variable<?> var, double newMin, double newMax,
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

    @Override
    public String getRelation() {
        return "diffn(rects=" + xOrigins.size() + ")";
    }
}
