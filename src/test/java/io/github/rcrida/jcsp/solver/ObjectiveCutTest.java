package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ObjectiveCut#enforce}, the apply-once-per-search half of the cut. {@link ObjectiveCut#narrow}
 * is exercised through {@link BranchAndBoundSolver}, which is its only caller.
 */
public class ObjectiveCutTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final Variable<Integer> X = F.create("cutX");
    static final Variable<Integer> Y = F.create("cutY");

    static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(0, 9))
            .variableDomain(Y, IntRangeDomain.of(0, 9))
            .build();

    static final LinearObjective SUM = LinearObjective.builder()
            .coefficient(X, 1.0).coefficient(Y, 1.0).build();

    @Test
    void enforce_addsTheCutAsAConstraintAndLeavesEveryDomainAlone() {
        // The property the whole method exists for. A bound of 16 on x + y narrows neither domain --
        // either variable can still take 9 while the other takes 0 -- so a search handed only what one
        // propagation pass narrowed would be given back the unbounded problem and re-answer it. The
        // bound has to survive as a constraint to be re-propagated at every node, against the domains
        // that node has narrowed.
        val bounded = new ObjectiveCut().enforce(CSP, SUM, 16.0);

        assertThat(bounded.getVariableDomains())
                .as("a thin bound narrows nothing, which is exactly why narrowing is not enough")
                .isEqualTo(CSP.getVariableDomains());
        assertThat(bounded.getConstraints()).hasSize(1);
        val cut = (LinearBoundConstraint<?>) bounded.getConstraints().iterator().next();
        // Strictly better than 16, i.e. at most 15, since the objective is wholly integral.
        assertThat(cut.getOperator()).isEqualTo(Operator.LEQ);
        assertThat(cut.getBound()).isEqualTo(15);
        assertThat(cut.getVariables()).containsExactlyInAnyOrder(X, Y);
    }

    @Test
    void enforce_isPropagatedByAFixpointBuiltForTheResult() {
        // The second half of the fix, and the reason enforce's result must be what a FixpointPropagation
        // is built from: forProblem filters on the constraint types the problem has, so a cut added to a
        // problem that had no LinearBoundConstraint is propagated only by a list filtered for the cut
        // problem. Filtered for the original, the cut is in the constraint set and never looked at.
        val bounded = new ObjectiveCut().enforce(CSP, SUM, 6.0);

        val forOriginal = FixpointPropagation.Factory.INSTANCE.forProblem(CSP, false);
        val forBounded = FixpointPropagation.Factory.INSTANCE.forProblem(bounded, false);

        assertThat(fixpoint(forOriginal, bounded).getDomain(X))
                .as("filtered for a problem with no linear constraint, the cut is never propagated")
                .isEqualTo(CSP.getDomain(X));
        assertThat(fixpoint(forBounded, bounded).getDomain(X))
                .as("x + y <= 5 bounds each of them at 5")
                .isEqualTo(IntRangeDomain.of(0, 5));
    }

    static ConstraintSatisfactionProblem fixpoint(FixpointPropagation propagation,
                                                  ConstraintSatisfactionProblem csp) {
        return propagation.applyFixpoint(csp, null, io.github.rcrida.jcsp.solver.listener.SolverListener.NONE,
                new io.github.rcrida.jcsp.assignments.Statistics(), new Cancellation()).orElseThrow();
    }

    @Test
    void enforce_declinesAnInfiniteBound() {
        // What an as-yet-unknown incumbent looks like: no bound at all, so nothing to add.
        assertThat(new ObjectiveCut().enforce(CSP, SUM, Double.MAX_VALUE)).isSameAs(CSP);
    }

    @Test
    void constraintFor_declinesAFractionalDiscreteDomain() {
        // Not a continuous domain, so the BoundedDomain check never saw it, and its coefficients and
        // derived bound are both exact ints. But LinearBoundPropagation reads a domain through
        // intValue(), so 1.9 counts as 1 both when filtering the domain against the bound and in
        // isSatisfiedBy's own sum: (1.9, 1.9) sums to 2 under a bound of 2 while really costing 3.8,
        // and a probe would adopt it as an "improvement" over a first solution costing 3.
        val fx = F.<Double>create("fracX");
        val fy = F.<Double>create("fracY");
        val fractional = ConstraintSatisfactionProblem.builder()
                .variableDomain(fx, NumericDiscreteDomain.of(0.0, 1.0, 1.9))
                .variableDomain(fy, NumericDiscreteDomain.of(0.0, 1.0, 1.9))
                .build();
        val sum = LinearObjective.builder().coefficient(fx, 1.0).coefficient(fy, 1.0).build();

        assertThat(new ObjectiveCut().constraintFor(sum, 3.0, fractional)).isNull();
        assertThat(new ObjectiveCut().enforce(fractional, sum, 3.0)).isSameAs(fractional);
    }

    @Test
    void constraintFor_declinesAContinuousDomain() {
        // The other half of the same check: a non-singleton interval cannot be read by integer
        // propagation at all, and -1 is not the next improvement over a continuous cost.
        val cx = F.<Double>create("contX");
        val continuous = ConstraintSatisfactionProblem.builder()
                .variableDomain(cx, IntervalDomain.of(0.0, 9.0))
                .build();
        val sum = LinearObjective.builder().coefficient(cx, 1.0).build();

        assertThat(new ObjectiveCut().constraintFor(sum, 5.0, continuous)).isNull();
    }

    @Test
    void constraintFor_reusesTheCachedCutForTheSameObjectiveAndBound() {
        // What the cache is for: the incumbent moves rarely relative to the per-node rate the cut is
        // asked for, and building one copies a variable set.
        val cut = new ObjectiveCut();

        assertThat(cut.constraintFor(SUM, 16.0, CSP)).isSameAs(cut.constraintFor(SUM, 16.0, CSP));
    }

    @Test
    void constraintFor_rebuildsWhenTheBoundChanges() {
        val cut = new ObjectiveCut();

        assertThat(cut.constraintFor(SUM, 16.0, CSP).getBound()).isEqualTo(15);
        assertThat(cut.constraintFor(SUM, 10.0, CSP).getBound()).isEqualTo(9);
    }

    @Test
    void constraintFor_rebuildsWhenTheObjectiveChanges() {
        // Keyed on the bound alone, a second objective at the same bound was answered with the first
        // objective's cut -- a wrong cut, which discards real solutions rather than failing.
        val xOnly = LinearObjective.builder().coefficient(X, 1.0).build();
        val cut = new ObjectiveCut();

        assertThat(cut.constraintFor(SUM, 16.0, CSP).getVariables()).containsExactlyInAnyOrder(X, Y);
        assertThat(cut.constraintFor(xOnly, 16.0, CSP).getVariables()).containsExactly(X);
    }

    @Test
    void constraintFor_cachesADeclinedNaNBound() {
        // A NaN bound has no cut, and under a raw == comparison never matched its own cache entry
        // either, so it was rebuilt (and re-declined) at every node.
        val cut = new ObjectiveCut();

        assertThat(cut.constraintFor(SUM, Double.NaN, CSP)).isNull();
        assertThat(cut.constraintFor(SUM, Double.NaN, CSP)).isNull();
    }

    @Test
    void enforce_declinesAnObjectiveWithNoExactCut() {
        // A fractional coefficient cannot be cut exactly, and an inexact cut would have to be loosened
        // by an epsilon to stay sound -- see ObjectiveCut#build.
        val halfSum = LinearObjective.builder().coefficient(X, 0.5).coefficient(Y, 0.5).build();

        assertThat(new ObjectiveCut().enforce(CSP, halfSum, 16.0)).isSameAs(CSP);
    }
}
