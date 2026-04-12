package org.jcsp.solver.tree.selector;

import org.jcsp.TreeConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.consistency.arc.Arc;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.domains.DomainObjectSet;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TopologicalUnassignedVariableSelectorTest {
    static final Variable.Factory FACTORY = Variable.Factory.INSTANCE;
    static final Domain DOMAIN = DomainObjectSet.builder().value(1).value(2).value(3).build();
    static final Variable A = FACTORY.create("A");
    static final Variable B = FACTORY.create("B");
    static final List<Arc> ARCS = List.of(Arc.of(A, B));
    static final TreeConstraintSatisfactionProblem TCSP = new TreeConstraintSatisfactionProblem(
            Map.of(A, DOMAIN, B, DOMAIN), Set.of(BinaryNotEqualsConstraint.builder().left(A).right(B).build())
    );
    TopologicalUnassignedVariableSelector selector = new TopologicalUnassignedVariableSelector(ARCS);

    static Stream<Arguments> select() {
        return Stream.of(
                Arguments.of(Assignment.EMPTY, B),
                Arguments.of(Assignment.of(Map.of(A, 1)), B)
        );
    }

    @ParameterizedTest
    @MethodSource
    void select(Assignment assignment, Variable expected) {
        assertThat(selector.select(TCSP, assignment)).isEqualTo(expected);
    }

    @Test
    void select_illegal() {
        assertThatThrownBy(() -> selector.select(TCSP, Assignment.of(Map.of(A, 1, B, 2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No unassigned arc found");
    }
}
