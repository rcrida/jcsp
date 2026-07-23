package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SetBoundsNogoodConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // --- construction ---

    @Test
    void of_createsEquivalentConstraint() {
        Variable<Set<Integer>> x = F.create("ox");
        assertThat(SetBoundsNogoodConstraint.of(Map.of(x, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2))))
                .isEqualTo(SetBoundsNogoodConstraint.of(Map.of(x, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2))));
    }

    @Test
    void of_populatesVariablesFromForbiddenMapKeys() {
        Variable<Set<Integer>> x = F.create("px"), y = F.create("py");
        var c = SetBoundsNogoodConstraint.of(Map.of(
                x, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2),
                y, SetIntervalDomain.of(Set.of(2), Set.of(2, 3), 1, 2)));
        assertThat(c.getVariables()).containsExactlyInAnyOrder(x, y);
    }

    @Test
    void of_emptyForbidden_asserts() {
        assertThatThrownBy(() -> SetBoundsNogoodConstraint.of(Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_partialAssignment_optimistic() {
        Variable<Set<Integer>> x = F.create("ix"), y = F.create("iy");
        var c = SetBoundsNogoodConstraint.of(Map.of(
                x, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3),
                y, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, Set.of(1))))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBy_everyValueInsideItsRegion_violated() {
        Variable<Set<Integer>> x = F.create("jx"), y = F.create("jy");
        var c = SetBoundsNogoodConstraint.of(Map.of(
                x, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3),
                y, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, Set.of(1), y, Set.of(2))))).isFalse();
    }

    @Test
    void isSatisfiedBy_oneValueOutsideItsRegion_satisfied() {
        Variable<Set<Integer>> x = F.create("kx"), y = F.create("ky");
        var c = SetBoundsNogoodConstraint.of(Map.of(
                x, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3),
                y, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, Set.of(1), y, Set.of(9))))).isTrue();
    }

    // --- fromCurrentBounds ---

    @Test
    void fromCurrentBounds_setVariables_citesEachOnesCurrentBoundsAndCardinality() {
        Variable<Set<Integer>> x = F.create("dx");
        var domain = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        var result = SetBoundsNogoodConstraint.fromCurrentBounds(Set.of(x), Map.of(x, domain));
        assertThat(result).contains(SetBoundsNogoodConstraint.of(Map.of(x, domain)));
    }

    @Test
    void fromCurrentBounds_nonSetBoundedVariable_returnsEmpty() {
        Variable<Integer> x = F.create("ex");
        var domains = Map.<Variable<?>, Domain<?>>of(x, DomainObjectSet.<Integer>builder().value(1).build());
        assertThat(SetBoundsNogoodConstraint.fromCurrentBounds(Set.of(x), domains)).isEmpty();
    }

    @Test
    void fromCurrentBounds_emptySetBoundedDomain_returnsEmpty() {
        // Citing an empty domain's bound/cardinality state verbatim would violate
        // SetIntervalDomain.of's own consistency asserts (minCardinality <= maxCardinality here) --
        // must degrade to empty rather than throw AssertionError.
        Variable<Set<Integer>> x = F.create("zx");
        var valid = SetIntervalDomain.of(Set.<Integer>of(), Set.of(1, 2), 0, 2);
        var empty = valid.withCardinality(3, 3);
        assertThat(empty.isEmpty()).isTrue();
        var domains = Map.<Variable<?>, Domain<?>>of(x, empty);
        assertThat(SetBoundsNogoodConstraint.fromCurrentBounds(Set.of(x), domains)).isEmpty();
    }

    // --- explainViaGroundOrBounds ---

    @Test
    void explainViaGroundOrBounds_bothSingleton_returnsGroundNogood() {
        Variable<Set<Integer>> x = F.create("fx"), y = F.create("fy");
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1),
                y, SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1));
        var reason = SetBoundsNogoodConstraint.explainViaGroundOrBounds(Set.of(x, y), domains);
        assertThat(reason).isPresent();
        assertThat(reason.get()).isInstanceOf(GroundNogoodConstraint.class);
    }

    @Test
    void explainViaGroundOrBounds_notBothSingleton_returnsSetBoundsNogood() {
        Variable<Set<Integer>> x = F.create("gx"), y = F.create("gy");
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2),
                y, SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1));
        var reason = SetBoundsNogoodConstraint.explainViaGroundOrBounds(Set.of(x, y), domains);
        assertThat(reason).isPresent();
        assertThat(reason.get()).isInstanceOf(SetBoundsNogoodConstraint.class);
    }

    // --- propagate(): satisfied literals ---

    @Test
    void propagate_emptyDomain_permanentlySatisfied() {
        Variable<Set<Integer>> x = F.create("lx");
        var region = SetIntervalDomain.of(Set.of(), Set.of(1, 2), 0, 2);
        var c = SetBoundsNogoodConstraint.of(Map.of(x, region));
        var emptyDomain = SetIntervalDomain.of(Set.of(), Set.of(1, 2), 0, 2).withCardinality(3, 3);
        var result = c.propagate(Map.of(x, emptyDomain));
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_elementForcedInDomainLowerExcludedFromRegionUpper_permanentlySatisfied() {
        Variable<Set<Integer>> x = F.create("mx");
        var region = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 0, 3);
        var c = SetBoundsNogoodConstraint.of(Map.of(x, region));
        var domain = SetIntervalDomain.of(Set.of(5), Set.of(5, 6), 1, 2);
        var result = c.propagate(Map.of(x, domain));
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_elementForcedInRegionLowerExcludedFromDomainUpper_permanentlySatisfied() {
        Variable<Set<Integer>> x = F.create("nx");
        var region = SetIntervalDomain.of(Set.of(5), Set.of(5, 6), 1, 2);
        var c = SetBoundsNogoodConstraint.of(Map.of(x, region));
        var domain = SetIntervalDomain.of(Set.of(), Set.of(1, 2), 0, 2);
        var result = c.propagate(Map.of(x, domain));
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_cardinalityRangesDisjoint_domainBelowRegion_permanentlySatisfied() {
        Variable<Set<Integer>> x = F.create("ox");
        var region = SetIntervalDomain.of(Set.of(), Set.of(1, 2), 2, 3);
        var c = SetBoundsNogoodConstraint.of(Map.of(x, region));
        var domain = SetIntervalDomain.of(Set.of(), Set.of(1, 2), 0, 1);
        var result = c.propagate(Map.of(x, domain));
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_cardinalityRangesDisjoint_domainAboveRegion_permanentlySatisfied() {
        Variable<Set<Integer>> x = F.create("px2");
        var region = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3, 4), 0, 1);
        var c = SetBoundsNogoodConstraint.of(Map.of(x, region));
        var domain = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3, 4), 3, 4);
        var result = c.propagate(Map.of(x, domain));
        assertThat(result).contains(Map.of());
    }

    // --- propagate(): falsified / infeasible ---

    @Test
    void propagate_allFalsified_infeasible() {
        // domain (non-singleton) nests entirely inside region on both bounds and cardinality --
        // falsified doesn't require singleton, unlike GroundNogoodConstraint.
        Variable<Set<Integer>> x = F.create("qx");
        var region = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        var c = SetBoundsNogoodConstraint.of(Map.of(x, region));
        var domain = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2);
        assertThat(domain.isSingleton()).isFalse();
        assertThat(c.propagate(Map.of(x, domain))).isEmpty();
    }

    // --- propagate(): exactly one undetermined ---

    @Test
    void propagate_undeterminedWithBoundsNotNested_leftUntouched() {
        Variable<Set<Integer>> x = F.create("rx"), y = F.create("ry");
        var regionY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        var domainY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2); // falsified, as above

        var regionX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 1);
        var domainX = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 1, 1); // bounds not nested (lower {} !⊇ {1})

        var c = SetBoundsNogoodConstraint.of(Map.of(x, regionX, y, regionY));
        var result = c.propagate(Map.of(x, domainX, y, domainY));
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_undeterminedWithDomainUpperEscapingRegionUpper_leftUntouched() {
        // domain's lower bound covers region's lower bound (one half of boundsNested), but
        // domain's upper bound has an element ("3") region's upper bound excludes -- the other
        // half of boundsNested, left untested by the bounds-not-nested case above.
        Variable<Set<Integer>> x = F.create("rx2"), y = F.create("ry2");
        var regionY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        var domainY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2); // falsified, as above

        var regionX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2);
        var domainX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);

        var c = SetBoundsNogoodConstraint.of(Map.of(x, regionX, y, regionY));
        var result = c.propagate(Map.of(x, domainX, y, domainY));
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_undeterminedCardinalityOverlapsLowEdge_narrowsUpPastForbiddenCardinality() {
        Variable<Set<Integer>> x = F.create("sx"), y = F.create("sy");
        var regionY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        var domainY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2); // falsified

        var regionX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3, 4, 5), 1, 3);
        var domainX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3, 4, 5), 2, 5);

        var c = SetBoundsNogoodConstraint.of(Map.of(x, regionX, y, regionY));
        var result = c.propagate(Map.of(x, domainX, y, domainY)).orElseThrow();
        assertThat(result).containsOnlyKeys(x);
        var narrowed = (SetIntervalDomain<Integer>) result.get(x);
        assertThat(narrowed.getMinCardinality()).isEqualTo(4);
        assertThat(narrowed.getMaxCardinality()).isEqualTo(5);
        assertThat(narrowed.getLowerBound()).isEqualTo(domainX.getLowerBound());
        assertThat(narrowed.getUpperBound()).isEqualTo(domainX.getUpperBound());
    }

    @Test
    void propagate_undeterminedCardinalityOverlapsHighEdge_narrowsDownBeforeForbiddenCardinality() {
        Variable<Set<Integer>> x = F.create("tx"), y = F.create("ty");
        var regionY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        var domainY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2); // falsified

        var regionX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3, 4, 5), 3, 5);
        var domainX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3, 4, 5), 1, 4);

        var c = SetBoundsNogoodConstraint.of(Map.of(x, regionX, y, regionY));
        var result = c.propagate(Map.of(x, domainX, y, domainY)).orElseThrow();
        assertThat(result).containsOnlyKeys(x);
        var narrowed = (SetIntervalDomain<Integer>) result.get(x);
        assertThat(narrowed.getMinCardinality()).isEqualTo(1);
        assertThat(narrowed.getMaxCardinality()).isEqualTo(2);
    }

    @Test
    void propagate_undeterminedCardinalityForbiddenRangeStrictlyInterior_leftUntouched() {
        Variable<Set<Integer>> x = F.create("ux"), y = F.create("uy");
        var regionY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        var domainY = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2); // falsified

        var regionX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3, 4, 5), 2, 3);
        var domainX = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3, 4, 5), 1, 5);

        var c = SetBoundsNogoodConstraint.of(Map.of(x, regionX, y, regionY));
        var result = c.propagate(Map.of(x, domainX, y, domainY));
        assertThat(result).contains(Map.of());
    }

    @Test
    void propagate_twoOrMoreUndetermined_noChange() {
        Variable<Set<Integer>> x = F.create("vx"), y = F.create("vy");
        var region = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 1);
        var domain = SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 1, 1); // bounds not nested -> undetermined
        var c = SetBoundsNogoodConstraint.of(Map.of(x, region, y, region));
        var result = c.propagate(Map.of(x, domain, y, domain));
        assertThat(result).contains(Map.of());
    }

    // --- pruneCardinality(): direct, package-visible call ---

    @Test
    void pruneCardinality_fullyNestedCardinality_returnsEmpty() {
        // classify() would report this pair FALSIFIED (bounds and cardinality both nested), so
        // propagate() never actually reaches pruneCardinality with it -- exercised directly here,
        // matching the same "extract and unit-test the unreachable-in-practice branch" approach
        // documented on the method itself.
        var region = SetIntervalDomain.of(Set.of(1), Set.of(1, 2, 3), 1, 2);
        var domain = SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2);
        assertThat(SetBoundsNogoodConstraint.pruneCardinality(domain, region)).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_returnsSelf() {
        Variable<Set<Integer>> x = F.create("wx");
        var c = SetBoundsNogoodConstraint.of(Map.of(x, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2)));
        var domain = SetIntervalDomain.of(Set.of(1), Set.of(1), 1, 1);
        assertThat(c.explainInfeasible(Map.of(x, domain))).contains(c);
    }

    // --- misc ---

    @Test
    void testToString() {
        Variable<Set<Integer>> x = F.create("xx");
        var c = SetBoundsNogoodConstraint.of(Map.of(x, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2)));
        assertThat(c.toString()).isEqualTo(
                "<(xx), nogood(xx not in [[1] subsetOf S subsetOf [1, 2], |S| in [1, 2]])>");
    }
}
