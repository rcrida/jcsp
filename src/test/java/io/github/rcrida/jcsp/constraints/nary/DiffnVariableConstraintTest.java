package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DiffnVariableConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    private static Map<Variable<?>, Domain<?>> domains(Object... pairs) {
        var map = new java.util.HashMap<Variable<?>, Domain<?>>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Variable<?>) pairs[i], (Domain<?>) pairs[i + 1]);
        }
        return map;
    }

    // --- factory ---

    @Test
    void of_widthsHeightsSizeMismatch_asserts() {
        Variable<Integer> x = F.create("vx"), y = F.create("vy");
        Variable<Integer> w = F.create("vw"), h0 = F.create("vh0"), h1 = F.create("vh1");
        assertThatThrownBy(() -> DiffnVariableConstraint.of(
                List.of(x), List.of(y), List.of(w), List.of(h0, h1)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_xsYsSizeMismatch_asserts() {
        Variable<Integer> x0 = F.create("ax0"), x1 = F.create("ax1"), y = F.create("ay");
        Variable<Integer> w = F.create("aw"), h = F.create("ah");
        assertThatThrownBy(() -> DiffnVariableConstraint.of(
                List.of(x0, x1), List.of(y), List.of(w), List.of(h)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_ysWidthsSizeMismatch_asserts() {
        Variable<Integer> x0 = F.create("bx0"), x1 = F.create("bx1");
        Variable<Integer> y0 = F.create("by0"), y1 = F.create("by1");
        Variable<Integer> w = F.create("bw"), h0 = F.create("bh0"), h1 = F.create("bh1");
        assertThatThrownBy(() -> DiffnVariableConstraint.of(
                List.of(x0, x1), List.of(y0, y1), List.of(w), List.of(h0, h1)))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_separatedInX_iBeforeJ() {
        Variable<Integer> x0 = F.create("vx0"), y0 = F.create("vy0"), w0 = F.create("vw0"), h0 = F.create("vh0");
        Variable<Integer> x1 = F.create("vx1"), y1 = F.create("vy1"), w1 = F.create("vw1"), h1 = F.create("vh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        // rect0 [0,2)x[0,2), rect1 [3,5)x[0,2): separated in x
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, y0, 0, w0, 2, h0, 2, x1, 3, y1, 0, w1, 2, h1, 2)))).isTrue();
    }

    @Test
    void isSatisfiedBy_overlapping_false() {
        Variable<Integer> x0 = F.create("wx0"), y0 = F.create("wy0"), w0 = F.create("ww0"), h0 = F.create("wh0");
        Variable<Integer> x1 = F.create("wx1"), y1 = F.create("wy1"), w1 = F.create("ww1"), h1 = F.create("wh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        // rect0 [0,3)x[0,3), rect1 [1,4)x[1,4): overlap
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, y0, 0, w0, 3, h0, 3, x1, 1, y1, 1, w1, 3, h1, 3)))).isFalse();
    }

    @Test
    void isSatisfiedBy_xMissing_optimistic() {
        Variable<Integer> x0 = F.create("px0"), y0 = F.create("py0"), w0 = F.create("pw0"), h0 = F.create("ph0");
        Variable<Integer> x1 = F.create("px1"), y1 = F.create("py1"), w1 = F.create("pw1"), h1 = F.create("ph1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(y0, 0, w0, 2, h0, 2, x1, 3, y1, 0, w1, 2, h1, 2)))).isTrue();
    }

    @Test
    void isSatisfiedBy_yMissing_optimistic() {
        Variable<Integer> x0 = F.create("qx0"), y0 = F.create("qy0"), w0 = F.create("qw0"), h0 = F.create("qh0");
        Variable<Integer> x1 = F.create("qx1"), y1 = F.create("qy1"), w1 = F.create("qw1"), h1 = F.create("qh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, w0, 2, h0, 2, x1, 3, y1, 0, w1, 2, h1, 2)))).isTrue();
    }

    @Test
    void isSatisfiedBy_widthMissing_optimistic() {
        Variable<Integer> x0 = F.create("mx0"), y0 = F.create("my0"), w0 = F.create("mw0"), h0 = F.create("mh0");
        Variable<Integer> x1 = F.create("mx1"), y1 = F.create("my1"), w1 = F.create("mw1"), h1 = F.create("mh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, y0, 0, h0, 2, x1, 3, y1, 0, w1, 2, h1, 2)))).isTrue();
    }

    @Test
    void isSatisfiedBy_heightMissing_optimistic() {
        Variable<Integer> x0 = F.create("rx0"), y0 = F.create("ry0"), w0 = F.create("rw0"), h0 = F.create("rh0");
        Variable<Integer> x1 = F.create("rx1"), y1 = F.create("ry1"), w1 = F.create("rw1"), h1 = F.create("rh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, y0, 0, w0, 2, x1, 3, y1, 0, w1, 2, h1, 2)))).isTrue();
    }

    // --- propagate: rotation-choice scenario (matches the XCSP3 StripPacking use case) ---

    /**
     * Two rectangles, each choosing between a 4x2 or 2x4 orientation (width/height each have
     * domain {2,4}, mirroring an XCSP3 channelling extension constraint's r/w/h triple). x fixed
     * so both have mandatory x-overlap using width's guaranteed minimum (2): compulsory x-parts
     * [0,2) and [1,3) overlap regardless of which orientation is eventually chosen, forcing
     * y-separation using height's guaranteed minimum (2) too.
     */
    @Test
    void propagate_variableWidths_mandatoryXOverlapForcesYSeparation() {
        Variable<Integer> x0 = F.create("rx0"), y0 = F.create("ry0"), w0 = F.create("rw0"), h0 = F.create("rh0");
        Variable<Integer> x1 = F.create("rx1"), y1 = F.create("ry1"), w1 = F.create("rw1"), h1 = F.create("rh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(1, 1),
                w0, IntRangeDomain.of(2, 4), w1, IntRangeDomain.of(2, 4),
                y0, IntRangeDomain.of(2, 3), y1, IntRangeDomain.of(3, 4),
                h0, IntRangeDomain.of(2, 4), h1, IntRangeDomain.of(2, 4));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        // caseB (j precedes i, i.e. y1+h1<=y0) false since y1min+h1min=3+2=5>y0max=3; only caseA
        // (y0+h0<=y1) survives -> y0 max lowered to sjMax-hi = 4-2=2, y1 min raised to siMin+hi=2+2=4.
        assertThat(result.get().get(y0)).isEqualTo(IntRangeDomain.of(2, 2));
        assertThat(result.get().get(y1)).isEqualTo(IntRangeDomain.of(4, 4));
    }

    @Test
    void propagate_noMandatoryOverlap_noChange() {
        Variable<Integer> x0 = F.create("nx0"), y0 = F.create("ny0"), w0 = F.create("nw0"), h0 = F.create("nh0");
        Variable<Integer> x1 = F.create("nx1"), y1 = F.create("ny1"), w1 = F.create("nw1"), h1 = F.create("nh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 5), x1, IntRangeDomain.of(0, 5),
                w0, IntRangeDomain.of(2, 2), w1, IntRangeDomain.of(2, 2),
                y0, IntRangeDomain.of(0, 5), y1, IntRangeDomain.of(0, 5),
                h0, IntRangeDomain.of(2, 2), h1, IntRangeDomain.of(2, 2));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_bothCasesImpossible_infeasible() {
        // Identical fixed-position, fixed-size rectangles -> cannot separate on either axis.
        Variable<Integer> x0 = F.create("ix0"), y0 = F.create("iy0"), w0 = F.create("iw0"), h0 = F.create("ih0");
        Variable<Integer> x1 = F.create("ix1"), y1 = F.create("iy1"), w1 = F.create("iw1"), h1 = F.create("ih1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                w0, IntRangeDomain.of(4, 4), w1, IntRangeDomain.of(4, 4),
                y0, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                h0, IntRangeDomain.of(2, 2), h1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d)).isEmpty();
    }

    // --- propagate(domains, changedSinceLastRun) -- dirty-pair filtering ---

    @Test
    void propagate_dirtyHintExcludingBothRectangles_skipsPairEntirely() {
        // Same infeasible setup as propagate_bothCasesImpossible_infeasible, but the hint names a
        // variable outside this pair entirely -- neither rectangle is dirty (including its size
        // variables, unlike DiffnConstraint), so the pair must be skipped.
        Variable<Integer> x0 = F.create("hix0"), y0 = F.create("hiy0"), w0 = F.create("hiw0"), h0 = F.create("hih0");
        Variable<Integer> x1 = F.create("hix1"), y1 = F.create("hiy1"), w1 = F.create("hiw1"), h1 = F.create("hih1");
        Variable<Integer> unrelated = F.create("hiUnrelated");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                w0, IntRangeDomain.of(4, 4), w1, IntRangeDomain.of(4, 4),
                y0, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                h0, IntRangeDomain.of(2, 2), h1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d, java.util.Set.of(unrelated))).contains(Map.of());
    }

    @Test
    void propagate_dirtyHintIncludingSizeVariable_stillChecksPair() {
        // Same setup, but the hint includes w0 (a size variable, not an origin) -- must still be
        // treated as dirtying rectangle 0, unlike DiffnConstraint where sizes are fixed constants.
        Variable<Integer> x0 = F.create("hjx0"), y0 = F.create("hjy0"), w0 = F.create("hjw0"), h0 = F.create("hjh0");
        Variable<Integer> x1 = F.create("hjx1"), y1 = F.create("hjy1"), w1 = F.create("hjw1"), h1 = F.create("hjh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                w0, IntRangeDomain.of(4, 4), w1, IntRangeDomain.of(4, 4),
                y0, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                h0, IntRangeDomain.of(2, 2), h1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d, java.util.Set.of(w0))).isEmpty();
    }

    @Test
    void propagate_dirtyHintIncludingHeightVariable_stillChecksPair() {
        // As propagate_dirtyHintIncludingSizeVariable_stillChecksPair, but via h0 specifically --
        // dirtyRectangles' own OR chain checks x/y origins then width then height in order, so a
        // width-only test never exercises the height disjunct actually being the deciding one.
        Variable<Integer> x0 = F.create("hkx0"), y0 = F.create("hky0"), w0 = F.create("hkw0"), h0 = F.create("hkh0");
        Variable<Integer> x1 = F.create("hkx1"), y1 = F.create("hky1"), w1 = F.create("hkw1"), h1 = F.create("hkh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                w0, IntRangeDomain.of(4, 4), w1, IntRangeDomain.of(4, 4),
                y0, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                h0, IntRangeDomain.of(2, 2), h1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d, java.util.Set.of(h0))).isEmpty();
    }

    @Test
    void propagate_nullHint_behavesLikeFullScan() {
        Variable<Integer> x0 = F.create("hkx0"), y0 = F.create("hky0"), w0 = F.create("hkw0"), h0 = F.create("hkh0");
        Variable<Integer> x1 = F.create("hkx1"), y1 = F.create("hky1"), w1 = F.create("hkw1"), h1 = F.create("hkh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(1, 1),
                w0, IntRangeDomain.of(2, 4), w1, IntRangeDomain.of(2, 4),
                y0, IntRangeDomain.of(2, 3), y1, IntRangeDomain.of(3, 4),
                h0, IntRangeDomain.of(2, 4), h1, IntRangeDomain.of(2, 4));
        assertThat(c.propagate(d, null)).isEqualTo(c.propagate(d));
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_widthsAndHeightsFixed_citesSizeVariablesTooAsSingleton() {
        // Same scenario as propagate_bothCasesImpossible_infeasible: every origin AND every
        // width/height variable is singleton, so the (up to 8-variable) citation set is fully
        // singleton and the reason should be non-empty, including the size variables.
        Variable<Integer> x0 = F.create("ex0"), y0 = F.create("ey0"), w0 = F.create("ew0"), h0 = F.create("eh0");
        Variable<Integer> x1 = F.create("ex1"), y1 = F.create("ey1"), w1 = F.create("ew1"), h1 = F.create("eh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                w0, IntRangeDomain.of(4, 4), w1, IntRangeDomain.of(4, 4),
                y0, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2),
                h0, IntRangeDomain.of(2, 2), h1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d)).isEmpty();
        var reason = c.explainInfeasible(d);
        assertThat(reason).isPresent();
        assertThat(reason.get().getVariables()).contains(x0, x1, y0, y1, w0, w1);
    }

    @Test
    void explainInfeasible_widthNotSingleton_returnsEmptyReason() {
        // Same as propagate_variableWidths_mandatoryXOverlapForcesYSeparation but widths stay
        // non-singleton in the returned domains, so the x-axis mandatory-overlap check's own
        // culprits (w0, w1) can't be cited as singleton -- unsound to omit them, so this must
        // decline (empty), not fall back to a partial/incorrect citation.
        Variable<Integer> x0 = F.create("fx0"), y0 = F.create("fy0"), w0 = F.create("fw0"), h0 = F.create("fh0");
        Variable<Integer> x1 = F.create("fx1"), y1 = F.create("fy1"), w1 = F.create("fw1"), h1 = F.create("fh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                w0, IntRangeDomain.of(4, 4), w1, IntRangeDomain.of(4, 4),
                y0, IntRangeDomain.of(0, 0), y1, IntRangeDomain.of(0, 2),
                h0, IntRangeDomain.of(2, 6), h1, IntRangeDomain.of(2, 2));
        // y0=0 (singleton), y1=[0,2], h0=[2,6] (not singleton) -> mandatory y-overlap check for
        // x-separation forcing would involve h0 not singleton, so any resulting infeasibility
        // reason on that path must decline.
        var propagated = c.propagate(d);
        if (propagated.isEmpty()) {
            assertThat(c.explainInfeasible(d)).isEmpty();
        }
    }

    @Test
    void explainInfeasible_feasible_returnsEmptyReason() {
        Variable<Integer> x0 = F.create("gx0"), y0 = F.create("gy0"), w0 = F.create("gw0"), h0 = F.create("gh0");
        Variable<Integer> x1 = F.create("gx1"), y1 = F.create("gy1"), w1 = F.create("gw1"), h1 = F.create("gh1");
        var c = DiffnVariableConstraint.of(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1));
        var d = domains(
                x0, IntRangeDomain.of(0, 5), x1, IntRangeDomain.of(0, 5),
                w0, IntRangeDomain.of(2, 2), w1, IntRangeDomain.of(2, 2),
                y0, IntRangeDomain.of(0, 5), y1, IntRangeDomain.of(0, 5),
                h0, IntRangeDomain.of(2, 2), h1, IntRangeDomain.of(2, 2));
        assertThat(c.explainInfeasible(d)).isEmpty();
    }

    // --- misc ---

    @Test
    void testToString() {
        Variable<Integer> x0 = F.create("tx0"), y0 = F.create("ty0"), w0 = F.create("tw0"), h0 = F.create("th0");
        var c = DiffnVariableConstraint.of(List.of(x0), List.of(y0), List.of(w0), List.of(h0));
        assertThat(c.toString()).isEqualTo("<(th0, tw0, tx0, ty0), diffn(rects=1, variable-sized)>");
    }

    // --- CSP builder method ---

    @Test
    void cspBuilder_diffnVariableConstraint_method() {
        Variable<Integer> x0 = F.create("bx0"), y0 = F.create("by0"), w0 = F.create("bw0"), h0 = F.create("bh0");
        Variable<Integer> x1 = F.create("bx1"), y1 = F.create("by1"), w1 = F.create("bw1"), h1 = F.create("bh1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x0, IntRangeDomain.of(0, 2))
                .variableDomain(x1, IntRangeDomain.of(0, 2))
                .variableDomain(w0, IntRangeDomain.of(2, 2))
                .variableDomain(w1, IntRangeDomain.of(2, 2))
                .variableDomain(y0, IntRangeDomain.of(0, 0))
                .variableDomain(y1, IntRangeDomain.of(0, 0))
                .variableDomain(h0, IntRangeDomain.of(2, 2))
                .variableDomain(h1, IntRangeDomain.of(2, 2))
                .diffnVariableConstraint(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(2);
    }

    // --- solver integration: rotation choice ---

    @Test
    void solver_rotationChoice_onlyNonOverlappingOrientationsSurvive() {
        // Two boxes each choosing between (w,h) = (3,1) or (1,3) via a shared rotation-linked
        // domain {1,3} per axis (mirroring an XCSP3 channelling table: only matching (w,h) pairs
        // are consistent -- approximated here directly via matched domains since this test targets
        // the diffn propagation, not the channelling table itself). x fixed to the same origin
        // forces a width-based mandatory-x-overlap check.
        Variable<Integer> x0 = F.create("sx0"), y0 = F.create("sy0"), w0 = F.create("sw0"), h0 = F.create("sh0");
        Variable<Integer> x1 = F.create("sx1"), y1 = F.create("sy1"), w1 = F.create("sw1"), h1 = F.create("sh1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x0, IntRangeDomain.of(0, 0))
                .variableDomain(x1, IntRangeDomain.of(0, 0))
                .variableDomain(w0, IntRangeDomain.of(1, 1))
                .variableDomain(w1, IntRangeDomain.of(1, 1))
                .variableDomain(y0, IntRangeDomain.of(0, 10))
                .variableDomain(y1, IntRangeDomain.of(0, 10))
                .variableDomain(h0, IntRangeDomain.of(3, 3))
                .variableDomain(h1, IntRangeDomain.of(3, 3))
                .diffnVariableConstraint(List.of(x0, x1), List.of(y0, y1), List.of(w0, w1), List.of(h0, h1))
                .build();
        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).isNotEmpty();
        for (Assignment a : solutions) {
            int actualY0 = a.getValue(y0).orElseThrow();
            int actualY1 = a.getValue(y1).orElseThrow();
            assertThat(actualY0 + 3 <= actualY1 || actualY1 + 3 <= actualY0).isTrue();
        }
    }

    // --- randomized cross-check: dirty-pair filtering must not change the converged fixpoint ---

    /**
     * As {@link DiffnConstraintTest#propagate_randomizedCrossCheck_fixpointConvergesIdentically}:
     * a single {@link #propagate(Map, java.util.Set)} call can legitimately do less work than the
     * unfiltered scan (an earlier-processed dirty pair can narrow a rectangle a later "clean" pair
     * depends on, invisible to that pair's own hint until the *next* round) -- what must never
     * differ is where the whole {@code while(changed)} loop converges. Includes width/height in
     * the random domains too, unlike the fixed-size sibling.
     */
    @Test
    void propagate_randomizedCrossCheck_fixpointConvergesIdentically() {
        var random = new java.util.Random(13);
        for (int trial = 0; trial < 300; trial++) {
            int n = 2 + random.nextInt(4); // 2..5 rectangles
            List<Variable<? extends Number>> xs = new java.util.ArrayList<>();
            List<Variable<? extends Number>> ys = new java.util.ArrayList<>();
            List<Variable<? extends Number>> ws = new java.util.ArrayList<>();
            List<Variable<? extends Number>> hs = new java.util.ArrayList<>();
            Map<Variable<?>, Domain<?>> d = new java.util.HashMap<>();
            for (int i = 0; i < n; i++) {
                Variable<Integer> x = F.create("vrx" + trial + "_" + i);
                Variable<Integer> y = F.create("vry" + trial + "_" + i);
                Variable<Integer> w = F.create("vrw" + trial + "_" + i);
                Variable<Integer> h = F.create("vrh" + trial + "_" + i);
                xs.add(x);
                ys.add(y);
                ws.add(w);
                hs.add(h);
                int xlo = random.nextInt(4), xhi = xlo + random.nextInt(3);
                int ylo = random.nextInt(4), yhi = ylo + random.nextInt(3);
                int wlo = 1 + random.nextInt(2), whi = wlo + random.nextInt(2);
                int hlo = 1 + random.nextInt(2), hhi = hlo + random.nextInt(2);
                d.put(x, IntRangeDomain.of(xlo, xhi));
                d.put(y, IntRangeDomain.of(ylo, yhi));
                d.put(w, IntRangeDomain.of(wlo, whi));
                d.put(h, IntRangeDomain.of(hlo, hhi));
            }
            var c = DiffnVariableConstraint.of(xs, ys, ws, hs);

            var filtered = runToFixpoint(c, d, true);
            var unfiltered = runToFixpoint(c, d, false);
            assertThat(filtered).as("trial %d initial=%s", trial, d).isEqualTo(unfiltered);
        }
    }

    private static Optional<Map<Variable<?>, Domain<?>>> runToFixpoint(
            DiffnVariableConstraint c, Map<Variable<?>, Domain<?>> initial, boolean useHint) {
        Map<Variable<?>, Domain<?>> current = new java.util.HashMap<>(initial);
        java.util.Set<Variable<?>> changed = null;
        while (true) {
            var result = useHint ? c.propagate(current, changed) : c.propagate(current);
            if (result.isEmpty()) return Optional.empty();
            Map<Variable<?>, Domain<?>> updates = result.get();
            if (updates.isEmpty()) return Optional.of(current);
            current.putAll(updates);
            changed = new java.util.HashSet<>(updates.keySet());
        }
    }
}
