package io.github.rcrida.jcsp.solver.examples.miplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.domains.NumericSetDomain;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * MIPLIB's {@code flugpl} instance (airline fleet planning, Wagner/Gregory/Boyd): 6-period fleet
 * sizing with 11 integer + 7 continuous variables. Known optimum 1,201,500; LP relaxation
 * 1,167,185.73 -- a real integrality gap, so this only solves correctly if branch-and-bound actually
 * branches the integer fleet-count variables rather than snapping every {@code IntervalDomain} to a
 * midpoint before any integer decision is made.
 * <p>
 * This is the case that originally motivated ADR-0009: {@code STM}/{@code ANM} (aircraft counts,
 * ~80% of cost) dominate the objective, while {@code UE} (overtime hours) is continuous slack
 * determined by them. Transcribed directly from MIPLIB's {@code flugpl.mps} (fetched from
 * miplib.zib.de), not simplified -- see the field comments below for the row-by-row correspondence.
 * All 18 variables are modelled as {@code Variable<Double>} (the 11 integer ones via a whole-number
 * {@link NumericSetDomain}, not {@code Variable<Integer>}) so every constraint can use a single
 * uniform {@code Map<Variable<Double>, Double>} -- {@link io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint}
 * requires one numeric type per constraint, and this model's rows genuinely mix continuous and
 * integer variables (e.g. {@code ANZ2: 0.9*STM1 + ANM1 - STM2 = 0}).
 */
