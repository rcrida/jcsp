package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GroundNogoodConstraintTest {
    final Variable.Factory F = new Variable.Factory();

    // --- construction ---

    @Test
    void of_createsEquivalentConstraint() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        assertThat(GroundNogoodConstraint.of(Map.of(x, 1, y, 2)))
                .isEqualTo(GroundNogoodConstraint.of(Map.of(x, 1, y, 2)));
    }

    @Test
    void of_populatesVariablesFromForbiddenMapKeys() {
        Variable<Integer> x = F.create("x"), y = F.create("y");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        assertThat(c.getVariables()).containsExactlyInAnyOrder(x, y);
    }

    @Test
    void of_emptyForbidden_asserts() {
        assertThatThrownBy(() -> GroundNogoodConstraint.of(Map.of()))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_partialAssignment_optimistic() {
        Variable<Integer> x = F.create("ix"), y = F.create("iy");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 1)))).isTrue();
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBy_exactMatch_violated() {
        Variable<Integer> x = F.create("jx"), y = F.create("jy");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 2)))).isFalse();
    }

    @Test
    void isSatisfiedBy_oneValueDiffers_satisfied() {
        Variable<Integer> x = F.create("kx"), y = F.create("ky");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 3)))).isTrue();
    }

    // --- propagate() ---

    @Test
    void propagate_oneDomainAlreadyExcludesForbiddenValue_permanentlySatisfied() {
        // y's domain never contains 2 at all: this literal is guaranteed true regardless of x.
        Variable<Integer> x = F.create("lx"), y = F.create("ly");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 1), y, IntRangeDomain.of(5, 6));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_allSingletonMatchingForbidden_infeasible() {
        Variable<Integer> x = F.create("mx"), y = F.create("my");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 1), y, IntRangeDomain.of(2, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_exactlyOneUndetermined_prunesForbiddenValueFromIt() {
        // x is singleton at its forbidden value (falsified); y is still open and contains its
        // forbidden value (undetermined) -- the only way to keep the clause satisfiable is for y
        // to avoid 2, so propagation prunes it.
        Variable<Integer> x = F.create("nx"), y = F.create("ny");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 1), y, IntRangeDomain.of(1, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).containsOnlyKeys(y);
        assertThat(result.get().get(y)).isEqualTo(IntRangeDomain.of(1, 3).toBuilder().delete(2).build());
    }

    @Test
    void propagate_exactlyOneUndeterminedButBoundedDomain_leftUntouched() {
        // z (IntervalDomain, non-singleton, continuous) is the sole undetermined literal, but
        // removing a single point from a continuous domain isn't a meaningful narrowing, so it's
        // safely skipped rather than attempted -- the constraint is not yet infeasible either,
        // since the literal remains genuinely undetermined.
        Variable<Integer> x = F.create("ox");
        Variable<Double> z = F.create("oz");
        var c = GroundNogoodConstraint.of(Map.<Variable<?>, Object>of(x, 1, z, 5.0));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 1), z, IntervalDomain.of(1.0, 10.0));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_twoOrMoreUndetermined_noChange() {
        Variable<Integer> x = F.create("px"), y = F.create("py");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 3), y, IntRangeDomain.of(1, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_returnsForbiddenMap() {
        Variable<Integer> x = F.create("qx"), y = F.create("qy");
        var c = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                x, IntRangeDomain.of(1, 1), y, IntRangeDomain.of(2, 2));
        assertThat(c.explainInfeasible(domains)).contains(c);
    }

    // --- hits ---

    @Test
    void hits_countAPruningAndAFailureButNotASatisfiedNogood() {
        Variable<Integer> x = F.create("hx"), y = F.create("hy");
        var hits = new GroundNogoodConstraint.Hits();
        var c = GroundNogoodConstraint.of(io.github.rcrida.jcsp.assignments.Assignment.of(Map.of(x, 1, y, 2)), hits);

        c.propagate(Map.of(x, IntRangeDomain.of(1, 1), y, IntRangeDomain.of(1, 2)));
        assertThat(hits.count()).as("x is fixed to its forbidden value, so y loses 2").isEqualTo(1);

        c.propagate(Map.of(x, IntRangeDomain.of(1, 1), y, IntRangeDomain.of(2, 2)));
        assertThat(hits.count()).as("both are fixed to theirs: infeasible").isEqualTo(2);

        c.propagate(Map.of(x, IntRangeDomain.of(2, 2), y, IntRangeDomain.of(2, 2)));
        c.propagate(Map.of(x, IntRangeDomain.of(1, 2), y, IntRangeDomain.of(1, 2)));
        assertThat(hits.count()).as("satisfied, and with two literals open: nothing to do").isEqualTo(2);
    }

    @Test
    void hits_areSharedByEveryNogoodGivenThem() {
        Variable<Integer> x = F.create("hsx"), y = F.create("hsy");
        var hits = new GroundNogoodConstraint.Hits();
        var first = GroundNogoodConstraint.of(io.github.rcrida.jcsp.assignments.Assignment.of(Map.of(x, 1)), hits);
        var second = GroundNogoodConstraint.of(io.github.rcrida.jcsp.assignments.Assignment.of(Map.of(y, 1)), hits);

        first.propagate(Map.of(x, IntRangeDomain.of(1, 1)));
        second.propagate(Map.of(y, IntRangeDomain.of(1, 1)));

        assertThat(hits.count()).isEqualTo(2);
    }

    @Test
    void hits_playNoPartInEquality() {
        Variable<Integer> x = F.create("hex");
        var forbidden = io.github.rcrida.jcsp.assignments.Assignment.of(Map.of(x, 1));

        assertThat(GroundNogoodConstraint.of(forbidden, new GroundNogoodConstraint.Hits()))
                .isEqualTo(GroundNogoodConstraint.of(forbidden))
                .hasSameHashCodeAs(GroundNogoodConstraint.of(forbidden));
    }

    // --- misc ---

    @Test
    void testToString() {
        Variable<Integer> x = F.create("rx");
        var c = GroundNogoodConstraint.of(Map.of(x, 1));
        assertThat(c.toString()).isEqualTo("<(rx), nogood(rx!=1)>");
    }
}
