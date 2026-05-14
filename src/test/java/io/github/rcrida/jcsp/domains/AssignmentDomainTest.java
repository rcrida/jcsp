package io.github.rcrida.jcsp.domains;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AssignmentDomainTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    Variable<Integer> variable1 = VARIABLE_FACTORY.create("V1");
    Variable<String> variable2 = VARIABLE_FACTORY.create("V2");
    Variable<Boolean> variable3 = VARIABLE_FACTORY.create("V3");
    Domain<Integer> domain1 = DomainObjectSet.<Integer>builder().values(List.of(1, 2)).build();
    Domain<String> domain2 = DomainObjectSet.<String>builder().values(List.of("a", "b")).build();
    Domain<Boolean> domain3 = DomainObjectSet.<Boolean>builder().values(List.of(true, false)).build();
    ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
            .variableDomain(variable1, domain1)
            .variableDomain(variable2, domain2)
            .variableDomain(variable3, domain3)
            .biPredicateConstraint(variable1, variable2, (Integer v1, String v2) -> !(v1 == 1 && v2.equals("b")))
            .biPredicateConstraint(variable2, variable3, (String v2, Boolean v3) -> !(v2.equals("a") && v3))
            .build();
    AssignmentDomain assignmentDomain;

    @BeforeEach
    void setUp() {
        assignmentDomain = new AssignmentDomain(
                Map.<Variable<?>, Domain<?>>of(variable1, domain1, variable2, domain2, variable3, domain3), csp);
    }

    @Test
    void stream() {
        assertThat(assignmentDomain.stream()).containsOnly(
                Assignment.of(Map.<Variable<?>, Object>of(variable1, 1, variable2, "a", variable3, false)),
                Assignment.of(Map.<Variable<?>, Object>of(variable1, 2, variable2, "a", variable3, false)),
                Assignment.of(Map.<Variable<?>, Object>of(variable1, 2, variable2, "b", variable3, true)),
                Assignment.of(Map.<Variable<?>, Object>of(variable1, 2, variable2, "b", variable3, false))
        );
    }
}