public class FlugplTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    /** MPS default upper bound (no explicit UP entry means [0, +infinity)); large but finite. */
    static final double UNBOUNDED = 1_000_000.0;

    static NumericDiscreteDomain<Double> wholeRange(int min, int max) {
        var builder = NumericDiscreteDomain.<Double>builder();
        for (int i = min; i <= max; i++) {
            builder.value((double) i);
        }
        return builder.build();
    }

    @Test
    void solvesToKnownOptimum() {
        // STM = Standardflugzeuge (standard aircraft on hand), ANM = Anschaffung (acquired this
        // period), UE = Ueberstunden (overtime hours). STM1 is continuous in the source MPS (only
        // ANM/STM2..6 are marked INTORG) but pinned to 60 by ANZ1 regardless.
        Variable<Double> stm1 = F.create("STM1");
        Variable<Double> anm1 = F.create("ANM1");
        Variable<Double> ue1 = F.create("UE1");
        Variable<Double> stm2 = F.create("STM2");
        Variable<Double> anm2 = F.create("ANM2");
        Variable<Double> ue2 = F.create("UE2");
        Variable<Double> stm3 = F.create("STM3");
        Variable<Double> anm3 = F.create("ANM3");
        Variable<Double> ue3 = F.create("UE3");
        Variable<Double> stm4 = F.create("STM4");
        Variable<Double> anm4 = F.create("ANM4");
        Variable<Double> ue4 = F.create("UE4");
        Variable<Double> stm5 = F.create("STM5");
        Variable<Double> anm5 = F.create("ANM5");
        Variable<Double> ue5 = F.create("UE5");
        Variable<Double> stm6 = F.create("STM6");
        Variable<Double> anm6 = F.create("ANM6");
        Variable<Double> ue6 = F.create("UE6");

        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(stm1, IntervalDomain.of(0.0, UNBOUNDED))
                .variableDomain(anm1, wholeRange(0, 18))
                .variableDomain(ue1, IntervalDomain.of(0.0, UNBOUNDED))
                .variableDomain(stm2, wholeRange(57, 75))
                .variableDomain(anm2, wholeRange(0, 18))
                .variableDomain(ue2, IntervalDomain.of(0.0, UNBOUNDED))
                .variableDomain(stm3, wholeRange(57, 75))
                .variableDomain(anm3, wholeRange(0, 18))
                .variableDomain(ue3, IntervalDomain.of(0.0, UNBOUNDED))
                .variableDomain(stm4, wholeRange(57, 75))
                .variableDomain(anm4, wholeRange(0, 18))
                .variableDomain(ue4, IntervalDomain.of(0.0, UNBOUNDED))
                .variableDomain(stm5, wholeRange(57, 75))
                .variableDomain(anm5, wholeRange(0, 18))
                .variableDomain(ue5, IntervalDomain.of(0.0, UNBOUNDED))
                .variableDomain(stm6, wholeRange(57, 75))
                .variableDomain(anm6, wholeRange(0, 18))
                .variableDomain(ue6, IntervalDomain.of(0.0, UNBOUNDED))
                // ANZ1: STM1 = 60
                .linearConstraint(Map.of(stm1, 1.0), Operator.EQ, 60.0)
                // ANZ2..6: STM(t+1) = 0.9*STM(t) + ANM(t)
                .linearConstraint(Map.of(stm1, 0.9, anm1, 1.0, stm2, -1.0), Operator.EQ, 0.0)
                .linearConstraint(Map.of(stm2, 0.9, anm2, 1.0, stm3, -1.0), Operator.EQ, 0.0)
                .linearConstraint(Map.of(stm3, 0.9, anm3, 1.0, stm4, -1.0), Operator.EQ, 0.0)
                .linearConstraint(Map.of(stm4, 0.9, anm4, 1.0, stm5, -1.0), Operator.EQ, 0.0)
                .linearConstraint(Map.of(stm5, 0.9, anm5, 1.0, stm6, -1.0), Operator.EQ, 0.0)
                // STD1..6: 150*STM(t) - 100*ANM(t) + UE(t) >= demand(t)
                .linearConstraint(Map.of(stm1, 150.0, anm1, -100.0, ue1, 1.0), Operator.GEQ, 8000.0)
                .linearConstraint(Map.of(stm2, 150.0, anm2, -100.0, ue2, 1.0), Operator.GEQ, 9000.0)
                .linearConstraint(Map.of(stm3, 150.0, anm3, -100.0, ue3, 1.0), Operator.GEQ, 8000.0)
                .linearConstraint(Map.of(stm4, 150.0, anm4, -100.0, ue4, 1.0), Operator.GEQ, 10000.0)
                .linearConstraint(Map.of(stm5, 150.0, anm5, -100.0, ue5, 1.0), Operator.GEQ, 9000.0)
                .linearConstraint(Map.of(stm6, 150.0, anm6, -100.0, ue6, 1.0), Operator.GEQ, 12000.0)
                // UEB1..6: UE(t) <= 20*STM(t)
                .linearConstraint(Map.of(stm1, -20.0, ue1, 1.0), Operator.LEQ, 0.0)
                .linearConstraint(Map.of(stm2, -20.0, ue2, 1.0), Operator.LEQ, 0.0)
                .linearConstraint(Map.of(stm3, -20.0, ue3, 1.0), Operator.LEQ, 0.0)
                .linearConstraint(Map.of(stm4, -20.0, ue4, 1.0), Operator.LEQ, 0.0)
                .linearConstraint(Map.of(stm5, -20.0, ue5, 1.0), Operator.LEQ, 0.0)
                .linearConstraint(Map.of(stm6, -20.0, ue6, 1.0), Operator.LEQ, 0.0)
                .build();

        LinearObjective objective = LinearObjective.builder()
                .coefficient(stm1, 2700.0).coefficient(anm1, 1500.0).coefficient(ue1, 30.0)
                .coefficient(stm2, 2700.0).coefficient(anm2, 1500.0).coefficient(ue2, 30.0)
                .coefficient(stm3, 2700.0).coefficient(anm3, 1500.0).coefficient(ue3, 30.0)
                .coefficient(stm4, 2700.0).coefficient(anm4, 1500.0).coefficient(ue4, 30.0)
                .coefficient(stm5, 2700.0).coefficient(anm5, 1500.0).coefficient(ue5, 30.0)
                .coefficient(stm6, 2700.0).coefficient(anm6, 1500.0).coefficient(ue6, 30.0)
                .build();

        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();

        assertThat(solution).isPresent();
        assertThat(objective.applyAsDouble(solution.get())).isCloseTo(1_201_500.0, within(0.5));
    }
}
