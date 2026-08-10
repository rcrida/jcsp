package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.unary.SetMembershipConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Number Partitioning (CSPLib prob049): split {@code {1, .., N}} into two sets {@code A} and
 * {@code B} of equal cardinality such that {@code sum(A) = sum(B)} and
 * {@code sumOfSquares(A) = sumOfSquares(B)}. No solution exists for {@code N < 8}, so {@code N =
 * 8} is the smallest satisfiable instance.
 * <p>
 * Modelled as two {@code Variable<Set<Integer>>} with {@link SetIntervalDomain} fixed to
 * cardinality {@code [N/2, N/2]} over the universe {@code {1..N}}, related by
 * {@code disjointConstraint(A, B)} rather than {@code partitionConstraint} (already exercised by
 * {@code Prob010SocialGolfersTest}/{@code Prob044SteinerTripleSystemTest}) — CSPLib's own
 * reference models ({@code Problems/prob049/models/set_partition.ecl} on GitHub) build this same
 * problem from exactly {@code all_disjoint(Sets)} plus a separate {@code all_union(Sets,
 * Universe)}. The union half is redundant here rather than dropped: two disjoint subsets of an
 * {@code N}-element universe whose sizes sum to {@code N} cover it completely by a simple counting
 * argument, so fixing each set's cardinality to exactly {@code N/2} makes full coverage a
 * consequence of {@code disjointConstraint} alone, with no separate constraint needed (mirrored
 * independently by {@link #assertValidPartition}, which checks {@code A ∪ B = universe} directly
 * rather than trusting this argument). {@code A}/{@code B} are otherwise interchangeable (swapping
 * them yields the same partition), broken by a plain, non-reified
 * {@code setMembershipConstraint(A, 1)} fixing element 1 to always land in {@code A} — the
 * unreified sibling of {@code Prob044SteinerTripleSystemTest}'s reified use of the same constraint.
 * <p>
 * The sum/sum-of-squares equalities need per-element membership as a genuine numeric weight, which
 * a reified {@code Variable<Boolean>} alone can't feed into {@code linearConstraint} (its
 * coefficient map requires the variable and the weight to share one {@link Number} type). This
 * motivated adding {@code linearBooleanConstraint} — the boolean-indicator sibling of {@code
 * linearConstraint}, treating each {@code true} as contributing its coefficient and each
 * {@code false} as contributing zero. Each {@code MEMBER_A[k]} (reified against {@code
 * setMembershipConstraint(A, k)}) is weighted directly by {@code k} (and separately by {@code
 * k*k}) into two {@code linearBooleanConstraint} calls, pinned to the exact midpoint of the total
 * sum/sum-of-squares — sound because {@code sum(A) + sum(B) = sum(1..N)} always holds once
 * {@code A}/{@code B} partition the universe, so {@code sum(A) = sum(B)} reduces to {@code sum(A)
 * = sum(1..N)/2} (no separate {@code B}-side sum or {@code SUM_A} target variable is needed).
 * <p>
 * <b>Expected solution count.</b> Rather than hardcode a count sourced from outside this test,
 * {@link #bruteForceValidPartitions()} independently enumerates every candidate partition (all
 * {@code C(N-1, N/2-1)} ways to complete {@code A} around the fixed element 1) directly in Java and
 * checks the same sum/sum-of-squares property; {@link #getSolutions_matchesBruteForceEnumeration()}
 * checks the solver's own enumeration against that count.
 */
public class Prob049NumberPartitioningTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N = 8;
    static final int HALF = N / 2;

    record PartitionProblem(ConstraintSatisfactionProblem csp, Set<Integer> universe,
                             Variable<Set<Integer>> a, Variable<Set<Integer>> b, List<Variable<Boolean>> memberA) {}

    /** Universe size is a parameter, so a larger instance can be built (e.g. for benchmarking). */
    static PartitionProblem buildCsp(int n) {
        int half = n / 2;
        Set<Integer> universe = IntStream.rangeClosed(1, n).boxed().collect(Collectors.toSet());
        int totalSum = IntStream.rangeClosed(1, n).sum();
        int totalSumOfSquares = IntStream.rangeClosed(1, n).map(k -> k * k).sum();

        Variable<Set<Integer>> a = F.create("A");
        Variable<Set<Integer>> b = F.create("B");
        // memberA[k-1] -- boolean, reified as "k in a".
        List<Variable<Boolean>> memberA = IntStream.rangeClosed(1, n)
                .<Variable<Boolean>>mapToObj(k -> F.create("memberA" + k))
                .toList();

        var builder = ConstraintSatisfactionProblem.builder();
        var setDomain = SetIntervalDomain.of(Set.of(), universe, half, half);
        builder.variableDomain(a, setDomain);
        builder.variableDomain(b, setDomain);
        builder.disjointConstraint(a, b);
        builder.setMembershipConstraint(a, 1);

        Map<Variable<Boolean>, Integer> weights = new HashMap<>();
        Map<Variable<Boolean>, Integer> squaredWeights = new HashMap<>();
        for (int k = 1; k <= n; k++) {
            Variable<Boolean> member = memberA.get(k - 1);
            builder.variableDomain(member, BooleanDomain.INSTANCE);
            builder.reifyConstraint(member, SetMembershipConstraint.of(a, k));
            weights.put(member, k);
            squaredWeights.put(member, k * k);
        }

        builder.linearBooleanConstraint(weights, Operator.EQ, totalSum / 2);
        builder.linearBooleanConstraint(squaredWeights, Operator.EQ, totalSumOfSquares / 2);

        return new PartitionProblem(builder.build(), universe, a, b, memberA);
    }

    static final PartitionProblem PROBLEM = buildCsp(N);
    static final ConstraintSatisfactionProblem CSP = PROBLEM.csp();
    static final Set<Integer> UNIVERSE = PROBLEM.universe();
    static final Variable<Set<Integer>> A = PROBLEM.a();
    static final Variable<Set<Integer>> B = PROBLEM.b();
    static final List<Variable<Boolean>> MEMBER_A = PROBLEM.memberA();

    /**
     * Every candidate partition with element 1 fixed in A, checked directly against the problem's
     * mathematical property rather than derived from the CSP model.
     */
    static List<Set<Integer>> bruteForceValidPartitions() {
        List<Integer> rest = new ArrayList<>(UNIVERSE);
        rest.remove(Integer.valueOf(1));
        List<Set<Integer>> valid = new ArrayList<>();
        int size = rest.size();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                for (int k = j + 1; k < size; k++) {
                    Set<Integer> a = Set.of(1, rest.get(i), rest.get(j), rest.get(k));
                    Set<Integer> b = new HashSet<>(UNIVERSE);
                    b.removeAll(a);
                    if (sum(a) == sum(b) && sumOfSquares(a) == sumOfSquares(b)) valid.add(a);
                }
            }
        }
        return valid;
    }

    static int sum(Set<Integer> s) {
        return s.stream().mapToInt(Integer::intValue).sum();
    }

    static int sumOfSquares(Set<Integer> s) {
        return s.stream().mapToInt(x -> x * x).sum();
    }

    @Test
    void getSolutions_matchesBruteForceEnumeration() {
        List<Set<Integer>> expected = bruteForceValidPartitions();
        assertThat(expected).isNotEmpty();

        val solutions = Solver.Factory.INSTANCE.createSolver(CSP).getSolutions().toList();
        assertThat(solutions).hasSize(expected.size());

        Set<Set<Integer>> foundAValues = new HashSet<>();
        for (Assignment solution : solutions) {
            assertValidPartition(solution);
            foundAValues.add(solution.getValue(A).orElseThrow());
        }
        assertThat(foundAValues).isEqualTo(new HashSet<>(expected));
    }

    static void assertValidPartition(Assignment assignment) {
        Set<Integer> a = assignment.getValue(A).orElseThrow();
        Set<Integer> b = assignment.getValue(B).orElseThrow();
        assertThat(a).hasSize(HALF);
        assertThat(b).hasSize(HALF);
        assertThat(a).doesNotContainAnyElementsOf(b);

        Set<Integer> union = new HashSet<>(a);
        union.addAll(b);
        assertThat(union).isEqualTo(UNIVERSE);

        assertThat(sum(a)).isEqualTo(sum(b));
        assertThat(sumOfSquares(a)).isEqualTo(sumOfSquares(b));
    }

    @Test
    void knownValidPartition_isASolutionOfThisModel() {
        Set<Integer> a = Set.of(1, 4, 6, 7);
        Set<Integer> b = Set.of(2, 3, 5, 8);
        assertThat(sum(a)).isEqualTo(sum(b));
        assertThat(sumOfSquares(a)).isEqualTo(sumOfSquares(b));

        Map<Variable<?>, Object> values = new HashMap<>();
        values.put(A, a);
        values.put(B, b);
        for (int k = 1; k <= N; k++) {
            values.put(MEMBER_A.get(k - 1), a.contains(k));
        }

        val assignment = Assignment.of(values);
        assertThat(assignment.isSolution(CSP)).isTrue();
    }
}
