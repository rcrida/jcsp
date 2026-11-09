package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Inference;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NeighbourhoodRootTest {

    /** Propagates nothing, refuses the variables in {@code reject}, and remembers what it was told changed. */
    private static final class Recording implements Inference {
        final List<Set<Variable<?>>> told = new ArrayList<>();
        final Set<Variable<?>> reject = new HashSet<>();

        @Override
        public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem, Variable<?> variable,
                                                             Assignment assignment) {
            return apply(problem, variable, assignment, Set.of());
        }

        @Override
        public Optional<ConstraintSatisfactionProblem> apply(ConstraintSatisfactionProblem problem, Variable<?> variable,
                                                             Assignment assignment, Set<Variable<?>> alsoChanged) {
            told.add(alsoChanged);
            return reject.contains(variable) ? Optional.empty() : Optional.of(problem);
        }
    }

    private final ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder =
            ConstraintSatisfactionProblem.builder();
    private final Recording inference = new Recording();

    private NeighbourhoodRoot root() {
        return new NeighbourhoodRoot(inference, Assignment.empty());
    }

    @Test
    void fixesEveryDecisionThatIsNotFreeAtItsIncumbentValue() {
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(0, 9));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(0, 9));
        Variable<Integer> c = builder.createVariable("c", IntRangeDomain.of(0, 9));
        ConstraintSatisfactionProblem csp = builder.build();

        Optional<NeighbourhoodRoot.Fixed> fixed = root().fix(csp, Set.of(), List.of(a, b, c), Set.of(b),
                Assignment.of(Map.of(a, 1, b, 2, c, 3)));

        assertThat(fixed).isPresent();
        assertThat(fixed.get().assignment().getValue(a)).contains(1);
        assertThat(fixed.get().assignment().getValue(b)).isEmpty();
        assertThat(fixed.get().assignment().getValue(c)).contains(3);
        assertThat(fixed.get().csp()).isSameAs(csp);
    }

    @Test
    void aDecisionPropagationAlreadyForcedIsNotFixedAgain() {
        Variable<Integer> forced = builder.createVariable("forced", IntRangeDomain.of(5, 5));
        Variable<Integer> other = builder.createVariable("other", IntRangeDomain.of(0, 9));
        ConstraintSatisfactionProblem csp = builder.build();

        Optional<NeighbourhoodRoot.Fixed> fixed = root().fix(csp, Set.of(), List.of(forced, other), Set.of(),
                Assignment.of(Map.of(forced, 5, other, 4)));

        assertThat(fixed.orElseThrow().assignment().getValue(forced)).contains(5);
        assertThat(fixed.orElseThrow().assignment().getValue(other)).contains(4);
        // Only `other` was propagated: `forced` was assigned by the forced-value step.
        assertThat(inference.told).hasSize(1);
    }

    @Test
    void aDecisionWhoseIncumbentValueIsNoLongerInItsDomainIsLeftFree() {
        Variable<Integer> narrowed = builder.createVariable("narrowed", IntRangeDomain.of(0, 2));
        Variable<Integer> other = builder.createVariable("other", IntRangeDomain.of(0, 9));
        ConstraintSatisfactionProblem csp = builder.build();

        Optional<NeighbourhoodRoot.Fixed> fixed = root().fix(csp, Set.of(), List.of(narrowed, other), Set.of(),
                Assignment.of(Map.of(narrowed, 7, other, 4)));

        assertThat(fixed.orElseThrow().assignment().getValue(narrowed)).isEmpty();
        assertThat(fixed.orElseThrow().assignment().getValue(other)).contains(4);
    }

    @Test
    void aDecisionThatConflictsWithOneAlreadyFixedIsLeftFree() {
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(0, 9));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(0, 9));
        builder.notEqualsConstraint(a, b);
        ConstraintSatisfactionProblem csp = builder.build();

        // Not a solution, so that fixing b at 1 conflicts with a at 1 and cannot be kept.
        Optional<NeighbourhoodRoot.Fixed> fixed = root().fix(csp, Set.of(), List.of(a, b), Set.of(),
                Assignment.of(Map.of(a, 1, b, 1)));

        assertThat(fixed.orElseThrow().assignment().getValue(a)).contains(1);
        assertThat(fixed.orElseThrow().assignment().getValue(b)).isEmpty();
    }

    @Test
    void aDecisionPropagationRefusesIsLeftFreeAndTheRestAreStillFixed() {
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(0, 9));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(0, 9));
        Variable<Integer> c = builder.createVariable("c", IntRangeDomain.of(0, 9));
        ConstraintSatisfactionProblem csp = builder.build();
        inference.reject.add(a);

        Optional<NeighbourhoodRoot.Fixed> fixed = root().fix(csp, Set.of(), List.of(a, b, c), Set.of(),
                Assignment.of(Map.of(a, 1, b, 2, c, 3)));

        assertThat(fixed.orElseThrow().assignment().getValue(a)).isEmpty();
        assertThat(fixed.orElseThrow().assignment().getValue(b)).contains(2);
        assertThat(fixed.orElseThrow().assignment().getValue(c)).contains(3);
    }

    @Test
    void theCutsScopeIsDeclaredUntilAFixingHasPropagatedIt() {
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(0, 9));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(0, 9));
        Variable<Integer> c = builder.createVariable("c", IntRangeDomain.of(0, 9));
        Variable<Integer> cutVariable = builder.createVariable("cut", IntRangeDomain.of(0, 9));
        ConstraintSatisfactionProblem csp = builder.build();
        inference.reject.add(a);
        Set<Variable<?>> cut = Set.of(cutVariable);

        root().fix(csp, cut, List.of(a, b, c), Set.of(), Assignment.of(Map.of(a, 1, b, 2, c, 3)));

        // a was refused, so the cut was not propagated by it; b carried it, and c needed no more.
        assertThat(inference.told).containsExactly(cut, cut, Set.of());
    }

    @Test
    void whenTheFixedPartIsAlreadyInconsistentThereIsNoNeighbourhood() {
        Variable<Integer> a = builder.createVariable("a", IntRangeDomain.of(1, 1));
        Variable<Integer> b = builder.createVariable("b", IntRangeDomain.of(1, 1));
        Variable<Integer> c = builder.createVariable("c", IntRangeDomain.of(0, 9));
        builder.notEqualsConstraint(a, b);
        ConstraintSatisfactionProblem csp = builder.build();

        Optional<NeighbourhoodRoot.Fixed> fixed = root().fix(csp, Set.of(), List.of(c), Set.of(),
                Assignment.of(Map.of(a, 1, b, 1, c, 4)));

        assertThat(fixed).isEmpty();
    }
}
