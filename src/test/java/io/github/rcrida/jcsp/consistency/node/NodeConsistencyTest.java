package io.github.rcrida.jcsp.consistency.node;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
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
    void explainConflict_usesInheritedDefault_returnsEmpty() {
        // NodeConsistency doesn't override explainConflict, unlike AC3/FixpointConsistency -- this
        // keeps ConstraintConsistency's default implementation itself covered (Optional.empty()),
        // since no other ConstraintConsistency implementor falls back to it any more.
        val domain = EnumDomain.allOf(Colour.class);
        val builder = ConstraintSatisfactionProblem.builder();
        val SA = builder.createVariable("SA", domain);
        builder
                .notEqualsConstraint(SA, Colour.RED)
                .notEqualsConstraint(SA, Colour.GREEN)
                .notEqualsConstraint(SA, Colour.BLUE);
        val problem = builder.build();
        assertThat(NodeConsistency.INSTANCE.explainConflict(problem)).isEmpty();
    }

    @Test
    void applyWithReason_usesInheritedDefault_feasibleDelegatesToApply() {
        // NodeConsistency doesn't override applyWithReason either -- ConstraintConsistency's
        // default (apply, then map to a feasible ConsistencyResult) is what runs here.
        val domain = new EnumDomain<>(EnumSet.of(Colour.RED, Colour.GREEN));
        val builder = ConstraintSatisfactionProblem.builder();
        val WA = builder.createVariable("WA", domain);
        builder.notEqualsConstraint(WA, Colour.BLUE);
        val problem = builder.build();
        var result = NodeConsistency.INSTANCE.applyWithReason(problem, null);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.problem()).isEqualTo(NodeConsistency.INSTANCE.apply(problem).orElseThrow());
    }

    @Test
    void applyWithReason_usesInheritedDefault_infeasibleFallsBackToExplainConflict() {
        // On failure, ConstraintConsistency's default delegates to explainConflict -- which
        // NodeConsistency also doesn't override, so the reason is always null here.
        val domain = EnumDomain.allOf(Colour.class);
        val builder = ConstraintSatisfactionProblem.builder();
        val SA = builder.createVariable("SA", domain);
        builder
                .notEqualsConstraint(SA, Colour.RED)
                .notEqualsConstraint(SA, Colour.GREEN)
                .notEqualsConstraint(SA, Colour.BLUE);
        val problem = builder.build();
        var result = NodeConsistency.INSTANCE.applyWithReason(problem, null);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    void noRevisionsRequired() {
        val domain = new EnumDomain<>(EnumSet.of(Colour.RED, Colour.GREEN));
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
    void intervalDomain_skippedByNodeConsistency() {
        // UnaryComparatorConstraint on an IntervalDomain variable: NodeConsistency skips it
        // (leaves it to FixpointConsistency); the domain should be unchanged.
        Variable<Double> x = Variable.Factory.INSTANCE.create("x_nc");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .comparatorConstraint(x, Operator.GEQ, 3.0)
                .build();
        var result = NodeConsistency.INSTANCE.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().getDomain(x)).isEqualTo(IntervalDomain.of(0.0, 10.0));
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
