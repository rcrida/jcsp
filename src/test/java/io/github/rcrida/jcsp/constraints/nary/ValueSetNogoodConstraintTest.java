package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ValueSetNogoodConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    // --- construction ---

    @Test
    void of_createsEquivalentConstraint() {
        Variable<Integer> x = F.create("x");
        assertThat(ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 2, 3))))
                .isEqualTo(ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 2, 3))));
    }

    @Test
    void of_populatesVariablesFromForbiddenMapKeys() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 2), y, Set.of(3, 4)));
        assertThat(c.getVariables()).containsExactlyInAnyOrder(x, y);
    }

    @Test
    void of_emptyForbidden_asserts() {
        assertThatThrownBy(() -> ValueSetNogoodConstraint.of(Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_partialAssignment_optimistic() {
        Variable<Integer> x = F.create("ix"), y = F.create("iy");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 2, 3), y, Set.of(1, 2, 3)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 2)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBy_everyValueInsideItsSet_violated() {
        Variable<Integer> x = F.create("jx"), y = F.create("jy");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 2, 3), y, Set.of(4, 5)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 2, y, 4)))).isFalse();
    }

    @Test
    void isSatisfiedBy_oneValueOutsideItsSet_satisfied() {
        Variable<Integer> x = F.create("kx"), y = F.create("ky");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 2, 3), y, Set.of(4, 5)));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 2, y, 99)))).isTrue();
    }

    // --- propagate ---

    @Test
    void propagate_discreteDomainDisjointFromForbiddenSet_permanentlySatisfied() {
        Variable<Integer> x = F.create("lx");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(2, 4)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(10, 12));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_allFalsified_infeasible() {
        // Both x and y's whole (non-singleton) domains fall entirely inside their forbidden sets --
        // falsified doesn't require singleton, same as RangeNogoodConstraint.
        Variable<Integer> x = F.create("mx"), y = F.create("my");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 2, 3), y, Set.of(4, 5)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 3), y, IntRangeDomain.of(4, 5));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_exactlyOneUndetermined_prunesForbiddenValuesAtOnce() {
        // x overlaps its forbidden set {2,4} partially ({2,4} forbidden, {1,3,5} allowed) -- the
        // only undetermined literal; y's domain is already falsified. Unlike GroundNogoodConstraint
        // (one value per step), this removes every forbidden value in a single propagation step.
        Variable<Integer> x = F.create("nx"), y = F.create("ny");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(2, 4), y, Set.of(4, 5)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 5), y, IntRangeDomain.of(4, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(x);
        assertThat(result.get().get(x)).isEqualTo(
                IntRangeDomain.of(1, 5).toBuilder().delete(2).delete(4).build());
    }

    @Test
    void propagate_twoOrMoreUndetermined_noChange() {
        Variable<Integer> x = F.create("px"), y = F.create("py");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(2, 4), y, Set.of(2, 4)));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 5), y, IntRangeDomain.of(1, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_nonDiscreteUndeterminedDomain_noChangeAndNotInfeasible() {
        // A BoundedDomain always classifies as UNDETERMINED (no meaningful "entirely within a
        // discrete set" for a continuous domain) -- the sole undetermined literal here, but the
        // pruning guard requires DiscreteDomain, so this degrades to a safe no-op rather than an
        // invalid cast.
        Variable<Double> x = F.create("qx");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(2.0, 4.0)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntervalDomain.of(0.0, 10.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- fromCurrentValues ---

    @Test
    void fromCurrentValues_discreteVariables_citesEachOnesExactCurrentSetEvenWithGaps() {
        Variable<Integer> x = F.create("dx"), y = F.create("dy");
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, DiscreteDomain.of(1, 5), y, IntRangeDomain.of(4, 4));
        var result = ValueSetNogoodConstraint.fromCurrentValues(Set.of(x, y), domains);
        assertThat(result).contains(ValueSetNogoodConstraint.of(
                Map.of(x, Set.of(1, 5), y, Set.of(4))));
    }

    @Test
    void fromCurrentValues_nonDiscreteVariable_returnsEmpty() {
        Variable<Double> x = F.create("bx");
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntervalDomain.of(2.0, 5.0));
        assertThat(ValueSetNogoodConstraint.fromCurrentValues(Set.of(x), domains)).isEmpty();
    }

    // --- fromCurrentState ---

    @Test
    void fromCurrentState_allSingleton_returnsTighterGroundNogoodConstraint() {
        Variable<Integer> x = F.create("gx"), y = F.create("gy");
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(3, 3), y, IntRangeDomain.of(4, 4));
        var result = ValueSetNogoodConstraint.fromCurrentState(Set.of(x, y), domains);
        assertThat(result).contains(GroundNogoodConstraint.of(Map.of(x, 3, y, 4)));
    }

    @Test
    void fromCurrentState_notAllSingletonButAllDiscrete_returnsValueSetNogoodConstraint() {
        Variable<Integer> x = F.create("hx"), y = F.create("hy");
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, DiscreteDomain.of(1, 5), y, IntRangeDomain.of(4, 4));
        var result = ValueSetNogoodConstraint.fromCurrentState(Set.of(x, y), domains);
        assertThat(result).contains(ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 5), y, Set.of(4))));
    }

    @Test
    void fromCurrentState_nonDiscreteVariable_returnsEmpty() {
        Variable<Double> x = F.create("ix");
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntervalDomain.of(2.0, 5.0));
        assertThat(ValueSetNogoodConstraint.fromCurrentState(Set.of(x), domains)).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_returnsSelf() {
        Variable<Integer> x = F.create("jx2");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 2)));
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(1, 2));
        assertThat(c.explainInfeasible(domains)).contains(c);
    }

    // --- misc ---

    @Test
    void testToString() {
        Variable<Integer> x = F.create("rx");
        var c = ValueSetNogoodConstraint.of(Map.of(x, Set.of(1, 5)));
        assertThat(c.toString()).startsWith("<(rx), nogood(rx not in ").contains("1").contains("5");
    }
}
