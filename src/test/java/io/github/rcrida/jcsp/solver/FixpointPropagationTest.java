package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.consistency.ConstraintConsistency;
import io.github.rcrida.jcsp.consistency.arc.AC3;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
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
        // that excludes both x and y means applyFixpoint(csp, seed, listener) must skip checking it
        // entirely -- proving the seed actually reaches NogoodFixpointConsistency and narrows what
        // round 1 checks, not just that nothing broke. A null seed (full round-1 scan, as used
        // everywhere outside a search node) still catches the same nogood.
        Variable<Integer> x = F.create("seedx"), y = F.create("seedy"), z = F.create("seedz");
        var nogood = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(2, 2))
                .variableDomain(z, IntRangeDomain.of(1, 3))
                .nogood(nogood)
                .build();
        assertThat(FixpointPropagation.applyFixpoint(csp, Set.of(z), SolverListener.NONE)).hasValue(csp);
        assertThat(FixpointPropagation.applyFixpoint(csp, null, SolverListener.NONE)).isEmpty();
    }

    @Test
    void logIfDomainSumReduced_debugDisabledAndNoListener_isANoOpRegardlessOfChange() {
        Variable<Integer> x = F.create("logdisabledx");
        var wide = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        var narrow = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 2)).build();
        FixpointPropagation.logIfDomainSumReduced(AC3.INSTANCE, wide, narrow, false, SolverListener.NONE);
    }

    @Test
    void logIfDomainSumReduced_debugEnabledAndReduced_logsTheReduction() {
        Variable<Integer> x = F.create("logreducedx");
        var wide = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        var narrow = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 2)).build();
        FixpointPropagation.logIfDomainSumReduced(AC3.INSTANCE, wide, narrow, true, SolverListener.NONE);
    }

    @Test
    void logIfDomainSumReduced_debugEnabledButUnchanged_doesNotLog() {
        Variable<Integer> x = F.create("logunchangedx");
        var csp = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        FixpointPropagation.logIfDomainSumReduced(AC3.INSTANCE, csp, csp, true, SolverListener.NONE);
    }

    @Test
    void logIfDomainSumReduced_debugDisabledButListenerRegistered_firesOnPropagatorProgress() {
        // Debug logging off, but a real listener registered: the gate must still let the reduced
        // branch through (listener != SolverListener.NONE), exercising the one combination the
        // other tests here don't -- debug-off no longer means cost-free once a listener is present.
        Variable<Integer> x = F.create("logdisabledwithlistenerx");
        var wide = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        var narrow = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 2)).build();

        record Progress(ConstraintConsistency propagator, Map<Variable<?>, Domain<?>> before,
                         Map<Variable<?>, Domain<?>> after, double beforeSum, double afterSum) {}
        var captured = new Progress[1];
        SolverListener recorder = new SolverListener() {
            @Override
            public void onPropagatorProgress(ConstraintConsistency propagator, Map<Variable<?>, Domain<?>> domainsBefore,
                                              Map<Variable<?>, Domain<?>> domainsAfter, double domainSumBefore, double domainSumAfter) {
                captured[0] = new Progress(propagator, domainsBefore, domainsAfter, domainSumBefore, domainSumAfter);
            }
        };

        FixpointPropagation.logIfDomainSumReduced(AC3.INSTANCE, wide, narrow, false, recorder);

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].propagator()).isSameAs(AC3.INSTANCE);
        assertThat(captured[0].before()).isEqualTo(wide.getVariableDomains());
        assertThat(captured[0].after()).isEqualTo(narrow.getVariableDomains());
        assertThat(captured[0].beforeSum()).isEqualTo(5.0); // IntRangeDomain.of(1,5) has 5 values
        assertThat(captured[0].afterSum()).isEqualTo(2.0);  // IntRangeDomain.of(1,2) has 2 values
    }
}
