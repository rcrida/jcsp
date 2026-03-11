package org.jcsp.consistency.arc;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.BinaryConstraint;
import org.jcsp.constraints.ExpressionConstraint;
import org.jcsp.domains.EnumDomain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.relations.BinaryTuple;
import org.jcsp.relations.BinaryTuplesRelation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

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
        val arcConstrainedProblem = AC3.INSTANCE.apply(problem);
        System.out.println(arcConstrainedProblem.get());
    }

    enum Colour {
        RED, GREEN, BLUE
    }

    @Test
    void applyAustraliaMapColouring() {
        val domain = EnumDomain.allOf(Colour.class);
        val coloursNotEqual = List.of(
                BinaryTuple.of(Colour.RED, Colour.GREEN),
                BinaryTuple.of(Colour.RED, Colour.BLUE),
                BinaryTuple.of(Colour.GREEN, Colour.RED),
                BinaryTuple.of(Colour.GREEN, Colour.BLUE),
                BinaryTuple.of(Colour.BLUE, Colour.RED),
                BinaryTuple.of(Colour.BLUE, Colour.GREEN)
        );
        val builder = ConstraintSatisfactionProblem.builder();
        val WA = builder.createVariable("WA", domain);
        val NT = builder.createVariable("NT", domain);
        val Q = builder.createVariable("Q", domain);
        val NSW = builder.createVariable("NSW", domain);
        val V = builder.createVariable("V", domain);
        val SA = builder.createVariable("SA", domain);
        val T = builder.createVariable("T", domain);
        builder.constraint(BinaryConstraint.of(SA, WA, BinaryTuplesRelation.builder()
                .left(SA)
                .right(WA)
                .binaryTuples(coloursNotEqual)
                .build()));
        val problem = builder.build();
        System.out.println(problem);
        val arcConstrainedProblem = AC3.INSTANCE.apply(problem);
        System.out.println(arcConstrainedProblem.get());
    }
}
