package io.github.rcrida.jcsp.consistency.fixpoint;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FixpointConsistencyTest {

    @Test
    void toString_namesTheConstraintType() {
        assertThat(FixpointConsistency.of(SumBoundConstraint.class)).hasToString("FixpointConsistency(SumBoundConstraint)");
    }

    @Test
    void apply_noMatchingConstraints_returnsUnchanged() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        assertThat(FixpointConsistency.of(SumBoundConstraint.class).apply(csp)).hasValue(csp);
    }

    @Test
    void explainConflict_noMatchingConstraints_returnsEmpty() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        assertThat(FixpointConsistency.of(SumBoundConstraint.class).explainConflict(csp)).isEmpty();
    }

    @Test
    void explainConflict_feasibleConstraintWithUpdates_returnsEmpty() {
        // SumBoundConstraint(x+y≤3) with x,y∈{1..5}: first pass narrows domains (updates non-empty →
        // changed=true branch), second pass makes no further progress → while exits → Optional.empty()
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(1, 5))
                .variableDomain(y, IntRangeDomain.of(1, 5))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        assertThat(FixpointConsistency.of(SumBoundConstraint.class).explainConflict(csp)).isEmpty();
    }

    @Test
    void explainConflict_infeasibleConstraint_returnsReason() {
        // SumBoundConstraint(x+y≤3) with x,y∈{5..5}: infeasible on the very first propagate() call, so
        // explainConflict's isInfeasible() ternary branch (as opposed to the empty/feasible one
        // covered above) is exercised, via applyWithReason's own reason derivation.
        Variable<Integer> x = Variable.Factory.INSTANCE.create("x");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("y");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(5, 5))
                .variableDomain(y, IntRangeDomain.of(5, 5))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        assertThat(FixpointConsistency.of(SumBoundConstraint.class).explainConflict(csp)).isPresent();
    }

    // --- per-object dirty tracking (relevant()/byVariable index) ---

    @Test
    void apply_dirtySetExcludingConstraintVariables_skipsCheckEntirely() {
        // The constraint (x=y=5, sum<=3) is already infeasible if checked -- but the dirty set only
        // names z, which the constraint doesn't reference, so it must be skipped entirely and the
        // CSP returned unchanged, mirroring NogoodFixpointConsistencyTest's identical scenario.
        Variable<Integer> x = Variable.Factory.INSTANCE.create("dtx1");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("dty1");
        Variable<Integer> z = Variable.Factory.INSTANCE.create("dtz1");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(5, 5))
                .variableDomain(y, IntRangeDomain.of(5, 5))
                .variableDomain(z, IntRangeDomain.of(1, 3))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        assertThat(FixpointConsistency.of(SumBoundConstraint.class).apply(csp, Set.of(z))).hasValue(csp);
    }

    @Test
    void apply_dirtySetIncludingConstraintVariable_stillDetectsInfeasibility() {
        Variable<Integer> x = Variable.Factory.INSTANCE.create("dtx2");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("dty2");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(5, 5))
                .variableDomain(y, IntRangeDomain.of(5, 5))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        assertThat(FixpointConsistency.of(SumBoundConstraint.class).apply(csp, Set.of(x))).isEmpty();
    }

    @Test
    void apply_dirtySetSpanningBothConstraintVariables_dedupesAndDetectsInfeasibility() {
        // Both x and y are dirty, so relevant()'s indexed lookup unions per-variable matches for a
        // constraint referencing both -- must still be deduplicated down to a single check, not
        // applied/counted twice.
        Variable<Integer> x = Variable.Factory.INSTANCE.create("dtx3");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("dty3");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(5, 5))
                .variableDomain(y, IntRangeDomain.of(5, 5))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        assertThat(FixpointConsistency.of(SumBoundConstraint.class).apply(csp, Set.of(x, y))).isEmpty();
    }

    @Test
    void apply_nullDirtySet_fullScanStillDetectsInfeasibility() {
        Variable<Integer> x = Variable.Factory.INSTANCE.create("dtx4");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("dty4");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(5, 5))
                .variableDomain(y, IntRangeDomain.of(5, 5))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        assertThat(FixpointConsistency.of(SumBoundConstraint.class).apply(csp, null)).isEmpty();
    }

    @Test
    void applyWithReason_dirtySetExcludingConstraintVariables_skipsCheckEntirely() {
        Variable<Integer> x = Variable.Factory.INSTANCE.create("dtx5");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("dty5");
        Variable<Integer> z = Variable.Factory.INSTANCE.create("dtz5");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(5, 5))
                .variableDomain(y, IntRangeDomain.of(5, 5))
                .variableDomain(z, IntRangeDomain.of(1, 3))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        var result = FixpointConsistency.of(SumBoundConstraint.class).applyWithReason(csp, Set.of(z));
        assertThat(result.isInfeasible()).isFalse();
    }

    @Test
    void applyWithReason_dirtySetIncludingConstraintVariable_stillDetectsInfeasibility() {
        Variable<Integer> x = Variable.Factory.INSTANCE.create("dtx6");
        Variable<Integer> y = Variable.Factory.INSTANCE.create("dty6");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x, IntRangeDomain.of(5, 5))
                .variableDomain(y, IntRangeDomain.of(5, 5))
                .sumConstraint(Set.of(x, y), Operator.LEQ, 3)
                .build();
        var result = FixpointConsistency.of(SumBoundConstraint.class).applyWithReason(csp, Set.of(x));
        assertThat(result.isInfeasible()).isTrue();
    }
}
