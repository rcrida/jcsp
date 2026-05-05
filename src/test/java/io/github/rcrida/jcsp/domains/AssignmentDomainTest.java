package io.github.rcrida.jcsp.domains;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class AssignmentDomainTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    Variable variable1 = VARIABLE_FACTORY.create("V1");
    Variable variable2 = VARIABLE_FACTORY.create("V2");
    Variable variable3 = VARIABLE_FACTORY.create("V3");
    Domain domain1 = DomainObjectSet.builder().values(List.of(1, 2)).build();
    Domain domain2 = DomainObjectSet.builder().values(List.of("a", "b")).build();
    Domain domain3 = DomainObjectSet.builder().values(List.of(true, false)).build();
    ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
            .variableDomain(variable1, domain1)
            .variableDomain(variable2, domain2)
            .variableDomain(variable3, domain3)
            .biPredicateConstraint(variable1, variable2, (v1, v2) -> !((int) v1 == 1 && v2 == "b"))
            .biPredicateConstraint(variable2, variable3, (v2, v3) -> !(v2 == "a" && (boolean) v3))
            .build();
    AssignmentDomain assignmentDomain;

    @BeforeEach
    void setUp() {
        assignmentDomain = new AssignmentDomain(Map.of(variable1, domain1, variable2, domain2, variable3, domain3), csp);
    }

    @Test
    void stream() {
        assertThat((Stream<Assignment>) assignmentDomain.stream()).containsOnly(
                Assignment.of(Map.of(variable1, 1, variable2, "a", variable3, false)),
                Assignment.of(Map.of(variable1, 2, variable2, "a", variable3, false)),
                Assignment.of(Map.of(variable1, 2, variable2, "b", variable3, true)),
                Assignment.of(Map.of(variable1, 2, variable2, "b", variable3, false))
        );
    }
}
