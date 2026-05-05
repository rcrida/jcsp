package io.github.rcrida.jcsp.consistency.node;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.EnumDomain;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
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
                .notEqualsConstraint(SA, Colour.GREEN);
        val problem = builder.build();
        System.out.println(problem);
        val arcConstrainedProblem = NodeConsistency.INSTANCE.apply(problem).get();
        System.out.println(arcConstrainedProblem);
        assertThat(arcConstrainedProblem.getVariableDomains().get(SA)).isEqualTo(DomainObjectSet.builder().values(List.of(Colour.BLUE, Colour.RED)).build());
    }

    @Test
    void domainBecomesEmpty() {
        val domain = EnumDomain.allOf(Colour.class);
        val builder = ConstraintSatisfactionProblem.builder();
        val SA = builder.createVariable("SA", domain);
        builder
                .notEqualsConstraint(SA, Colour.RED)
                .notEqualsConstraint(SA, Colour.GREEN)
                .notEqualsConstraint(SA, Colour.BLUE);
        val problem = builder.build();
        System.out.println(problem);
        assertThat(NodeConsistency.INSTANCE.apply(problem)).isEmpty();
    }

    @Test
    void noRevisionsRequired() {
        val domain = new EnumDomain(EnumSet.of(Colour.RED, Colour.GREEN));
        val builder = ConstraintSatisfactionProblem.builder();
        val WA = builder.createVariable("WA", domain);
        builder.notEqualsConstraint(WA, Colour.BLUE);
        val problem = builder.build();
        assertThat(NodeConsistency.INSTANCE.apply(problem))
                .isPresent()
                .hasValueSatisfying(updatedProblem ->
                        assertThat(updatedProblem.getVariableDomains().get(WA)).isEqualTo(domain));
    }

    @Test
    void multipleVariablesWithoutInconsistencies() {
        val domain = EnumDomain.allOf(Colour.class);
        val builder = ConstraintSatisfactionProblem.builder();
        val WA = builder.createVariable("WA", domain);
        val NT = builder.createVariable("NT", domain);
        builder
                .notEqualsConstraint(WA, Colour.GREEN)
                .notEqualsConstraint(NT, Colour.RED);
        val problem = builder.build();
        val result = NodeConsistency.INSTANCE.apply(problem);
        assertThat(result).isPresent();
        val updatedProblem = result.get();
        assertThat(updatedProblem.getVariableDomains().get(WA)).isEqualTo(
                DomainObjectSet.builder().values(List.of(Colour.RED, Colour.BLUE)).build());
        assertThat(updatedProblem.getVariableDomains().get(NT)).isEqualTo(
                DomainObjectSet.builder().values(List.of(Colour.GREEN, Colour.BLUE)).build());
    }
}
