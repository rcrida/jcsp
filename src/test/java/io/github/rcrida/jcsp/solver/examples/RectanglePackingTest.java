package io.github.rcrida.jcsp.solver.examples;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pack four rectangles — A(2×2), B(2×1), C(1×2), D(1×1) — into a 3×3 bounding box.
 * Total area is 4+2+2+1=9=3×3, so every valid placement tiles the box exactly.
 *
 * <p>The 2×2 piece A can sit in any of the four corners; for each corner the remaining
 * L-shaped region admits exactly 3 tilings — giving 12 solutions in total.
 */
public class RectanglePackingTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // A: 2 wide, 2 tall  →  x ∈ [0,1], y ∈ [0,1]
    static final Variable<Integer> AX = F.create("ax");
    static final Variable<Integer> AY = F.create("ay");

    // B: 2 wide, 1 tall  →  x ∈ [0,1], y ∈ [0,2]
    static final Variable<Integer> BX = F.create("bx");
    static final Variable<Integer> BY = F.create("by");

    // C: 1 wide, 2 tall  →  x ∈ [0,2], y ∈ [0,1]
    static final Variable<Integer> CX = F.create("cx");
    static final Variable<Integer> CY = F.create("cy");

    // D: 1 wide, 1 tall  →  x ∈ [0,2], y ∈ [0,2]
    static final Variable<Integer> DX = F.create("dx");
    static final Variable<Integer> DY = F.create("dy");

    static ConstraintSatisfactionProblem problem() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(AX, IntRangeDomain.of(0, 1))
                .variableDomain(AY, IntRangeDomain.of(0, 1))
                .variableDomain(BX, IntRangeDomain.of(0, 1))
                .variableDomain(BY, IntRangeDomain.of(0, 2))
                .variableDomain(CX, IntRangeDomain.of(0, 2))
                .variableDomain(CY, IntRangeDomain.of(0, 1))
                .variableDomain(DX, IntRangeDomain.of(0, 2))
                .variableDomain(DY, IntRangeDomain.of(0, 2))
                .diffnConstraint(
                        List.of(AX, BX, CX, DX),
                        List.of(AY, BY, CY, DY),
                        List.of(2.0, 2.0, 1.0, 1.0),
                        List.of(2.0, 1.0, 2.0, 1.0))
                .build();
    }

    @Test
    void allSolutions() {
        val solutions = Solver.Factory.INSTANCE.createSolver(problem()).getSolutions().toList();
        assertThat(solutions).hasSize(12);
        solutions.forEach(a -> System.out.printf(
                "A(%d,%d) B(%d,%d) C(%d,%d) D(%d,%d)%n",
                a.getValue(AX).orElseThrow(), a.getValue(AY).orElseThrow(),
                a.getValue(BX).orElseThrow(), a.getValue(BY).orElseThrow(),
                a.getValue(CX).orElseThrow(), a.getValue(CY).orElseThrow(),
                a.getValue(DX).orElseThrow(), a.getValue(DY).orElseThrow()));
    }
}
