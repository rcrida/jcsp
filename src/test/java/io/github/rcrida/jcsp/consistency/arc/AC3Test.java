package io.github.rcrida.jcsp.consistency.arc;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.EnumDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.constraints.binary.BinaryTuple;
import io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest.Colour.GREEN;
import static io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest.Colour.RED;
import static io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest.NT;
import static io.github.rcrida.jcsp.solver.examples.AustraliaMapColouringTest.WA;

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
        Variable<Object> left = Variable.Factory.INSTANCE.create("left");
        Variable<Object> right = Variable.Factory.INSTANCE.create("right");
        builder.variableDomainEntry(left, domain);
        builder.variableDomainEntry(right, domain);
        builder.constraint(BinaryTuplesConstraint.of(left, right, Set.copyOf(tuples)));
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
        val twoColours = new EnumDomain<>(EnumSet.of(RED, GREEN));
        val redOnly = new EnumDomain<>(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        val result = AC3.INSTANCE.revise(problem, Arc.of(WA, NT));
        assertThat(((DiscreteDomain<?>) result.get().getDomain(WA)).toList()).isEqualTo(List.of(GREEN));
    }

    @Test
    void reviseArcConstraint_threeArgOverload_revisedDomain() {
        // Same scenario as reviseArc_revisedDomain, but exercising the public 3-arg revise(problem,
        // arc, constraint) overload directly, which delegates to the Map-based private helper that
        // applyQueue/explainConflict call with their own progressively-narrowed domains instead.
        val twoColours = new EnumDomain<>(EnumSet.of(RED, GREEN));
        val redOnly = new EnumDomain<>(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        val result = AC3.INSTANCE.revise(problem, Arc.of(WA, NT), BinaryNotEqualsConstraint.of(WA, NT));
        assertThat(result.get().toList()).isEqualTo(List.of(GREEN));
    }

    @Test
    void reviseArc_noRevisionNeeded() {
        val twoColours = new EnumDomain<>(EnumSet.of(RED, GREEN));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, twoColours)
                .notEqualsConstraint(WA, NT)
                .build();
        val result = AC3.INSTANCE.revise(problem, Arc.of(WA, NT));
        assertThat(result.get().getDomain(WA)).isEqualTo(twoColours);
    }

    @Test
    void reviseArc_emptyDomain() {
        val redOnly = new EnumDomain<>(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, redOnly)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(AC3.INSTANCE.revise(problem, Arc.of(WA, NT))).isEmpty();
    }

    @Test
    void intervalDomain_fromNotDiscrete_skippedByAC3() {
        // Both IntervalDomain: AC3 skips the arc at the first instanceof check (from is not discrete).
        Variable<Double> x = Variable.Factory.INSTANCE.create("x_ac3a");
        Variable<Double> y = Variable.Factory.INSTANCE.create("y_ac3a");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntervalDomain.of(0.0, 10.0))
                .variableDomain(y, IntervalDomain.of(0.0, 10.0))
                .comparatorConstraint(x, Operator.LEQ, y)
                .build();
        var result = AC3.INSTANCE.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().getDomain(x)).isEqualTo(IntervalDomain.of(0.0, 10.0));
        assertThat(result.get().getDomain(y)).isEqualTo(IntervalDomain.of(0.0, 10.0));
    }

    @Test
    void intervalDomain_toNotDiscrete_skippedByAC3() {
        // Mixed: discrete Double from-variable, IntervalDomain to-variable.
        // AC3 passes the first instanceof check (from IS discrete) but skips at the second (to is not).
        Variable<Double> d = Variable.Factory.INSTANCE.create("d_ac3b");
        Variable<Double> c = Variable.Factory.INSTANCE.create("c_ac3b");
        var discreteDoubleDomain = DomainObjectSet.<Double>builder().value(2.0).value(5.0).build();
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(d, discreteDoubleDomain)
                .variableDomain(c, IntervalDomain.of(0.0, 10.0))
                .comparatorConstraint(d, Operator.LEQ, c)
                .build();
        var result = AC3.INSTANCE.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().getDomain(d)).isEqualTo(discreteDoubleDomain);
        assertThat(result.get().getDomain(c)).isEqualTo(IntervalDomain.of(0.0, 10.0));
    }

    @Test
    void explainConflict_bothSidesSingleton_returnsSoundReason() {
        // Same setup as reviseArc_emptyDomain: WA and NT both fixed to RED, notEquals violated.
        // Both sides singleton -> the pair alone is a sound, structural reason.
        val redOnly = new EnumDomain<>(EnumSet.of(RED));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, redOnly)
                .variableDomain(NT, redOnly)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(AC3.INSTANCE.explainConflict(problem)).contains(GroundNogoodConstraint.of(Map.of(WA, RED, NT, RED)));
    }

    @Test
    void explainConflict_neitherSideSingleton_returnsEmptyOptional() {
        // Same setup as inconsistent(): both domains have 11 values, none of which can ever
        // satisfy the sole tuple (0,11) since 11 isn't even in either domain. AC3 still wipes a
        // domain, but since neither side is singleton, no sound reason can be cited -- deliberately
        // ground-only (see AC3#explainConflict's javadoc for why it doesn't fall back to a
        // range-based nogood the way FixpointConsistency does), so this reports Optional.empty()
        // (a GroundNogoodConstraint can't be built from an empty reason map), signalling the caller
        // to fall back to the full assignment -- same observable shape as no wipeout at all.
        val domain = IntRangeDomain.of(0, 10);
        val tuples = List.of(BinaryTuple.of(0, 11));
        val builder = ConstraintSatisfactionProblem.builder();
        Variable<Object> left = Variable.Factory.INSTANCE.create("left_ec");
        Variable<Object> right = Variable.Factory.INSTANCE.create("right_ec");
        builder.variableDomainEntry(left, domain);
        builder.variableDomainEntry(right, domain);
        builder.constraint(BinaryTuplesConstraint.of(left, right, Set.copyOf(tuples)));
        val problem = builder.build();
        assertThat(AC3.INSTANCE.explainConflict(problem)).isEmpty();
    }

    @Test
    void explainConflict_noWipeout_returnsEmptyOptional() {
        // Same setup as reviseArc_noRevisionNeeded: no domain is ever wiped, so explainConflict
        // must report no conflict at all (Optional.empty()), not just an empty reason.
        val twoColours = new EnumDomain<>(EnumSet.of(RED, GREEN));
        val problem = ConstraintSatisfactionProblem.builder()
                .variableDomain(WA, twoColours)
                .variableDomain(NT, twoColours)
                .notEqualsConstraint(WA, NT)
                .build();
        assertThat(AC3.INSTANCE.explainConflict(problem)).isEmpty();
    }

    @Test
    void inconsistent() {
        val domain = IntRangeDomain.of(0, 10);
        val tuples = List.of(
                BinaryTuple.of(0, 11)
        );
        val builder = ConstraintSatisfactionProblem.builder();
        Variable<Object> left = Variable.Factory.INSTANCE.create("left");
        Variable<Object> right = Variable.Factory.INSTANCE.create("right");
        builder.variableDomainEntry(left, domain);
        builder.variableDomainEntry(right, domain);
        builder.constraint(BinaryTuplesConstraint.of(left, right, Set.copyOf(tuples)));
        val problem = builder.build();
        System.out.println(problem);
        assertThat(AC3.INSTANCE.apply(problem)).isEmpty();
    }
}
