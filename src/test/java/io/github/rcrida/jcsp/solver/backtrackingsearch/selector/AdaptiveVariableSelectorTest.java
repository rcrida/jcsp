package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The interface's own contract: a selector that only ranks variables implements {@link
 * AdaptiveVariableSelector#select} and nothing else, and the search may raise every event at it
 * regardless.
 */
class AdaptiveVariableSelectorTest {

    private final Variable.Factory F = new Variable.Factory();

    /** Ranks by nothing at all -- it always answers with the first unassigned variable. */
    private static final AdaptiveVariableSelector RANKING_ONLY =
            (csp, assignment) -> csp.getVariableDomains().keySet().stream()
                    .filter(v -> !assignment.isAssigned(v))
                    .findFirst()
                    .orElseThrow();

    @Test
    void defaultHooks_ignoreEveryEvent() {
        Variable<Integer> a = F.create("adapt_a"), b = F.create("adapt_b");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 2))
                .variableDomain(b, IntRangeDomain.of(1, 2))
                .notEqualsConstraint(a, b)
                .build();
        Assignment empty = Assignment.empty();

        RANKING_ONLY.onConflict(a, empty);
        RANKING_ONLY.onValueRejected(a);
        RANKING_ONLY.onRestart(new Random(1));
        RANKING_ONLY.onRestart(null);
        RANKING_ONLY.onStagnation();

        // Nothing was recorded, so the ranking is exactly what it was before the events arrived.
        assertThat(RANKING_ONLY.select(csp, empty)).isEqualTo(a);
    }

    @Test
    void factoryInstance_buildsCutsetFirst() {
        assertThat(AdaptiveVariableSelector.Factory.INSTANCE.createSelector(Set.of()))
                .isInstanceOf(CutsetFirstSelector.class);
    }

    @Test
    void factoryDomWdeg_buildsDomWdeg() {
        assertThat(AdaptiveVariableSelector.Factory.DOM_WDEG.createSelector(Set.of()))
                .isInstanceOf(DomWdegVariableSelector.class);
    }

    /** Two unconstrained variables, which every dom/wdeg ratio ties. */
    private ConstraintSatisfactionProblem tied(Variable<Integer> first, Variable<Integer> second) {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(first, IntRangeDomain.of(1, 2))
                .variableDomain(second, IntRangeDomain.of(1, 2))
                .build();
    }

    @Test
    void factoriesBreakTiesWithTheGivenTieBreak() {
        Variable<Integer> a = F.create("tie_a"), b = F.create("tie_b");
        var csp = tied(a, b);
        TieBreak.Factory last = constraints -> tied -> tied.get(tied.size() - 1);

        assertThat(AdaptiveVariableSelector.Factory.cutsetFirst(last).createSelector(Set.of()).select(csp, Assignment.empty()))
                .isEqualTo(b);
        assertThat(AdaptiveVariableSelector.Factory.domWdeg(last).createSelector(Set.of()).select(csp, Assignment.empty()))
                .isEqualTo(b);
    }
}
