package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DiffnConstraintTest {
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
    void of_mismatchedSizes_asserts() {
        Variable<Integer> x = F.create("x");
        Variable<Integer> y = F.create("y");
        assertThatThrownBy(() -> DiffnConstraint.of(
                List.of(x), List.of(y), List.of(2.0), List.of(2.0, 2.0)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_xsYsSizeMismatch_asserts() {
        Variable<Integer> x0 = F.create("mx0"), x1 = F.create("mx1");
        Variable<Integer> y = F.create("my");
        assertThatThrownBy(() -> DiffnConstraint.of(
                List.of(x0, x1), List.of(y), List.of(2.0), List.of(2.0)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_ysWidthsSizeMismatch_asserts() {
        Variable<Integer> x0 = F.create("nx0"), x1 = F.create("nx1");
        Variable<Integer> y0 = F.create("ny0"), y1 = F.create("ny1");
        assertThatThrownBy(() -> DiffnConstraint.of(
                List.of(x0, x1), List.of(y0, y1), List.of(2.0), List.of(2.0)))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_separatedInX_iBeforeJ() {
        Variable<Integer> x0 = F.create("x0"), y0 = F.create("y0");
        Variable<Integer> x1 = F.create("x1"), y1 = F.create("y1");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        // rect0 [0,2)x[0,2), rect1 [3,5)x[0,2): separated in x
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, y0, 0, x1, 3, y1, 0)))).isTrue();
    }

    @Test
    void isSatisfiedBy_separatedInX_jBeforeI() {
        Variable<Integer> x0 = F.create("x0b"), y0 = F.create("y0b");
        Variable<Integer> x1 = F.create("x1b"), y1 = F.create("y1b");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        // rect1 entirely left of rect0
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 5, y0, 0, x1, 0, y1, 0)))).isTrue();
    }

    @Test
    void isSatisfiedBy_separatedInY_iBelowJ() {
        Variable<Integer> x0 = F.create("x0c"), y0 = F.create("y0c");
        Variable<Integer> x1 = F.create("x1c"), y1 = F.create("y1c");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        // same x, separated in y
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, y0, 0, x1, 0, y1, 3)))).isTrue();
    }

    @Test
    void isSatisfiedBy_separatedInY_jBelowI() {
        Variable<Integer> x0 = F.create("x0d"), y0 = F.create("y0d");
        Variable<Integer> x1 = F.create("x1d"), y1 = F.create("y1d");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, y0, 5, x1, 0, y1, 0)))).isTrue();
    }

    @Test
    void isSatisfiedBy_overlapping_false() {
        Variable<Integer> x0 = F.create("x0e"), y0 = F.create("y0e");
        Variable<Integer> x1 = F.create("x1e"), y1 = F.create("y1e");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(3.0, 3.0), List.of(3.0, 3.0));
        // rect0 [0,3)x[0,3), rect1 [1,4)x[1,4): overlap
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, y0, 0, x1, 1, y1, 1)))).isFalse();
    }

    @Test
    void isSatisfiedBy_xMissing_optimistic() {
        Variable<Integer> x0 = F.create("x0f"), y0 = F.create("y0f");
        Variable<Integer> x1 = F.create("x1f"), y1 = F.create("y1f");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(y0, 0, x1, 0, y1, 0)))).isTrue();
    }

    @Test
    void isSatisfiedBy_yMissing_optimistic() {
        Variable<Integer> x0 = F.create("x0g"), y0 = F.create("y0g");
        Variable<Integer> x1 = F.create("x1g"), y1 = F.create("y1g");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        // x0 present, y0 missing
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x0, 0, x1, 0, y1, 0)))).isTrue();
    }

    // --- propagate (IntRangeDomain) ---

    /** rect0 x fixed 0 w=4 -> compulsory [0,4); rect1 x fixed 2 w=4 -> compulsory [2,6): overlap. */
    private DiffnConstraint xOverlapConstraint(Variable<Integer> x0, Variable<Integer> y0,
            Variable<Integer> x1, Variable<Integer> y1, double h0, double h1) {
        return DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(4.0, 4.0), List.of(h0, h1));
    }

    @Test
    void propagate_bothCasesPossible_noPruning() {
        Variable<Integer> x0 = F.create("px0a"), y0 = F.create("py0a");
        Variable<Integer> x1 = F.create("px1a"), y1 = F.create("py1a");
        var c = xOverlapConstraint(x0, y0, x1, y1, 2.0, 2.0);
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                y0, IntRangeDomain.of(0, 10), y1, IntRangeDomain.of(0, 10));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_caseBImpossible_onlyA() {
        Variable<Integer> x0 = F.create("px0b"), y0 = F.create("py0b");
        Variable<Integer> x1 = F.create("px1b"), y1 = F.create("py1b");
        var c = xOverlapConstraint(x0, y0, x1, y1, 2.0, 2.0);
        // y0=[2,3], y1=[3,4] -> caseB false. Only A: y0 max lowered, y1 min raised.
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                y0, IntRangeDomain.of(2, 3), y1, IntRangeDomain.of(3, 4));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        assertThat(result.get().get(y0)).isEqualTo(IntRangeDomain.of(2, 2));
        assertThat(result.get().get(y1)).isEqualTo(IntRangeDomain.of(4, 4));
    }

    @Test
    void propagate_caseBImpossible_onlyA_noOpOnJ() {
        Variable<Integer> x0 = F.create("px0n"), y0 = F.create("py0n");
        Variable<Integer> x1 = F.create("px1n"), y1 = F.create("py1n");
        var c = xOverlapConstraint(x0, y0, x1, y1, 2.0, 2.0);
        // y0=[0,3], y1=[4,4]: only A; y0 max lowered to 2, y1 min stays 4 (no-op).
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                y0, IntRangeDomain.of(0, 3), y1, IntRangeDomain.of(4, 4));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        assertThat(result.get().get(y0)).isEqualTo(IntRangeDomain.of(0, 2));
        assertThat(result.get()).doesNotContainKey(y1);
    }

    @Test
    void propagate_caseAImpossible_onlyB() {
        Variable<Integer> x0 = F.create("px0c"), y0 = F.create("py0c");
        Variable<Integer> x1 = F.create("px1c"), y1 = F.create("py1c");
        var c = xOverlapConstraint(x0, y0, x1, y1, 2.0, 2.0);
        // y0=[3,4], y1=[2,3] -> caseA false. Only B: y0 min raised, y1 max lowered.
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                y0, IntRangeDomain.of(3, 4), y1, IntRangeDomain.of(2, 3));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        assertThat(result.get().get(y0)).isEqualTo(IntRangeDomain.of(4, 4));
        assertThat(result.get().get(y1)).isEqualTo(IntRangeDomain.of(2, 2));
    }

    @Test
    void propagate_bothCasesImpossible_infeasible() {
        Variable<Integer> x0 = F.create("px0d"), y0 = F.create("py0d");
        Variable<Integer> x1 = F.create("px1d"), y1 = F.create("py1d");
        var c = xOverlapConstraint(x0, y0, x1, y1, 2.0, 2.0);
        // identical rectangles -> cannot separate on either axis.
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                y0, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d)).isEmpty();
    }

    @Test
    void propagate_noMandatoryOverlap_noChange() {
        Variable<Integer> x0 = F.create("px0e"), y0 = F.create("py0e");
        Variable<Integer> x1 = F.create("px1e"), y1 = F.create("py1e");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        var d = domains(
                x0, IntRangeDomain.of(0, 5), x1, IntRangeDomain.of(0, 5),
                y0, IntRangeDomain.of(0, 5), y1, IntRangeDomain.of(0, 5));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_iCompulsoryEmpty_noChange() {
        // cond1 false: rect i compulsory part empty on x-axis.
        Variable<Integer> x0 = F.create("px0f"), y0 = F.create("py0f");
        Variable<Integer> x1 = F.create("px1f"), y1 = F.create("py1f");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        var d = domains(
                x0, IntRangeDomain.of(0, 5), x1, IntRangeDomain.of(0, 0),
                y0, IntRangeDomain.of(0, 5), y1, IntRangeDomain.of(0, 5));
        assertThat(c.propagate(d).orElseThrow()).isEmpty();
    }

    @Test
    void propagate_jCompulsoryEmpty_noChange() {
        // cond1 true, cond2 false: rect j compulsory part empty on x-axis.
        Variable<Integer> x0 = F.create("px0g"), y0 = F.create("py0g");
        Variable<Integer> x1 = F.create("px1g"), y1 = F.create("py1g");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(4.0, 2.0), List.of(2.0, 2.0));
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(0, 5),
                y0, IntRangeDomain.of(0, 5), y1, IntRangeDomain.of(0, 5));
        assertThat(c.propagate(d).orElseThrow()).isEmpty();
    }

    @Test
    void propagate_compulsoryPartsDisjoint_cond3False() {
        // cond1,cond2 true, cond3 false: i compulsory entirely right of j compulsory.
        Variable<Integer> x0 = F.create("px0h"), y0 = F.create("py0h");
        Variable<Integer> x1 = F.create("px1h"), y1 = F.create("py1h");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        // x0 fixed 3 -> comp [3,5); x1 fixed 0 -> comp [0,2): disjoint, i right of j.
        var d = domains(
                x0, IntRangeDomain.of(3, 3), x1, IntRangeDomain.of(0, 0),
                y0, IntRangeDomain.of(0, 5), y1, IntRangeDomain.of(0, 5));
        assertThat(c.propagate(d).orElseThrow()).isEmpty();
    }

    @Test
    void propagate_compulsoryPartsDisjoint_cond4False() {
        // cond1,cond2,cond3 true, cond4 false: i compulsory entirely left of j compulsory.
        Variable<Integer> x0 = F.create("px0i"), y0 = F.create("py0i");
        Variable<Integer> x1 = F.create("px1i"), y1 = F.create("py1i");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        // x0 fixed 0 -> comp [0,2); x1 fixed 3 -> comp [3,5): disjoint, i left of j.
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(3, 3),
                y0, IntRangeDomain.of(0, 5), y1, IntRangeDomain.of(0, 5));
        assertThat(c.propagate(d).orElseThrow()).isEmpty();
    }

    @Test
    void propagate_mandatoryYOverlap_xInfeasible() {
        // rect0: x=[0,3] (no compulsory x-part since 3>=0+2), y fixed 0, w=2, h=5.
        // rect1: x fixed 0,                                    y fixed 2, w=5, h=5.
        // Step 1: x-compulsory part of rect0 is empty (3>=0+2) -> no mandatory x-overlap -> passes.
        // Step 2: y-compulsory parts [0,5) and [2,7) overlap (mandatory y-overlap).
        //         x-separation: caseA 0+2=2<=0 FALSE, caseB 0+5=5<=3 FALSE -> infeasible (lines 104-105).
        Variable<Integer> x0 = F.create("zx0"), y0 = F.create("zy0");
        Variable<Integer> x1 = F.create("zx1"), y1 = F.create("zy1");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 5.0), List.of(5.0, 5.0));
        var d = domains(
                x0, IntRangeDomain.of(0, 3), x1, IntRangeDomain.of(0, 0),
                y0, IntRangeDomain.of(0, 0), y1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d)).isEmpty();
    }

    @Test
    void propagate_mandatoryYOverlap_propagatesX() {
        // y mandatory overlap (h=4 each, fixed origins) forces x separation.
        Variable<Integer> x0 = F.create("qx0"), y0 = F.create("qy0");
        Variable<Integer> x1 = F.create("qx1"), y1 = F.create("qy1");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(4.0, 4.0));
        // y0 fixed 0 -> comp [0,4); y1 fixed 2 -> comp [2,6): overlap.
        // x0=[2,3], x1=[3,4]: caseB false on x -> x0 max lowered, x1 min raised.
        var d = domains(
                x0, IntRangeDomain.of(2, 3), x1, IntRangeDomain.of(3, 4),
                y0, IntRangeDomain.of(0, 0), y1, IntRangeDomain.of(2, 2));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        assertThat(result.get().get(x0)).isEqualTo(IntRangeDomain.of(2, 2));
        assertThat(result.get().get(x1)).isEqualTo(IntRangeDomain.of(4, 4));
    }

    // --- propagate (IntervalDomain) ---

    @Test
    void propagate_interval_caseBImpossible_onlyA() {
        Variable<Double> x0 = F.create("ix0a"), y0 = F.create("iy0a");
        Variable<Double> x1 = F.create("ix1a"), y1 = F.create("iy1a");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(4.0, 4.0), List.of(2.0, 2.0));
        var d = domains(
                x0, IntervalDomain.of(0.0, 0.0), x1, IntervalDomain.of(2.0, 2.0),
                y0, IntervalDomain.of(2.0, 3.0), y1, IntervalDomain.of(3.0, 4.0));
        var result = c.propagate(d);
        assertThat(result).isPresent();
        assertThat(result.get().get(y0)).isEqualTo(IntervalDomain.of(2.0, 2.0));
        assertThat(result.get().get(y1)).isEqualTo(IntervalDomain.of(4.0, 4.0));
    }

    @Test
    void propagate_interval_infeasible() {
        Variable<Double> x0 = F.create("ix0b"), y0 = F.create("iy0b");
        Variable<Double> x1 = F.create("ix1b"), y1 = F.create("iy1b");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(4.0, 4.0), List.of(2.0, 2.0));
        var d = domains(
                x0, IntervalDomain.of(0.0, 0.0), x1, IntervalDomain.of(2.0, 2.0),
                y0, IntervalDomain.of(2.0, 2.0), y1, IntervalDomain.of(2.0, 2.0));
        assertThat(c.propagate(d)).isEmpty();
    }

    @Test
    void propagate_interval_noChange() {
        Variable<Double> x0 = F.create("ix0c"), y0 = F.create("iy0c");
        Variable<Double> x1 = F.create("ix1c"), y1 = F.create("iy1c");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        var d = domains(
                x0, IntervalDomain.of(0.0, 5.0), x1, IntervalDomain.of(0.0, 5.0),
                y0, IntervalDomain.of(0.0, 5.0), y1, IntervalDomain.of(0.0, 5.0));
        assertThat(c.propagate(d).orElseThrow()).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_bothCasesImpossible_allFourSingleton_attributesAll() {
        // Same domains as propagate_bothCasesImpossible_infeasible: identical rectangles, all
        // four origins fixed, so every variable in the failing pair's four is singleton.
        Variable<Integer> x0 = F.create("ex0a"), y0 = F.create("ey0a");
        Variable<Integer> x1 = F.create("ex1a"), y1 = F.create("ey1a");
        var c = xOverlapConstraint(x0, y0, x1, y1, 2.0, 2.0);
        var d = domains(
                x0, IntRangeDomain.of(0, 0), x1, IntRangeDomain.of(2, 2),
                y0, IntRangeDomain.of(2, 2), y1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d)).isEmpty();
        assertThat(c.explainInfeasible(d)).containsOnly(
                Map.entry(x0, 0), Map.entry(x1, 2), Map.entry(y0, 2), Map.entry(y1, 2));
    }

    @Test
    void explainInfeasible_notAllFourSingleton_returnsEmptyReason() {
        // Same domains as propagate_mandatoryYOverlap_xInfeasible: mandatory overlap is on y
        // (both y0, y1 singleton), but the x-axis separation check involves x0=[0,3] (not
        // singleton) and x1=0 (singleton). Unlike a binary comparator, no partial subset of the
        // four responsible variables is a sound nogood here — a different x0 within [0,3] could
        // still fail to separate, but the joint condition depends on x0's exact bounds, so citing
        // {y0, y1, x1} alone would incorrectly generalise to configurations where x0 makes
        // separation possible. Since x0 isn't singleton, the method must fall back to empty.
        Variable<Integer> x0 = F.create("ex0b"), y0 = F.create("ey0b");
        Variable<Integer> x1 = F.create("ex1b"), y1 = F.create("ey1b");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 5.0), List.of(5.0, 5.0));
        var d = domains(
                x0, IntRangeDomain.of(0, 3), x1, IntRangeDomain.of(0, 0),
                y0, IntRangeDomain.of(0, 0), y1, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(d)).isEmpty();
        assertThat(c.explainInfeasible(d)).isEmpty();
    }

    @Test
    void explainInfeasible_feasible_returnsEmptyReason() {
        Variable<Integer> x0 = F.create("ex0c"), y0 = F.create("ey0c");
        Variable<Integer> x1 = F.create("ex1c"), y1 = F.create("ey1c");
        var c = DiffnConstraint.of(List.of(x0, x1), List.of(y0, y1),
                List.of(2.0, 2.0), List.of(2.0, 2.0));
        var d = domains(
                x0, IntRangeDomain.of(0, 5), x1, IntRangeDomain.of(0, 5),
                y0, IntRangeDomain.of(0, 5), y1, IntRangeDomain.of(0, 5));
        assertThat(c.explainInfeasible(d)).isEmpty();
    }

    // --- misc ---

    @Test
    void testToString() {
        Variable<Integer> x0 = F.create("tx0"), y0 = F.create("ty0");
        var c = DiffnConstraint.of(List.of(x0), List.of(y0), List.of(2.0), List.of(2.0));
        assertThat(c.toString()).isEqualTo("<(tx0, ty0), diffn(rects=1)>");
    }

    // --- CSP builder method ---

    @Test
    void cspBuilder_diffnConstraint_method() {
        Variable<Integer> x0 = F.create("bx0"), y0 = F.create("by0");
        Variable<Integer> x1 = F.create("bx1"), y1 = F.create("by1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x0, IntRangeDomain.of(0, 2))
                .variableDomain(x1, IntRangeDomain.of(0, 2))
                .variableDomain(y0, IntRangeDomain.of(0, 0))
                .variableDomain(y1, IntRangeDomain.of(0, 0))
                .diffnConstraint(List.of(x0, x1), List.of(y0, y1), List.of(2.0, 2.0), List.of(2.0, 2.0))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(2);
    }

    // --- solver integration ---

    @Test
    void solver_threeRectsInStrip_sixPlacements() {
        // Three 2x2 rectangles in a 6-wide, 2-tall strip: y fixed at 0, x in {0..4}.
        // Only disjoint tiling is starts {0,2,4} in any order -> 3! = 6 placements.
        Variable<Integer> x0 = F.create("sx0"), y0 = F.create("sy0");
        Variable<Integer> x1 = F.create("sx1"), y1 = F.create("sy1");
        Variable<Integer> x2 = F.create("sx2"), y2 = F.create("sy2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x0, IntRangeDomain.of(0, 4))
                .variableDomain(x1, IntRangeDomain.of(0, 4))
                .variableDomain(x2, IntRangeDomain.of(0, 4))
                .variableDomain(y0, IntRangeDomain.of(0, 0))
                .variableDomain(y1, IntRangeDomain.of(0, 0))
                .variableDomain(y2, IntRangeDomain.of(0, 0))
                .constraint(DiffnConstraint.of(
                        List.of(x0, x1, x2), List.of(y0, y1, y2),
                        List.of(2.0, 2.0, 2.0), List.of(2.0, 2.0, 2.0)))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList()).hasSize(6);
    }
}
