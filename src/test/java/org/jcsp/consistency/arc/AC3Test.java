package org.jcsp.consistency.arc;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.BinaryConstraint;
import org.jcsp.domains.DomainObjectSet;
import org.jcsp.domains.EnumDomain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.relations.BinaryNotEqualsRelation;
import org.jcsp.relations.BinaryTuple;
import org.jcsp.relations.BinaryTuplesRelation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AC3Test {
    @Test
    void applyYEqualsX2() {
        val domain = new IntRangeDomain(0, 10);
        val tuples = List.of(
                BinaryTuple.of(0, 0),
                BinaryTuple.of(1, 1),
                BinaryTuple.of(2, 4),
                BinaryTuple.of(3, 9)
        );
        val builder = ConstraintSatisfactionProblem.builder();
        val left = builder.createVariable("left", domain);
        val right = builder.createVariable("right", domain);
        builder.constraint(BinaryConstraint.of(left, right, BinaryTuplesRelation.builder()
                .left(left)
                .right(right)
                .binaryTuples(tuples)
                .build()));
        val problem = builder.build();
        System.out.println(problem);
        val arcConstrainedProblem = AC3.INSTANCE.apply(problem).get();
        System.out.println(arcConstrainedProblem);
        assertThat(arcConstrainedProblem.getVariableDomains().get(left)).isEqualTo(DomainObjectSet.builder().values(List.of(0, 1, 2, 3)).build());
        assertThat(arcConstrainedProblem.getVariableDomains().get(right)).isEqualTo(DomainObjectSet.builder().values(List.of(0, 1, 4, 9)).build());
    }

    enum Colour {
        RED, GREEN, BLUE
    }

    @Test
    void applyAustraliaMapColouring() {
        val domain = EnumDomain.allOf(Colour.class);
        val builder = ConstraintSatisfactionProblem.builder();
        val WA = builder.createVariable("WA", domain);
        val NT = builder.createVariable("NT", domain);
        val Q = builder.createVariable("Q", domain);
        val NSW = builder.createVariable("NSW", domain);
        val V = builder.createVariable("V", domain);
        val SA = builder.createVariable("SA", domain);
        val T = builder.createVariable("T", domain);
        builder
                .constraint(BinaryConstraint.of(SA, WA, BinaryNotEqualsRelation.builder().left(SA).right(WA).build()))
                .constraint(BinaryConstraint.of(SA, NT, BinaryNotEqualsRelation.builder().left(SA).right(NT).build()))
                .constraint(BinaryConstraint.of(SA, Q, BinaryNotEqualsRelation.builder().left(SA).right(Q).build()))
                .constraint(BinaryConstraint.of(SA, NSW, BinaryNotEqualsRelation.builder().left(SA).right(NSW).build()))
                .constraint(BinaryConstraint.of(SA, V, BinaryNotEqualsRelation.builder().left(SA).right(V).build()))
                .constraint(BinaryConstraint.of(WA, NT, BinaryNotEqualsRelation.builder().left(WA).right(NT).build()))
                .constraint(BinaryConstraint.of(NT, Q, BinaryNotEqualsRelation.builder().left(NT).right(Q).build()))
                .constraint(BinaryConstraint.of(Q, NSW, BinaryNotEqualsRelation.builder().left(Q).right(NSW).build()))
                .constraint(BinaryConstraint.of(NSW, V, BinaryNotEqualsRelation.builder().left(NSW).right(V).build()));
        val problem = builder.build();
        System.out.println(problem);
        val arcConstrainedProblem = AC3.INSTANCE.apply(problem).get();
        System.out.println(arcConstrainedProblem);
        arcConstrainedProblem.getVariableDomains().keySet().stream().forEach(state -> {
            assertThat(arcConstrainedProblem.getVariableDomains().get(state)).isEqualTo(domain);
        });
    }

    @Test
    void inconsistent() {
        val domain = new IntRangeDomain(0, 10);
        val tuples = List.of(
                BinaryTuple.of(0, 11)
        );
        val builder = ConstraintSatisfactionProblem.builder();
        val left = builder.createVariable("left", domain);
        val right = builder.createVariable("right", domain);
        builder.constraint(BinaryConstraint.of(left, right, BinaryTuplesRelation.builder()
                .left(left)
                .right(right)
                .binaryTuples(tuples)
                .build()));
        val problem = builder.build();
        System.out.println(problem);
        assertThat(AC3.INSTANCE.apply(problem)).isEmpty();
    }
}
