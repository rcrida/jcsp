package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class SetBranchingSolverTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    /** Extracts singleton values from all domains — used as the terminal inner solver in tests. */
    static final Solver SINGLETON_EXTRACTOR = csp ->
            Stream.of(Assignment.of(csp.getVariableDomains().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().singleValue().orElseThrow()))));

    static SetBranchingSolver solver() {
        return SetBranchingSolver.builder().inner(SINGLETON_EXTRACTOR).build();
    }

    @Test
    void setAlreadySingleton_withNonSingletonOtherVariable_delegatesToInner() {
        // s is already singleton (no branching needed); n is a non-SetBoundedDomain variable that's
        // non-singleton -- exercises both findMostUndeterminedSet's instanceof=false filter (for n)
        // and its isSingleton=true filter (for s), leaving nothing to branch on, so isFullyDetermined()
        // is false and control must delegate to inner.
        Variable<Set<Integer>> s = F.create("s_delegate");
        Variable<Integer> n = F.create("n_delegate");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s, SetIntervalDomain.of(Set.of(1, 2), Set.of(1, 2), 2, 2))
                .variableDomain(n, IntRangeDomain.of(1, 2))
                .build();
        Solver inner = c -> List.of(1, 2).stream()
                .map(v -> Assignment.of(Map.of(s, c.getDomain(s).singleValue().orElseThrow(), n, v)));
        var solutions = SetBranchingSolver.builder().inner(inner).build().getSolutions(csp).toList();
        assertThat(solutions).hasSize(2);
    }

    @Test
    void getSolution_doesNotSkipBranchingLogic() {
        // Guards against a future change making this class inherit SolverDecorator's default
        // getSolution() (delegate straight to inner) instead of its own explicit override: inner is
        // SINGLETON_EXTRACTOR, which throws NoSuchElementException on singleValue().orElseThrow() if
        // s isn't already singleton -- it only becomes singleton via branching, so this test fails
        // loudly (not silently) if getSolution() ever bypasses branching.
        Variable<Set<Integer>> s = F.create("s_no_skip");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s, SetIntervalDomain.of(Set.of(), Set.of(1, 2), 1, 1))
                .build();
        var solution = solver().getSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(s).orElseThrow()).hasSize(1);
    }

    @Test
    void getSolutions_enumeratesAllBranches_whenNoObjective() {
        Variable<Set<Integer>> a = F.create("a_enum");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(), Set.of(1, 2), 1, 1))
                .build();
        var solutions = solver().getSolutions(csp).toList();
        assertThat(solutions.stream().map(s -> s.getValue(a).orElseThrow()).toList())
                .containsExactlyInAnyOrder(Set.of(1), Set.of(2));
    }

    @Test
    void getSolutions_filtersOutNonImprovingLaterBranch_whenObjectiveSet() {
        // Branch order is deterministic ("1" sorts before "2" by String.valueOf): {1} (cost 1.0) is
        // explored first and always kept as the initial incumbent; {2} (cost 5.0), explored second,
        // does not improve on it and must be filtered out -- proving the cross-branch incumbent
        // tracking documented on this class actually runs (without it, both would be yielded, since
        // each branch's own inner solver has no reason on its own to suppress {2}).
        Variable<Set<Integer>> a = F.create("a_obj");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(), Set.of(1, 2), 1, 1))
                .build();
        ToDoubleFunction<Assignment> objective = assignment ->
                assignment.getValue(a).orElseThrow().equals(Set.of(1)) ? 1.0 : 5.0;
        var solutions = SetBranchingSolver.builder().inner(SINGLETON_EXTRACTOR).objective(objective).build()
                .getSolutions(csp).toList();
        assertThat(solutions).hasSize(1);
        assertThat(solutions.get(0).getValue(a)).contains(Set.of(1));
    }

    @Test
    void branches_oneInfeasibleBranch_backtracksToOther() {
        // a needs exactly 1 element from {1,2}; b is already forced to {1} (its only candidate).
        // Forcing 1 into a (explored first, since "1" sorts first) conflicts with disjointConstraint
        // and must fail during repropagation; only excluding 1 (leaving a={2}) succeeds. Uses a
        // throwing inner to prove the forced solution is found via propagation+branching alone,
        // never reaching inner.
        Variable<Set<Integer>> a = F.create("a_branch");
        Variable<Set<Integer>> b = F.create("b_branch");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(), Set.of(1, 2), 1, 1))
                .variableDomain(b, SetIntervalDomain.of(Set.of(), Set.of(1), 1, 1))
                .disjointConstraint(a, b)
                .build();
        Solver failsIfCalled = c -> { throw new AssertionError("inner should not be reached"); };
        var solution = SetBranchingSolver.builder().inner(failsIfCalled).build().getSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(a)).contains(Set.of(2));
        assertThat(solution.get().getValue(b)).contains(Set.of(1));
    }

    @Test
    void solvesMultiVariableInstance_exercisingWidestFirstHeuristic() {
        // a (3 candidates) and b (2 candidates) are simultaneously undetermined at the root, forcing
        // findMostUndeterminedSet's comparator to genuinely compare them (picks a, the wider one)
        // rather than trivially returning a single-element stream's only candidate.
        Variable<Set<Integer>> a = F.create("a_multi");
        Variable<Set<Integer>> b = F.create("b_multi");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 1, 1))
                .variableDomain(b, SetIntervalDomain.of(Set.of(), Set.of(1, 2), 1, 1))
                .disjointConstraint(a, b)
                .build();
        var solutions = solver().getSolutions(csp).toList();
        var pairs = solutions.stream()
                .map(sol -> Map.entry(sol.getValue(a).orElseThrow(), sol.getValue(b).orElseThrow()))
                .toList();
        assertThat(pairs).containsExactlyInAnyOrder(
                Map.entry(Set.of(1), Set.of(2)),
                Map.entry(Set.of(2), Set.of(1)),
                Map.entry(Set.of(3), Set.of(1)),
                Map.entry(Set.of(3), Set.of(2))
        );
    }

    @Test
    void optimizationChain_wiresInSetBranchingSolver_andFindsGlobalOptimum() {
        // Exercises Solver.Factory's optimization-chain hasSets branch specifically (the
        // satisfaction-chain equivalent is already covered by every solvesEndToEnd test on the
        // individual set-constraint test classes). Branch order is deterministic ("1" before "2"
        // before "3"); costs are set up strictly decreasing in that order (30, 20, 10) so all three
        // branches pass the "cost < incumbent" filter and BoundSolver#getSolution's
        // reduce((a,b)->b) must actually walk through all of them to land on the true optimum,
        // rather than the first-explored branch coincidentally already being best.
        Variable<Set<Integer>> a = F.create("a_opt");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(), Set.of(1, 2, 3), 1, 1))
                .build();
        ToDoubleFunction<Assignment> objective = assignment -> switch (assignment.getValue(a).orElseThrow().iterator().next()) {
            case 1 -> 30.0;
            case 2 -> 20.0;
            default -> 10.0;
        };
        var solution = Solver.Factory.INSTANCE.createSolver(csp, objective).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(a)).contains(Set.of(3));
    }

    @Test
    void pickElement_respectsDomainsOwnComparator() {
        // With the default natural-order comparator, pickElement would try 1 first (see the other
        // tests above). Constructing the domain with an explicit reverse comparator instead should
        // make it try 2 first -- since there's no constraint to make that fail, the very first
        // solution found (getSolution() = findFirst()) should be {2}, not {1}, proving
        // pickElement genuinely reads the domain's own comparator rather than defaulting to natural
        // order regardless.
        Variable<Set<Integer>> a = F.create("a_reverse");
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, SetIntervalDomain.of(Set.of(), Set.of(1, 2), 1, 1, Comparator.<Integer>reverseOrder()))
                .build();
        var solution = solver().getSolution(csp);
        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(a)).contains(Set.of(2));
    }
}
