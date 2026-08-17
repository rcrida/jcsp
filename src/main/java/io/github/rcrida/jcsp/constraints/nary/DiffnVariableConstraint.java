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
 * The variable-sized sibling of {@link DiffnConstraint}: places axis-aligned rectangles so that
 * none overlap, where widths and heights are themselves decision variables (e.g. a rotation
 * choice between two fixed orientations) rather than fixed constants. Rectangle {@code i}
 * occupies {@code [x[i], x[i] + widths[i]) × [y[i], y[i] + heights[i])} for whatever values
 * {@code widths[i]}/{@code heights[i]} eventually take.
 * <p>
 * Shares {@link DiffnConstraint}'s pairwise compulsory-part propagation (via
 * {@link DiffnPropagation}), substituting each width/height's current domain <em>minimum</em> for
 * the fixed constant that class uses directly — sound because a rectangle's actual size can never
 * be smaller than its domain minimum, so the compulsory part computed from that minimum is never
 * an over-claim of guaranteed-occupied space. Unlike {@link DiffnConstraint}, {@link #propagate}
 * never narrows the width/height variables themselves, only the origins — narrowing sizes from
 * separation reasoning would need different (currently unimplemented) math.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class DiffnVariableConstraint extends NaryConstraint implements Propagatable {
    @Singular("xOrigin") private final List<Variable<? extends Number>> xOrigins;
    @Singular("yOrigin") private final List<Variable<? extends Number>> yOrigins;
    @Singular("widthVar")  private final List<Variable<? extends Number>> widths;
    @Singular("heightVar") private final List<Variable<? extends Number>> heights;

    public static DiffnVariableConstraint of(@NonNull List<Variable<? extends Number>> xs, @NonNull List<Variable<? extends Number>> ys,
            @NonNull List<Variable<? extends Number>> widths, @NonNull List<Variable<? extends Number>> heights) {
        assert xs.size() == ys.size() && ys.size() == widths.size() && widths.size() == heights.size()
                : "xs, ys, widths and heights must have equal length";
        var builder = builder();
        for (var v : xs) builder.variable(v).xOrigin(v);
        for (var v : ys) builder.variable(v).yOrigin(v);
        for (var v : widths) builder.variable(v).widthVar(v);
        for (var v : heights) builder.variable(v).heightVar(v);
        return builder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        int n = xOrigins.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        double[] ws = new double[n];
        double[] hs = new double[n];
        for (int i = 0; i < n; i++) {
            var xv = assignment.getValue((Variable<Number>) xOrigins.get(i));
            var yv = assignment.getValue((Variable<Number>) yOrigins.get(i));
            var wv = assignment.getValue((Variable<Number>) widths.get(i));
            var hv = assignment.getValue((Variable<Number>) heights.get(i));
            if (xv.isEmpty() || yv.isEmpty() || wv.isEmpty() || hv.isEmpty()) return true; // optimistic
            xs[i] = xv.get().doubleValue();
            ys[i] = yv.get().doubleValue();
            ws[i] = wv.get().doubleValue();
            hs[i] = hv.get().doubleValue();
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean separated = xs[i] + ws[i] <= xs[j]
                        || xs[j] + ws[j] <= xs[i]
                        || ys[i] + hs[i] <= ys[j]
                        || ys[j] + hs[j] <= ys[i];
                if (!separated) return false;
            }
        }
        return true;
    }

    private DiffnPropagation.SizeLookup widthLookup() {
        return DiffnPropagation.variable(widths);
    }

    private DiffnPropagation.SizeLookup heightLookup() {
        return DiffnPropagation.variable(heights);
    }

    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return propagate(domains, null);
    }

    /**
     * As {@link DiffnConstraint#propagate(Map, Set)}: skips a pair's {@link
     * DiffnPropagation#separateOnOverlap} call entirely when neither rectangle's own variables
     * (both origins and, unlike {@link DiffnConstraint}, both size variables too) are in {@code
     * changedSinceLastRun}. See {@link DiffnPropagation#dirtyRectangles}'s own Javadoc for the
     * soundness argument.
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
                if (DiffnPropagation.separateOnOverlap(i, j, xOrigins, widthLookup(), yOrigins, heightLookup(), domains, updated).isPresent()) {
                    return Optional.empty();
                }
                if (DiffnPropagation.separateOnOverlap(i, j, yOrigins, heightLookup(), xOrigins, widthLookup(), domains, updated).isPresent()) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(updated);
    }

    /**
     * As {@link DiffnConstraint#explainInfeasible}, but the citation set can also include the
     * width/height variables that determined a pair's compulsory part or separation feasibility —
     * see {@link DiffnPropagation}'s own Javadoc for why omitting them would be unsound here (not
     * merely imprecise, unlike the fixed-size case).
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
        return "diffn(rects=" + xOrigins.size() + ", variable-sized)";
    }
}
