package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Statistics;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FixpointPropagationTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void changedVariables_isUnmodifiable() {
        Variable<Integer> x = F.create("cvx");
        var before = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(1, 3));
        var after = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(1, 1));
        var changed = FixpointPropagation.changedVariables(before, after);
        assertThat(changed).containsExactly(x);
        assertThatThrownBy(() -> changed.add(F.create("other"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void applyFixpoint_everyDirtySetHandedToAPropagatorIsUnmodifiable() {
        // Worklist.wakeOne hands one `changed` set to every propagator woken by it rather than
        // copying it per propagator, and unions in place on a second wake. Both only stay sound
        // while no propagator can mutate what it was given, so takeDirty must hand out an
        // unmodifiable set on both paths: the shared reference and the worklist's own union buffer.
        // This spy tries to mutate every non-null set it receives and records what happened, run
        // against a cascade so it is woken repeatedly and sees the union path too.
        var seen = new java.util.ArrayList<String>();
        ConstraintConsistency spy = new ConstraintConsistency() {
            @Override
            public java.util.Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem csp) {
                return java.util.Optional.of(csp);
            }

            @Override
            public java.util.Optional<ConstraintSatisfactionProblem> apply(
                    ConstraintSatisfactionProblem csp, Set<Variable<?>> changedSinceLastRun) {
                if (changedSinceLastRun == null) {
                    seen.add("fullScan");
                } else {
                    try {
                        changedSinceLastRun.add(F.create("intruder"));
                        seen.add("MUTABLE");
                    } catch (UnsupportedOperationException expected) {
                        seen.add("unmodifiable");
                    }
                }
                return java.util.Optional.of(csp);
            }
        };
        Variable<Integer> a = F.create("ua"), b = F.create("ub"), c = F.create("uc");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(0, 5))
                .variableDomain(b, IntRangeDomain.of(0, 5))
                .variableDomain(c, IntRangeDomain.of(0, 5))
                .offsetConstraint(a, 1, io.github.rcrida.jcsp.constraints.Operator.LEQ, b)
                .offsetConstraint(b, 1, io.github.rcrida.jcsp.constraints.Operator.LEQ, c)
                .build();
        // The spy sits on both sides of the narrowing propagator because the two paths need
        // different positions, and one list covers both. Ahead of it, the spy runs before anything
        // has woken it and so takes a shared set (the frozen initialSeed, then the shared `changed`
        // from a wake). Behind it, the spy is still queued when the wake arrives, so the wake has to
        // union into its pending set -- the one slot that is this worklist's own mutable buffer.
        // Verified by deleting takeDirty's wrap: the trailing position then reports MUTABLE, and
        // the leading position alone does not catch it.
        var propagation = FixpointPropagation.builder()
                .propagators(java.util.List.of(
                        spy,
                        io.github.rcrida.jcsp.consistency.fixpoint.FixpointConsistency.of(
                                io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint.class),
                        spy))
                .build();
        // A seed, so the spy's first call gets a set rather than null (a full scan).
        assertThat(propagation.applyFixpoint(csp, Set.of(a), SolverListener.NONE, new Statistics(), Cancellation.NEVER))
                .isPresent();
        assertThat(seen).hasSizeGreaterThan(2).doesNotContain("MUTABLE", "fullScan").contains("unmodifiable");
    }

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
        assertThat(FixpointPropagation.FULL.applyFixpoint(csp, Set.of(z), SolverListener.NONE, new Statistics(), Cancellation.NEVER)).hasValue(csp);
        assertThat(FixpointPropagation.FULL.applyFixpoint(csp, null, SolverListener.NONE, new Statistics(), Cancellation.NEVER)).isEmpty();
    }

    @Test
    void applyFixpointWithReason_nogoodCausesWipeout_incrementsNogoodRejections() {
        Variable<Integer> x = F.create("statx"), y = F.create("staty");
        var nogood = GroundNogoodConstraint.of(Map.of(x, 1, y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(2, 2))
                .nogood(nogood)
                .build();
        var statistics = new Statistics();
        var result = FixpointPropagation.FULL.applyFixpointWithReason(csp, null, SolverListener.NONE, statistics, Cancellation.NEVER);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(statistics.getNogoodRejections()).isEqualTo(1);
    }

    @Test
    void applyFixpointWithReason_ordinaryConstraintCausesWipeout_doesNotIncrementNogoodRejections() {
        Variable<Integer> x = F.create("statx2"), y = F.create("staty2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 1))
                .variableDomain(y, IntRangeDomain.of(1, 1))
                .notEqualsConstraint(x, y)
                .build();
        var statistics = new Statistics();
        var result = FixpointPropagation.FULL.applyFixpointWithReason(csp, null, SolverListener.NONE, statistics, Cancellation.NEVER);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(statistics.getNogoodRejections()).isZero();
    }

    @Test
    void applyFixpoint_cancelledBeforeFirstPropagator_throwsSolverCancelledException() {
        Variable<Integer> x = F.create("cancelledx");
        var csp = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        var cancellation = new Cancellation();
        cancellation.cancel();
        var statistics = new Statistics();

        assertThatThrownBy(() -> FixpointPropagation.FULL.applyFixpoint(csp, null, SolverListener.NONE, statistics, cancellation))
                .isInstanceOf(SolverCancelledException.class)
                .extracting(e -> ((SolverCancelledException) e).getStatistics())
                .isSameAs(statistics);

        assertThatThrownBy(() -> FixpointPropagation.FULL.applyFixpointWithReason(csp, null, SolverListener.NONE, statistics, cancellation))
                .isInstanceOf(SolverCancelledException.class);
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

    @Test
    void propagatorThatDoesNotConvergeInternally_isRewokenByItsOwnNarrowing() {
        // Worklist#wake skips re-queueing the propagator that caused a change only when that
        // propagator converges internally; one that does not must be woken by its own narrowing.
        // NogoodFixpointConsistency is the only such propagator, so this branch is reachable only
        // when nogoods are in play -- which stopped being the default when nogood learning did.
        Variable<Integer> x = Variable.Factory.INSTANCE.create("wlx");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("wly");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 2))
                .variableDomain(y, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(x, y)
                // Unit nogood: forbids x=1 outright, so the nogood propagator itself narrows x to
                // {2}, which must then wake the arc consistency that pins y to 1.
                .nogood(GroundNogoodConstraint.of(Map.of(x, 1)))
                .build();

        var result = FixpointPropagation.FULL.applyFixpoint(csp, null,
                SolverListener.NONE, new Statistics(), Cancellation.NEVER);

        assertThat(result).isPresent();
        assertThat(result.get().getDomain(x).singleValue()).contains(2);
        assertThat(result.get().getDomain(y).singleValue()).contains(1);
    }
}
