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

    private static final Variable.Factory F = Variable.Factory.INSTANCE;

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
    void factoryInstance_buildsDomWdeg() {
        assertThat(AdaptiveVariableSelector.Factory.INSTANCE.createSelector(Set.of()))
                .isInstanceOf(DomWdegVariableSelector.class);
    }
}
