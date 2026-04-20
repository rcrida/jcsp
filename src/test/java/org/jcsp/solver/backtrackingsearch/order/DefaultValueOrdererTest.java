package org.jcsp.solver.backtrackingsearch.order;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.assignments.Assignment;
import org.jcsp.domains.Domain;
import org.jcsp.domains.DomainObjectSet;
import org.jcsp.solver.backtrackingsearch.order.DefaultValueOrderer;
import org.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultValueOrdererTest {
    static Variable.Factory FACTORY = Variable.Factory.INSTANCE;
    static Domain DOMAIN = DomainObjectSet.builder().value(1).value(2).value(3).build();
    static Variable A = FACTORY.create("A");
    static Variable B = FACTORY.create("B");
    static ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(A, DOMAIN)
            .build();

    @Test
    void order() {
        assertThat(DefaultValueOrderer.INSTANCE.order(CSP, A, Assignment.EMPTY).toList()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void order_unknownVariable() {
        assertThat(DefaultValueOrderer.INSTANCE.order(CSP, B, Assignment.EMPTY)).isEmpty();
    }
}
