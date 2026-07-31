package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FixpointPropagationTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void applyFixpointWithSeed_skipsNogoodOutsideSeed_fullScanCatchesIt() {
        // A nogood (x=1, y=2) already falsified by the given domains. Seeding round 1 with a set
        // that excludes both x and y means applyFixpoint(csp, seed) must skip checking it entirely
        // -- proving the seed actually reaches NogoodFixpointConsistency and narrows what round 1
        // checks, not just that nothing broke. The no-seed overload (full round-1 scan, as used
        // everywhere outside a search node) still catches the same nogood.
        Variable<Integer> x = F.create("seedx"), y = F.create("seedy"), z = F.create("seedz");
        var nogood = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(2, 2))
                .variableDomain(z, IntRangeDomain.of(1, 3))
                .nogood(nogood)
                .build();
        assertThat(FixpointPropagation.applyFixpoint(csp, Set.of(z))).hasValue(csp);
        assertThat(FixpointPropagation.applyFixpoint(csp)).isEmpty();
    }

    @Test
    void logIfDomainSumReduced_debugDisabled_isANoOpRegardlessOfChange() {
        Variable<Integer> x = F.create("logdisabledx");
        var wide = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        var narrow = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 2)).build();
        FixpointPropagation.logIfDomainSumReduced(AC3.INSTANCE, wide, narrow, false);
    }

    @Test
    void logIfDomainSumReduced_debugEnabledAndReduced_logsTheReduction() {
        Variable<Integer> x = F.create("logreducedx");
        var wide = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        var narrow = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 2)).build();
        FixpointPropagation.logIfDomainSumReduced(AC3.INSTANCE, wide, narrow, true);
    }

    @Test
    void logIfDomainSumReduced_debugEnabledButUnchanged_doesNotLog() {
        Variable<Integer> x = F.create("logunchangedx");
        var csp = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        FixpointPropagation.logIfDomainSumReduced(AC3.INSTANCE, csp, csp, true);
    }
}
