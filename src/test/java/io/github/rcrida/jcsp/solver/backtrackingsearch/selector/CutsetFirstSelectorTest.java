package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CutsetFirstSelectorTest {
    private final Variable.Factory variables = new Variable.Factory();

    /** A ring of {@code size} not-equals constraints; the first variable gets {@code firstDomain} values, the rest two. */
    private ConstraintSatisfactionProblem ring(List<Variable<Integer>> ring, int size, int firstDomain) {
        for (int i = 0; i < size; i++) ring.add(variables.create("r" + i));
        var builder = ConstraintSatisfactionProblem.builder();
        for (int i = 0; i < size; i++) builder.variableDomain(ring.get(i), IntRangeDomain.of(1, i == 0 ? firstDomain : 2));
        for (int i = 0; i < size; i++) builder.notEqualsConstraint(ring.get(i), ring.get((i + 1) % size));
        return builder.build();
    }

    @Test
    void aSmallCutsetIsBranchedOnFirst() {
        // An 11-ring needs one cut variable, within 10% of 11. dom/wdeg alone would start with a
        // two-value variable; the cut variable, r0, has nine values.
        List<Variable<Integer>> r = new ArrayList<>();
        var csp = ring(r, 11, 9);
        Set<Constraint> constraints = new HashSet<>(csp.getConstraints());
        constraints.add(GroundNogoodConstraint.of(Map.<Variable<?>, Object>of(r.get(1), 1, r.get(5), 1)));
        var selector = new CutsetFirstSelector(constraints);
        var domWdeg = new DomWdegVariableSelector(constraints);

        assertThat(selector.cutset()).containsExactly(r.get(0));
        assertThat(domWdeg.select(csp, Assignment.empty())).isNotEqualTo(r.get(0));
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(r.get(0));

        // Once the cutset is assigned, the choice is dom/wdeg's.
        Assignment cutAssigned = Assignment.of(Map.of(r.get(0), 3));
        assertThat(selector.select(csp, cutAssigned)).isEqualTo(domWdeg.select(csp, cutAssigned));
    }

    @Test
    void withNoSmallCutsetItIsDomWdegUnderEveryEvent() {
        // A triangle needs one cut variable of three, over the 10% limit. Equal domains tie every
        // ratio once weights are reset, so the restart's tie-break decides each pick.
        Variable<Integer> a = variables.create("a");
        Variable<Integer> b = variables.create("b");
        Variable<Integer> c = variables.create("c");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, IntRangeDomain.of(1, 4))
                .variableDomain(b, IntRangeDomain.of(1, 4))
                .variableDomain(c, IntRangeDomain.of(1, 4))
                .notEqualsConstraint(a, b).notEqualsConstraint(b, c).notEqualsConstraint(c, a)
                .build();
        Set<Constraint> constraints = new HashSet<>(csp.getConstraints());
        var selector = new CutsetFirstSelector(constraints);
        var domWdeg = new DomWdegVariableSelector(constraints);
        assertThat(selector.cutset()).isEmpty();
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(domWdeg.select(csp, Assignment.empty()));

        Assignment bAssigned = Assignment.of(Map.of(b, 1));
        selector.onConflict(b, bAssigned);
        domWdeg.onConflict(b, bAssigned);
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(domWdeg.select(csp, Assignment.empty()));

        selector.onValueRejected(c);
        domWdeg.onValueRejected(c);
        assertThat(selector.select(csp, Assignment.empty())).isEqualTo(c);
        assertThat(domWdeg.select(csp, Assignment.empty())).isEqualTo(c);

        selector.onStagnation();
        domWdeg.onStagnation();
        selector.onRestart(new Random(3));
        domWdeg.onRestart(new Random(3));
        List<Variable<?>> picks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Variable<?> pick = selector.select(csp, Assignment.empty());
            assertThat(pick).isEqualTo(domWdeg.select(csp, Assignment.empty()));
            picks.add(pick);
        }
        // Drawn from the restart's source rather than always the first tied variable.
        assertThat(new HashSet<>(picks)).hasSizeGreaterThan(1);
    }
}
