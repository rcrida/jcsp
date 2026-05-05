package org.jcsp.consistency.arc;

import lombok.val;
import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.constraints.binary.BinaryTuplesConstraint;
import org.jcsp.domains.DomainObjectSet;
import org.jcsp.domains.EnumDomain;
import org.jcsp.domains.IntRangeDomain;
import org.jcsp.constraints.binary.BinaryTuple;
import org.jcsp.solver.AustraliaMapColouringTest;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jcsp.solver.AustraliaMapColouringTest.Colour.GREEN;
import static org.jcsp.solver.AustraliaMapColouringTest.Colour.RED;
import static org.jcsp.solver.AustraliaMapColouringTest.NT;
import static org.jcsp.solver.AustraliaMapColouringTest.WA;

public class AC3Test {
    @Test
    void applyYEqualsX2() {
        val domain = IntRangeDomain.of(0, 10);
        val tuples = List.of(
                BinaryTuple.of(0, 0),
                BinaryTuple.of(1, 1),
                BinaryTuple.of(2, 4),
                BinaryTuple.of(3, 9)
        );
        val builder = ConstraintSatisfactionProblem.builder();
        val left = builder.createVariable("left", domain);
        val right = builder.createVariable("right", domain);
        builder.constraint(BinaryTuplesConstraint.builder()
                .left(left)
                .right(right)
                .binaryTuples(tuples)
                .build());
        val problem = builder.build();
        System.out.println(problem);
        val arcConstrainedProblem = AC3.INSTANCE.apply(problem).get();
        System.out.println(arcConstrainedProblem);
        assertThat(arcConstrainedProblem.getVariableDomains().get(left)).isEqualTo(DomainObjectSet.builder().values(List.of(0, 1, 2, 3)).build());
        assertThat(arcConstrainedProblem.getVariableDomains().get(right)).isEqualTo(DomainObjectSet.builder().values(List.of(0, 1, 4, 9)).build());
    }

    @Test
    void emptyProblem() {
        val builder = ConstraintSatisfactionProblem.builder();
        val problem = builder.build();
        System.out.println(problem);
        val arcConstrainedProblem = AC3.INSTANCE.apply(problem).get();
        assertThat(arcConstrainedProblem.getVariableDomains()).isEmpty();
    }

    @Test
    void singleVariableNoConstraints() {
        val domain = IntRangeDomain.of(0, 5);
        val builder = ConstraintSatisfactionProblem.builder();
        val variable = builder.createVariable("x", domain);
        builder.variableDomain(variable, domain);
        val problem = builder.build();
        System.out.println(problem);
        val arcConstrainedProblem = AC3.INSTANCE.apply(problem).get();
        assertThat(arcConstrainedProblem.getVariableDomains().get(variable)).isEqualTo(domain);
    }

    @Test
    void applyAustraliaMapColouring() {
        val problem = AustraliaMapColouringTest.problem();
        System.out.println(problem);
        val arcConstrainedProblem = AC3.INSTANCE.apply(problem).get();
        System.out.println(arcConstrainedProblem);
        arcConstrainedProblem.getVariableDomains().keySet().stream().forEach(state -> {
            assertThat(arcConstrainedProblem.getVariableDomains().get(state)).isEqualTo(AustraliaMapColouringTest.DOMAIN);
        });
    }

    @Test
    void reviseArc_revisedDomain() {
        val twoColours = new EnumDomain(EnumSet.of(RED, GREEN));
        val redOnly = new EnumDomain(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        val result = AC3.INSTANCE.revise(problem, Arc.of(WA, NT));
        assertThat(result.get().getDomain(WA).get().stream().toList()).isEqualTo(List.of(GREEN));
    }

    @Test
    void reviseArc_noRevisionNeeded() {
        val twoColours = new EnumDomain(EnumSet.of(RED, GREEN));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, twoColours)
                .notEqualsConstraint(WA, NT)
                .build();
        val result = AC3.INSTANCE.revise(problem, Arc.of(WA, NT));
        assertThat(result.get().getDomain(WA).get()).isEqualTo(twoColours);
    }

    @Test
    void reviseArc_emptyDomain() {
        val redOnly = new EnumDomain(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, redOnly)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(AC3.INSTANCE.revise(problem, Arc.of(WA, NT))).isEmpty();
    }

    @Test
    void inconsistent() {
        val domain = IntRangeDomain.of(0, 10);
        val tuples = List.of(
                BinaryTuple.of(0, 11)
        );
        val builder = ConstraintSatisfactionProblem.builder();
        val left = builder.createVariable("left", domain);
        val right = builder.createVariable("right", domain);
        builder.constraint(BinaryTuplesConstraint.builder()
                .left(left)
                .right(right)
                .binaryTuples(tuples)
                .build());
        val problem = builder.build();
        System.out.println(problem);
        assertThat(AC3.INSTANCE.apply(problem)).isEmpty();
    }
}
