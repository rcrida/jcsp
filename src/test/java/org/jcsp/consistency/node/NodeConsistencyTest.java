package org.jcsp.consistency.node;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.UnaryConstraint;
import org.jcsp.domains.DomainObjectSet;
import org.jcsp.domains.EnumDomain;
import org.jcsp.relations.UnaryNotEqualsRelation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeConsistencyTest {
    enum Colour {
        RED, GREEN, BLUE
    }

    @Test
    void applyAustraliaMapColouring() {
        val domain = EnumDomain.allOf(Colour.class);
        val builder = ConstraintSatisfactionProblem.builder();
        val SA = builder.createVariable("SA", domain);
        builder
                .constraint(UnaryConstraint.of(SA, UnaryNotEqualsRelation.builder().variable(SA).value(Colour.GREEN).build()));
        val problem = builder.build();
        System.out.println(problem);
        val arcConstrainedProblem = NodeConsistency.INSTANCE.apply(problem).get();
        System.out.println(arcConstrainedProblem);
        assertThat(arcConstrainedProblem.getVariableDomains().get(SA)).isEqualTo(DomainObjectSet.builder().values(List.of(Colour.BLUE, Colour.RED)).build());
    }

    @Test
    void inconsistentProblem() {
        val domain = EnumDomain.allOf(Colour.class);
        val builder = ConstraintSatisfactionProblem.builder();
        val SA = builder.createVariable("SA", domain);
        builder
                .constraint(UnaryConstraint.of(SA, UnaryNotEqualsRelation.builder().variable(SA).value(Colour.RED).build()))
                .constraint(UnaryConstraint.of(SA, UnaryNotEqualsRelation.builder().variable(SA).value(Colour.GREEN).build()))
                .constraint(UnaryConstraint.of(SA, UnaryNotEqualsRelation.builder().variable(SA).value(Colour.BLUE).build()));
        val problem = builder.build();
        System.out.println(problem);
        assertThat(NodeConsistency.INSTANCE.apply(problem)).isEmpty();
    }
}
