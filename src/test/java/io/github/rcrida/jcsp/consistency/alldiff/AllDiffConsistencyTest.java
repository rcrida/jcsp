package io.github.rcrida.jcsp.consistency.alldiff;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rcrida.jcsp.consistency.FixpointConsistency;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;

public class AllDiffConsistencyTest {
    private static final FixpointConsistency CONSISTENCY = FixpointConsistency.of(AllDiffConstraint.class);
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void apply_noCumulativeConstraints_returnsUnchanged() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        assertThat(CONSISTENCY.apply(csp)).hasValue(csp);
    }

    @Test
    void apply_nakedPair_prunesOtherDomains() {
        // x1 ∈ {1,2}, x2 ∈ {1,2}, x3 ∈ {1,2,3}  AllDiff
        // x1 and x2 form a naked pair on {1,2}: value 1 and 2 cannot appear in x3.
        // Expected: x3 domain → {3}
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        Variable<Integer> x3 = F.create("x3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 2))
                .variableDomain(x2, IntRangeDomain.of(1, 2))
                .variableDomain(x3, IntRangeDomain.of(1, 3))
                .allDiffConstraint(java.util.Set.of(x1, x2, x3))
                .build();
        var result = CONSISTENCY.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().findDomain(x3)).hasValue(IntRangeDomain.of(3, 3));
    }

    @Test
    void apply_infeasible_returnsEmpty() {
        // x1 ∈ {1,2}, x2 ∈ {1,2}, x3 ∈ {1,2}  AllDiff — 3 variables, only 2 values → infeasible
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        Variable<Integer> x3 = F.create("x3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 2))
                .variableDomain(x2, IntRangeDomain.of(1, 2))
                .variableDomain(x3, IntRangeDomain.of(1, 2))
                .allDiffConstraint(java.util.Set.of(x1, x2, x3))
                .build();
        assertThat(CONSISTENCY.apply(csp)).isEmpty();
    }

    @Test
    void apply_wideDomains_noChange() {
        // Wide domains, no compulsory parts — no pruning expected
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 5))
                .variableDomain(x2, IntRangeDomain.of(1, 5))
                .allDiffConstraint(java.util.Set.of(x1, x2))
                .build();
        var result = CONSISTENCY.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().findDomain(x1)).hasValue(IntRangeDomain.of(1, 5));
        assertThat(result.get().findDomain(x2)).hasValue(IntRangeDomain.of(1, 5));
    }

    @Test
    void apply_singletonDomain_prunesMatchedValueFromOthers() {
        // x1 ∈ {1}, x2 ∈ {1,2,3}  AllDiff — x1 fixed to 1, so x2 cannot be 1
        // AC3 already handles this via binary ≠, but AllDiffConsistency should also prune it
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 1))
                .variableDomain(x2, IntRangeDomain.of(1, 3))
                .allDiffConstraint(java.util.Set.of(x1, x2))
                .build();
        var result = CONSISTENCY.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().findDomain(x2)).hasValue(IntRangeDomain.of(2, 3));
    }

    @Test
    void apply_nakedTriple_prunesRemainingVariable() {
        // x1∈{1,2,3}, x2∈{1,2,3}, x3∈{1,2,3}, x4∈{1,2,3,4}  AllDiff
        // x1/x2/x3 form a naked triple on {1,2,3} → x4 domain → {4}
        Variable<Integer> x1 = F.create("x1");
        Variable<Integer> x2 = F.create("x2");
        Variable<Integer> x3 = F.create("x3");
        Variable<Integer> x4 = F.create("x4");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, IntRangeDomain.of(1, 3))
                .variableDomain(x2, IntRangeDomain.of(1, 3))
                .variableDomain(x3, IntRangeDomain.of(1, 3))
                .variableDomain(x4, IntRangeDomain.of(1, 4))
                .allDiffConstraint(java.util.Set.of(x1, x2, x3, x4))
                .build();
        var result = CONSISTENCY.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().findDomain(x4)).hasValue(IntRangeDomain.of(4, 4));
    }
}
