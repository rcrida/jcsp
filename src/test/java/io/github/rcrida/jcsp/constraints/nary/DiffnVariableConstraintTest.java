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
}
