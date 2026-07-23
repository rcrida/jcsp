package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.DomainObjectSet;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RangeNogoodConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // --- construction ---

    @Test
    void of_createsEquivalentConstraint() {
        Variable<Integer> x = F.create("x");
        assertThat(RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5))))
                .isEqualTo(RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5))));
    }

    @Test
    void of_populatesVariablesFromForbiddenMapKeys() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5), y, IntervalDomain.of(2, 4)));
        assertThat(c.getVariables()).containsExactlyInAnyOrder(x, y);
    }

    @Test
    void of_emptyForbidden_asserts() {
        assertThatThrownBy(() -> RangeNogoodConstraint.of(Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_partialAssignment_optimistic() {
        Variable<Integer> x = F.create("ix"), y = F.create("iy");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5), y, IntervalDomain.of(1, 5)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 3)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBy_everyValueInsideItsRange_violated() {
        Variable<Integer> x = F.create("jx"), y = F.create("jy");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5), y, IntervalDomain.of(1, 5)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 3, y, 4)))).isFalse();
    }

    @Test
    void isSatisfiedBy_oneValueOutsideItsRange_satisfied() {
        Variable<Integer> x = F.create("kx"), y = F.create("ky");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5), y, IntervalDomain.of(1, 5)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 3, y, 99)))).isTrue();
    }

    // --- propagate(): discrete domains ---

    @Test
    void propagate_discreteDomainDisjointFromRange_permanentlySatisfied() {
        Variable<Integer> x = F.create("lx");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(2, 4)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(10, 12));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_allFalsified_infeasible() {
        // Both x and y's whole (non-singleton) domains fall entirely inside their forbidden
        // ranges -- falsified doesn't require singleton, unlike GroundNogoodConstraint.
        Variable<Integer> x = F.create("mx"), y = F.create("my");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5), y, IntervalDomain.of(1, 5)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(2, 3), y, IntRangeDomain.of(2, 4));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_exactlyOneUndetermined_prunesWholeForbiddenRangeAtOnce() {
        // x overlaps its forbidden range [2,4] partially ({2,3,4} forbidden, {1,5} allowed) --
        // the only undetermined literal; y's domain is already falsified. Unlike
        // GroundNogoodConstraint (one value per step), this removes the whole overlapping range
        // {2,3,4} in a single propagation step.
        Variable<Integer> x = F.create("nx"), y = F.create("ny");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(2, 4), y, IntervalDomain.of(1, 5)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 5), y, IntRangeDomain.of(2, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(x);
        assertThat(result.get().get(x)).isEqualTo(
                IntRangeDomain.of(1, 5).toBuilder().delete(2).delete(3).delete(4).build());
    }

    @Test
    void propagate_twoOrMoreUndetermined_noChange() {
        Variable<Integer> x = F.create("px"), y = F.create("py");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(2, 4), y, IntervalDomain.of(2, 4)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 5), y, IntRangeDomain.of(1, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- propagate(): bounded (continuous) domains ---

    @Test
    void propagate_boundedDomainBelowRange_permanentlySatisfied() {
        // domain entirely below the forbidden range: domainMax < range.min() short-circuits true.
        Variable<Double> x = F.create("qx");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(5.0, 10.0)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntervalDomain.of(0.0, 3.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_boundedDomainAboveRange_permanentlySatisfied() {
        // domain entirely above the forbidden range: domainMax >= range.min() (left operand
        // false), so this exercises the right-hand domainMin > range.max() operand instead.
        Variable<Double> x = F.create("q2x");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(0.0, 5.0)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntervalDomain.of(10.0, 15.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_boundedDomainFullyInsideRange_falsifiedWithoutBeingSingleton() {
        Variable<Double> x = F.create("rx");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(0.0, 10.0)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntervalDomain.of(3.0, 4.0));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_boundedDomainOverlapsLowEdge_narrowsUpPastForbiddenRange() {
        // x's domain [0,10] overlaps its forbidden range [-5,3] at the low edge; y is falsified.
        Variable<Double> x = F.create("sx"), y = F.create("sy");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(-5.0, 3.0), y, IntervalDomain.of(0.0, 10.0)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntervalDomain.of(0.0, 10.0), y, IntervalDomain.of(2.0, 4.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(x);
        assertThat(result.get().get(x)).isEqualTo(IntervalDomain.of(3.0, 10.0));
    }

    @Test
    void propagate_boundedDomainOverlapsHighEdge_narrowsDownBeforeForbiddenRange() {
        // x's domain [0,10] overlaps its forbidden range [7,20] at the high edge; y is falsified.
        Variable<Double> x = F.create("tx"), y = F.create("ty");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(7.0, 20.0), y, IntervalDomain.of(0.0, 10.0)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntervalDomain.of(0.0, 10.0), y, IntervalDomain.of(2.0, 4.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(x);
        assertThat(result.get().get(x)).isEqualTo(IntervalDomain.of(0.0, 7.0));
    }

    @Test
    void propagate_boundedDomainForbiddenRangeStrictlyInterior_leftUntouched() {
        // x's forbidden range [3,7] is a "hole" strictly inside its domain [0,10] -- can't be
        // represented as a single narrowed interval, so no narrowing is attempted even though
        // this is the sole undetermined literal.
        Variable<Double> x = F.create("ux"), y = F.create("uy");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(3.0, 7.0), y, IntervalDomain.of(0.0, 10.0)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntervalDomain.of(0.0, 10.0), y, IntervalDomain.of(2.0, 4.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- fromCurrentBounds ---

    @Test
    void fromCurrentBounds_discreteVariables_citesEachOnesFullCurrentRange() {
        Variable<Integer> x = F.create("dx"), y = F.create("dy");
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(1, 3), y, IntRangeDomain.of(4, 4));
        var result = RangeNogoodConstraint.fromCurrentBounds(Set.of(x, y), domains);
        assertThat(result).contains(RangeNogoodConstraint.of(
                Map.of(x, IntervalDomain.of(1.0, 3.0), y, IntervalDomain.of(4.0, 4.0))));
    }

    @Test
    void fromCurrentBounds_boundedVariable_citesItsCurrentBounds() {
        Variable<Double> x = F.create("bx");
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntervalDomain.of(2.0, 5.0));
        var result = RangeNogoodConstraint.fromCurrentBounds(Set.of(x), domains);
        assertThat(result).contains(RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(2.0, 5.0))));
    }

    @Test
    void fromCurrentBounds_nonNumericVariable_returnsEmpty() {
        Variable<String> x = F.create("sx");
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, DomainObjectSet.<String>builder().value("a").value("b").build());
        assertThat(RangeNogoodConstraint.fromCurrentBounds(Set.of(x), domains)).isEmpty();
    }

    @Test
    void fromCurrentBounds_emptyDomain_returnsEmpty() {
        Variable<Integer> x = F.create("ex");
        var domains = Map.<Variable<?>, Domain<?>>of(x, DomainObjectSet.<Integer>builder().build());
        assertThat(RangeNogoodConstraint.fromCurrentBounds(Set.of(x), domains)).isEmpty();
    }

    @Test
    void fromCurrentBounds_setBoundedVariable_returnsEmpty() {
        // Neither BoundedDomain nor DiscreteDomain -- a SetBoundedDomain isn't Number-based, so no
        // IntervalDomain could stand in for it at all; must degrade to empty rather than crash.
        Variable<Set<Integer>> x = F.create("hx");
        var domains = Map.<Variable<?>, Domain<?>>of(x, SetIntervalDomain.of(Set.of(1), Set.of(1, 2), 1, 2));
        assertThat(RangeNogoodConstraint.fromCurrentBounds(Set.of(x), domains)).isEmpty();
    }

    @Test
    void fromCurrentBounds_gappedDiscreteDomain_returnsEmpty() {
        // {1,5} bounds to [1,5], but 2/3/4 were never actually part of the domain that caused the
        // original infeasibility -- citing the interval here would be unsound (see the javadoc on
        // isSafeToCiteAsRange / fromCurrentBounds), so this must degrade to empty rather than
        // producing a RangeNogoodConstraint over a superset of the real domain.
        Variable<Integer> x = F.create("gx");
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, DomainObjectSet.<Integer>builder().value(1).value(5).build());
        assertThat(RangeNogoodConstraint.fromCurrentBounds(Set.of(x), domains)).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_everyVariableSingleton_returnsSelf() {
        Variable<Integer> x = F.create("vx"), y = F.create("vy");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5), y, IntervalDomain.of(1, 5)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(3, 3), y, IntRangeDomain.of(4, 4));
        assertThat(c.explainInfeasible(domains)).contains(c);
    }

    @Test
    void explainInfeasible_notEveryVariableSingleton_stillReturnsSelf() {
        // No singleton requirement is needed: the clause is falsified (both domains entirely
        // within their forbidden ranges) regardless of whether either domain is a single point.
        Variable<Integer> x = F.create("wx"), y = F.create("wy");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5), y, IntervalDomain.of(1, 5)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(2, 3), y, IntRangeDomain.of(4, 4));
        assertThat(c.explainInfeasible(domains)).contains(c);
    }

    // --- misc ---

    @Test
    void testToString() {
        Variable<Integer> x = F.create("rx");
        var c = RangeNogoodConstraint.of(Map.of(x, IntervalDomain.of(1, 5)));
        assertThat(c.toString()).isEqualTo("<(rx), nogood(rx not in [1.0, 5.0])>");
    }
}
