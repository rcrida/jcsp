package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MinimumDegreeVariableSelectorTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> LOW  = F.create("LOW");   // 1 neighbour
    static final Variable<Integer> MID  = F.create("MID");   // 2 neighbours
    static final Variable<Integer> HIGH = F.create("HIGH");  // 3 neighbours

    // LOW-MID-HIGH-? forms a path: LOW-MID, MID-HIGH, HIGH-LOW (triangle + extra)
    // Simpler: just a path LOW -- MID -- HIGH -- extra
    static final Variable<Integer> EXTRA = F.create("EXTRA");

    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(LOW,   IntRangeDomain.of(1, 3))
            .variableDomain(MID,   IntRangeDomain.of(1, 3))
            .variableDomain(HIGH,  IntRangeDomain.of(1, 3))
            .variableDomain(EXTRA, IntRangeDomain.of(1, 3))
            .notEqualsConstraint(LOW, MID)           // LOW: degree 1
            .notEqualsConstraint(MID, HIGH)          // MID: degree 2
            .notEqualsConstraint(HIGH, EXTRA)        // HIGH: degree 2
            .notEqualsConstraint(HIGH, LOW)          // HIGH: degree 3, LOW: degree 2
            .build();

    @Test
    void lowerDegreeComesFirst() {
        val selector = MinimumDegreeVariableSelector.Factory.INSTANCE.create(CSP);
        assertThat(selector.compare(LOW, HIGH)).isNegative();
        assertThat(selector.compare(HIGH, LOW)).isPositive();
    }

    @Test
    void equalDegreeComparesAsZero() {
        val selector = MinimumDegreeVariableSelector.Factory.INSTANCE.create(CSP);
        assertThat(selector.compare(LOW, MID)).isZero(); // both degree 2
    }

    @Test
    void factoryCreatesNewInstancePerCsp() {
        val selector1 = MinimumDegreeVariableSelector.Factory.INSTANCE.create(CSP);
        val selector2 = MinimumDegreeVariableSelector.Factory.INSTANCE.create(CSP);
        assertThat(selector1).isNotSameAs(selector2);
    }
}
