package io.github.rcrida.jcsp.solver.examples.csplib;
import io.github.rcrida.jcsp.solver.Solver;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.parser.xcsp3.Xcsp3Instance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binary knapsack (CSPLib prob133): choose which items to pack to maximise value without
 * exceeding the weight capacity. Each item is a 0/1 variable.
 *
 * <p>{@link #problem()} and {@link #xcsp3Instance()} parse the CSP and objective from a real
 * XCSP3 instance file (30 items, capacity 100 — see the file's own comment for provenance)
 * rather than building it via jcsp's constraint builder API; {@link CsplibBenchmarks} uses the
 * same {@link #xcsp3Instance()} for its own objective rather than a separate hand-rolled
 * function. At this size, enumerating every feasible subset the way a small hand-picked instance
 * would allow isn't practical, so only the proven optimum (709) is checked here.
 */
public class Prob133KnapsackTest {

    static Xcsp3Instance xcsp3Instance() {
        return Xcsp3CsplibResource.parse("knapsack-30items.xml");
    }

    static ConstraintSatisfactionProblem problem() {
        return xcsp3Instance().csp();
    }

    @Test
    void feasibility_hasASolutionWithinCapacity() {
        val csp = problem();
        val result = Solver.Factory.INSTANCE.createSolver(csp).getSolution();
        assertThat(result).hasValueSatisfying(a -> assertThat(a.isSolution(csp)).isTrue());
    }

    @Test
    void optimization_maxValue() {
        val instance = xcsp3Instance();
        val result = Solver.Factory.INSTANCE.createSolver(instance.csp(), instance.objective()).getSolution();
        assertThat(result).isPresent();
        assertThat(result.get().isSolution(instance.csp())).isTrue();
        // instance.objective() is minimize-oriented (maximize negates coefficients internally),
        // so the true maximised value is the negation of the minimized objective.
        assertThat(-instance.objective().applyAsDouble(result.get())).isEqualTo(709.0);
    }
}
