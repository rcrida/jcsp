package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NogoodFixpointConsistencyTest {

    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("nfc-x");
    static final Variable<Integer> Y = F.create("nfc-y");
    static final Variable<Integer> Z = F.create("nfc-z");

    @Test
    void apply_noNogoods_returnsUnchanged() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 3))
                .build();
        assertThat(NogoodFixpointConsistency.INSTANCE.apply(csp)).hasValue(csp);
    }

    @Test
    void apply_singleArgDelegatesToFullScan_prunesUndeterminedLiteral() {
        // X is singleton at its forbidden value (falsified); Y is open and still contains its
        // forbidden value (undetermined) -- propagation prunes it from Y.
        var nogood = GroundNogoodConstraint.of(Map.of(X, 1, Y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 1))
                .variableDomain(Y, IntRangeDomain.of(1, 3))
                .nogood(nogood)
                .build();
        var result = NogoodFixpointConsistency.INSTANCE.apply(csp);
        assertThat(result).isPresent();
        assertThat(result.get().getDomain(Y)).isEqualTo(IntRangeDomain.of(1, 3).toBuilder().delete(2).build());
    }

    @Test
    void apply_nullDirty_infeasibleNogoodDetected() {
        var nogood = GroundNogoodConstraint.of(Map.of(X, 1, Y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 1))
                .variableDomain(Y, IntRangeDomain.of(2, 2))
                .nogood(nogood)
                .build();
        assertThat(NogoodFixpointConsistency.INSTANCE.apply(csp, null)).isEmpty();
    }

    @Test
    void apply_dirtySetExcludingAllNogoodVariables_skipsCheckEntirely() {
        // The nogood (x=1, y=2) is already falsified by the given domains, so checking it would
        // report infeasible -- but the dirty set only names z, which the nogood doesn't reference,
        // so it must be skipped entirely and the CSP returned unchanged.
        var nogood = GroundNogoodConstraint.of(Map.of(X, 1, Y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 1))
                .variableDomain(Y, IntRangeDomain.of(2, 2))
                .variableDomain(Z, IntRangeDomain.of(1, 3))
                .nogood(nogood)
                .build();
        assertThat(NogoodFixpointConsistency.INSTANCE.apply(csp, Set.of(Z))).hasValue(csp);
    }

    @Test
    void apply_dirtySetIncludingNogoodVariable_stillDetectsInfeasibility() {
        var nogood = GroundNogoodConstraint.of(Map.of(X, 1, Y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 1))
                .variableDomain(Y, IntRangeDomain.of(2, 2))
                .variableDomain(Z, IntRangeDomain.of(1, 3))
                .nogood(nogood)
                .build();
        assertThat(NogoodFixpointConsistency.INSTANCE.apply(csp, Set.of(X))).isEmpty();
    }

    @Test
    void explainConflict_noNogoods_returnsEmpty() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 3))
                .build();
        assertThat(NogoodFixpointConsistency.INSTANCE.explainConflict(csp)).isEmpty();
    }

    @Test
    void explainConflict_feasibleWithNarrowing_returnsEmpty() {
        var nogood = GroundNogoodConstraint.of(Map.of(X, 1, Y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 1))
                .variableDomain(Y, IntRangeDomain.of(1, 3))
                .nogood(nogood)
                .build();
        assertThat(NogoodFixpointConsistency.INSTANCE.explainConflict(csp)).isEmpty();
    }

    @Test
    void explainConflict_infeasible_returnsNogoodAsItsOwnReason() {
        var nogood = GroundNogoodConstraint.of(Map.of(X, 1, Y, 2));
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(X, IntRangeDomain.of(1, 1))
                .variableDomain(Y, IntRangeDomain.of(2, 2))
                .nogood(nogood)
                .build();
        assertThat(NogoodFixpointConsistency.INSTANCE.explainConflict(csp)).contains(nogood);
    }
}
