package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An n-ary constraint that places axis-aligned rectangles so that none overlap. Rectangle
 * {@code i} occupies {@code [x[i], x[i] + widths[i]) × [y[i], y[i] + heights[i])}; every pair
 * of rectangles must be pairwise non-overlapping (they may touch at edges or corners).
 * <p>
 * Origin variables may be backed by either {@link io.github.rcrida.jcsp.domains.IntRangeDomain}
 * (integer placement) or {@link io.github.rcrida.jcsp.domains.IntervalDomain} (continuous
 * placement). Widths and heights are stored as {@code double} in both cases.
 * <p>
 * Beyond the basic satisfiability check, {@link #propagate} implements pairwise compulsory-part
 * reasoning: when two rectangles' compulsory parts overlap on one axis they must be separated on
 * the other, so the unforced axis domains are tightened accordingly. The pairwise math itself
 * lives in {@link DiffnPropagation}, shared with {@link DiffnVariableConstraint} (the sibling for
 * when widths/heights are themselves decision variables rather than fixed constants).
 * <p>
 * Equivalent to MiniZinc's {@code diffn(x, y, dx, dy)} constraint.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DiffnConstraint extends NaryConstraint implements Propagatable {
    @Singular("xOrigin") private final List<Variable<? extends Number>> xOrigins;
    @Singular("yOrigin") private final List<Variable<? extends Number>> yOrigins;
    @Singular("width")   private final List<Double> widths;
    @Singular("height")  private final List<Double> heights;

    public static DiffnConstraint of(@NonNull List<Variable<? extends Number>> xs, @NonNull List<Variable<? extends Number>> ys,
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
            var xv = assignment.getValue((Variable<Number>) xOrigins.get(i));
            var yv = assignment.getValue((Variable<Number>) yOrigins.get(i));
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

    private DiffnPropagation.SizeLookup widthLookup() {
        return DiffnPropagation.fixed(widths);
    }

    private DiffnPropagation.SizeLookup heightLookup() {
        return DiffnPropagation.fixed(heights);
    }

    /**
     * Pairwise compulsory-part propagator. For every pair of rectangles, if their compulsory parts
     * are forced to overlap on one axis, they cannot overlap on the other, so the perpendicular
     * origin domains are tightened to the still-feasible separation. Delegates to {@link
     * #propagate(Map, Set)} with a {@code null} hint (full scan, no pair skipped).
     *
     * @param domains current variable domains
     * @return updated domains for origin variables whose bounds were tightened,
     *         or {@link Optional#empty()} if the constraint is infeasible
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(
            @NonNull Map<Variable<?>, Domain<?>> domains) {
        return propagate(domains, null);
    }

    /**
     * Skips a pair's {@link DiffnPropagation#separateOnOverlap} call entirely when neither
     * rectangle's own variables (both origins; widths/heights are fixed constants here, never in
     * {@code changedSinceLastRun}) are in {@code changedSinceLastRun} -- see {@link
     * DiffnPropagation#dirtyRectangles}'s own Javadoc for why that's sound, not an approximation.
     * A {@code null} hint (unknown -- e.g. the first round of a fixpoint call) checks every pair,
     * matching {@link #propagate(Map)}'s own unconditional scan.
     */
    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(
            @NonNull Map<Variable<?>, Domain<?>> domains, @Nullable Set<Variable<?>> changedSinceLastRun) {
        int n = xOrigins.size();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        boolean[] dirty = changedSinceLastRun == null ? null
                : DiffnPropagation.dirtyRectangles(n, xOrigins, widthLookup(), yOrigins, heightLookup(), changedSinceLastRun);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (dirty != null && !dirty[i] && !dirty[j]) continue;
                // Mandatory x-overlap forces separation on y.
                if (DiffnPropagation.separateOnOverlap(i, j, xOrigins, widthLookup(), yOrigins, heightLookup(), domains, updated).isPresent()) {
                    return Optional.empty();
                }
                // Mandatory y-overlap forces separation on x.
                if (DiffnPropagation.separateOnOverlap(i, j, yOrigins, heightLookup(), xOrigins, widthLookup(), domains, updated).isPresent()) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(updated);
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
                var failure = DiffnPropagation.separateOnOverlap(i, j, xOrigins, widthLookup(), yOrigins, heightLookup(), domains, updated);
                if (failure.isPresent()) return GroundNogoodConstraint.fromReason(DiffnPropagation.buildReason(failure.get(), domains, updated));
                failure = DiffnPropagation.separateOnOverlap(i, j, yOrigins, heightLookup(), xOrigins, widthLookup(), domains, updated);
                if (failure.isPresent()) return GroundNogoodConstraint.fromReason(DiffnPropagation.buildReason(failure.get(), domains, updated));
            }
        }
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "diffn(rects=" + xOrigins.size() + ")";
    }
}
