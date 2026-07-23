package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Operator;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Social Golfers Problem (CSPLib prob010): schedule {@code N_GROUPS} groups of {@code
 * N_PER_GROUP} golfers each over {@code N_ROUNDS} rounds, such that no two golfers play in the
 * same group more than once across the whole schedule.
 * <p>
 * Instance data (2 groups, 2 golfers per group, 3 rounds — 4 golfers total) is CSPLib's own
 * smallest published instance ({@code Problems/prob010/data/golfers_2_2_3.dzn} on GitHub: {@code
 * n_groups=2; n_per_group=2; n_rounds=3;}), with golfers identified by name ({@code Set<String>})
 * rather than CSPLib's own {@code 1..n_golfers} numbering, to exercise {@link SetIntervalDomain}'s
 * natural-ordering factory ({@code E extends Comparable<E>}, here {@link String}) over a
 * non-numeric element type. Modelled directly from CSPLib's reference set-variable model ({@code
 * Problems/prob010/models/golfers3.mzn}), which uses {@code round_group_golfers[r, g]: var set of
 * golfers} exactly as {@link SetIntervalDomain} here does, with three constraints:
 * <ul>
 *   <li>fixed group size ({@code card(...) = n_per_group}) — expressed here as the domain's own
 *       cardinality range {@code [N_PER_GROUP, N_PER_GROUP]} rather than a separate constraint;</li>
 *   <li>each round's groups partition the golfer set ({@code all_disjoint} plus implicit full
 *       coverage in the reference model) — one {@code partitionConstraint(round's groups, GOLFERS)}
 *       per round, rather than one {@code disjointConstraint} per group pair: with only pairwise
 *       disjointness and no explicit coverage constraint, this instance's solution count would
 *       still happen to come out the same (fixed group sizes summing exactly to {@code
 *       |GOLFERS|} force full coverage as an arithmetic side effect regardless), but {@code
 *       partitionConstraint} states the actual CSPLib requirement directly rather than relying on
 *       that coincidence;</li>
 *   <li>"each pair may play together at most once" — the reference model sums, over every
 *       (round, group), an indicator for whether a specific golfer pair is both in that group, and
 *       bounds the total by 1. Provably equivalent here to bounding {@code |Group[r1][g1] ∩
 *       Group[r2][g2]| <= 1} for every pair of groups drawn from <em>different</em> rounds via
 *       {@code intersectionCardinalityConstraint}: two golfers together in two different groups
 *       would put both golfers in the intersection of those two groups (size ≥ 2, violating the
 *       bound); groups within the <em>same</em> round are already disjoint, so a repeat within one
 *       round is structurally impossible regardless of this constraint.</li>
 * </ul>
 * The reference model's symmetry-breaking constraint (an ordering between groups within a round)
 * is commented out in the source and left out here too.
 * <p>
 * This instance is tight but genuinely feasible: with 4 golfers there are exactly {@code C(4,2) =
 * 6} distinct pairs, and 3 rounds × 2 groups of 2 covers exactly 6 pairings — a 1-factorisation of
 * the complete graph on {@code {Alice, Bob, Carol, Dave}} into its 3 perfect matchings ({Alice,
 * Bob}|{Carol, Dave}, {Alice, Carol}|{Bob, Dave}, {Alice, Dave}|{Bob, Carol}), which is unique up
 * to ordering. Every solution is one of the {@code 3! = 6} ways to assign those 3 matchings to the
 * 3 ordered rounds, times the {@code (2!)^3 = 8} ways to assign each round's own matching's two
 * pairs to its 2 ordered group slots — {@code 48} solutions total, confirmed by {@link
 * #getSolutions_findsExactlyEveryValidSchedule()} enumerating and validating every one.
 */
public class Prob010SocialGolfersTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N_GROUPS = 2;
    static final int N_PER_GROUP = 2;
    static final int N_ROUNDS = 3;
    static final Set<String> GOLFERS = Set.of("Alice", "Bob", "Carol", "Dave");
    static final int EXPECTED_SOLUTION_COUNT = 48;

    static final List<List<Variable<Set<String>>>> GROUP = IntStream.range(0, N_ROUNDS)
            .<List<Variable<Set<String>>>>mapToObj(r -> IntStream.range(0, N_GROUPS)
                    .<Variable<Set<String>>>mapToObj(g -> F.create("round" + r + "group" + g))
                    .toList())
            .toList();

    static final ConstraintSatisfactionProblem CSP = buildCsp();

    static ConstraintSatisfactionProblem buildCsp() {
        var builder = ConstraintSatisfactionProblem.builder();
        for (int r = 0; r < N_ROUNDS; r++) {
            for (int g = 0; g < N_GROUPS; g++) {
                builder.variableDomain(GROUP.get(r).get(g), SetIntervalDomain.of(Set.of(), GOLFERS, N_PER_GROUP, N_PER_GROUP));
            }
        }

        // Each round's groups partition the golfer set.
        for (int r = 0; r < N_ROUNDS; r++) {
            builder.partitionConstraint(Set.copyOf(GROUP.get(r)), GOLFERS);
        }

        // Each pair may play together at most once.
        for (int r1 = 0; r1 < N_ROUNDS; r1++) {
            for (int r2 = r1 + 1; r2 < N_ROUNDS; r2++) {
                for (int g1 = 0; g1 < N_GROUPS; g1++) {
                    for (int g2 = 0; g2 < N_GROUPS; g2++) {
                        builder.intersectionCardinalityConstraint(GROUP.get(r1).get(g1), GROUP.get(r2).get(g2), Operator.LEQ, 1);
                    }
                }
            }
        }
        return builder.build();
    }

    @Test
    void getSolution_findsAValidSchedule() {
        val solution = Solver.Factory.INSTANCE.createSolver(CSP).getSolution();
        assertThat(solution).isPresent();
        assertValidSchedule(solution.get());
        System.out.println(solution);
    }

    @Test
    void getSolutions_findsExactlyEveryValidSchedule() {
        val solutions = Solver.Factory.INSTANCE.createSolver(CSP).getSolutions().toList();
        assertThat(solutions).hasSize(EXPECTED_SOLUTION_COUNT);
        solutions.forEach(Prob010SocialGolfersTest::assertValidSchedule);
    }

    /**
     * Directly checks the three CSPLib constraints against a found assignment, independent of how
     * {@code buildCsp} decomposed them: group sizes, round-disjointness (and, since each round's
     * groups must be disjoint <em>and</em> sized exactly {@code N_PER_GROUP} each, that a round's
     * groups also fully cover {@code GOLFERS} — {@code N_GROUPS * N_PER_GROUP == |GOLFERS|}), and
     * that no golfer pair is grouped together more than once across the whole schedule.
     */
    static void assertValidSchedule(Assignment assignment) {
        Map<Set<String>, Integer> pairCounts = new HashMap<>();
        for (int r = 0; r < N_ROUNDS; r++) {
            Set<String> roundCoverage = new HashSet<>();
            for (int g = 0; g < N_GROUPS; g++) {
                Set<String> group = assignment.getValue(GROUP.get(r).get(g)).orElseThrow();
                assertThat(group).hasSize(N_PER_GROUP);
                assertThat(roundCoverage).doesNotContainAnyElementsOf(group);
                roundCoverage.addAll(group);

                List<String> members = new ArrayList<>(group);
                for (int i = 0; i < members.size(); i++) {
                    for (int j = i + 1; j < members.size(); j++) {
                        pairCounts.merge(Set.of(members.get(i), members.get(j)), 1, Integer::sum);
                    }
                }
            }
            assertThat(roundCoverage).isEqualTo(GOLFERS);
        }
        pairCounts.values().forEach(count -> assertThat(count).isLessThanOrEqualTo(1));
    }
}
