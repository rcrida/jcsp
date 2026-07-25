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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steiner Triple Systems (CSPLib prob044): construct a collection of 3-element "triples" drawn
 * from {@code {1, .., N}} such that every pair of points occurs together in exactly one triple.
 * CSPLib's own reference instance ({@code N = 7}) requires exactly {@code N(N-1)/6 = 7} triples —
 * the Fano plane.
 * <p>
 * Modelled directly as set variables: each triple is a {@code Variable<Set<Integer>>} with domain
 * {@link SetIntervalDomain} fixed to cardinality {@code [3, 3]} over the universe {@code {1..7}}.
 * The full Steiner property reduces to a single pairwise condition — {@code
 * intersectionCardinalityConstraint(triple_i, triple_j, Operator.LEQ, 1)} for every pair of
 * triples — by the same counting argument {@code Prob010SocialGolfersTest} relies on for its own
 * partition constraint: 7 triples of 3 points each cover {@code 7*3 = 21 = C(7,2)} point-pairs in
 * total (counted with multiplicity); bounding every pairwise triple-intersection by 1 means no
 * point-pair can be covered more than once, so — since the total coverage already equals the
 * total number of distinct pairs exactly — none can be covered fewer than once either. A genuine
 * Steiner system falls out of the pairwise bound alone, with no separate coverage constraint
 * needed (mirrored independently by {@link #assertValidSteinerSystem}, which checks the actual
 * mathematical property directly rather than trusting this argument).
 * <p>
 * <b>Symmetry breaking.</b> The 7 triples are otherwise fully interchangeable slots: any one
 * Steiner system could be assigned to {@code triple_0 .. triple_6} in {@code 7!} different ways,
 * which would inflate the true count of 30 labeled systems (see below) to {@code 30 * 7! =
 * 151200}. Breaking that slot symmetry needs a genuine boolean "is point {@code p} a member of
 * {@code triple_i}?" indicator usable in an ordering constraint — something
 * {@code subsetConstraint}/{@code disjointConstraint} alone can't provide, since they only ever
 * force hard, unconditional membership/exclusion, never produce a boolean output. This motivated
 * adding {@link SetMembershipConstraint} (reified via {@code reifyConstraint}) as a genuinely new
 * constraint type: the set-CP analogue of {@code UnaryValueConstraint}, whitelisted for
 * {@code SetBoundedDomain} variables specifically so it can be reified this way. Each triple's row
 * of 7 reified membership booleans is then lexicographically ordered against the next triple's row
 * via {@code lexConstraint} — {@link Boolean} is already {@link Comparable}, so (unlike
 * {@code Prob038SteelMillSlabDesignTest}'s integer channel) no further channelling is needed, the
 * same idiom {@code Prob028BalancedIncompleteBlockDesignTest} uses for its own row/column symmetry
 * breaking. Since no two triples can ever be identical (two identical triples would have
 * intersection size 3, violating the pairwise bound above), non-strict ({@code LEQ}) lex ordering
 * is enough to force exactly one canonical slot assignment per abstract system.
 * <p>
 * <b>Expected solution count.</b> There is a unique Steiner triple system of order 7 up to
 * point-relabelling (the Fano plane), whose automorphism group is {@code PGL(3,2)} of order 168.
 * The number of distinct systems on <em>labeled</em> points {@code 1..7} is therefore
 * {@code 7! / 168 = 5040 / 168 = 30} — confirmed by {@link
 * #getSolutions_findsExactlyEveryLabeledSystem()} enumerating and validating every one.
 */
public class Prob044SteinerTripleSystemTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int N = 7;
    static final int NUM_TRIPLES = 7;
    static final Set<Integer> POINTS = Set.of(1, 2, 3, 4, 5, 6, 7);
    static final int EXPECTED_SOLUTION_COUNT = 30;

    static final List<Variable<Set<Integer>>> TRIPLE = IntStream.range(0, NUM_TRIPLES)
            .<Variable<Set<Integer>>>mapToObj(i -> F.create("triple" + i))
            .toList();

    /** {@code MEMBER[i][p-1]} -- boolean, reified as {@code p in TRIPLE[i]}. */
    static final List<List<Variable<Boolean>>> MEMBER = IntStream.range(0, NUM_TRIPLES)
            .<List<Variable<Boolean>>>mapToObj(i -> IntStream.rangeClosed(1, N)
                    .<Variable<Boolean>>mapToObj(p -> F.create("member" + i + "_" + p))
                    .toList())
            .toList();

    static final ConstraintSatisfactionProblem CSP = buildCsp();

    static ConstraintSatisfactionProblem buildCsp() {
        var builder = ConstraintSatisfactionProblem.builder();
        for (int i = 0; i < NUM_TRIPLES; i++) {
            builder.variableDomain(TRIPLE.get(i), SetIntervalDomain.of(Set.of(), POINTS, 3, 3));
            for (int p = 1; p <= N; p++) {
                Variable<Boolean> member = MEMBER.get(i).get(p - 1);
                builder.variableDomain(member, BooleanDomain.INSTANCE);
                builder.reifyConstraint(member, SetMembershipConstraint.of(TRIPLE.get(i), p));
            }
        }

        // Any two triples share at most one point -- forces full, exactly-once pair coverage by
        // the counting argument in the class Javadoc.
        for (int i = 0; i < NUM_TRIPLES; i++) {
            for (int j = i + 1; j < NUM_TRIPLES; j++) {
                builder.intersectionCardinalityConstraint(TRIPLE.get(i), TRIPLE.get(j), Operator.LEQ, 1);
            }
        }

        // Symmetry breaking: consecutive triples' membership rows are lexicographically ordered.
        for (int i = 0; i < NUM_TRIPLES - 1; i++) {
            builder.lexConstraint(MEMBER.get(i), Operator.LEQ, MEMBER.get(i + 1));
        }

        return builder.build();
    }

    @Test
    void getSolution_findsAValidSteinerSystem() {
        val solution = Solver.Factory.INSTANCE.createSolver(CSP).getSolution();
        assertThat(solution).isPresent();
        assertValidSteinerSystem(solution.get());
        System.out.println(solution);
    }

    @Test
    void getSolutions_findsExactlyEveryLabeledSystem() {
        val solutions = Solver.Factory.INSTANCE.createSolver(CSP).getSolutions().toList();
        assertThat(solutions).hasSize(EXPECTED_SOLUTION_COUNT);
        solutions.forEach(Prob044SteinerTripleSystemTest::assertValidSteinerSystem);
    }

    /**
     * Directly checks the actual Steiner triple system property against a found assignment,
     * independent of how {@code buildCsp} derived it: every triple has exactly 3 points, all 7
     * triples are pairwise distinct, and every one of the {@code C(7,2) = 21} point-pairs is
     * covered by exactly one triple.
     */
    static void assertValidSteinerSystem(Assignment assignment) {
        List<Set<Integer>> triples = TRIPLE.stream().map(t -> assignment.getValue(t).orElseThrow()).toList();
        triples.forEach(triple -> assertThat(triple).hasSize(3));
        assertThat(new HashSet<>(triples)).hasSize(NUM_TRIPLES);

        Map<Set<Integer>, Integer> pairCounts = new HashMap<>();
        for (Set<Integer> triple : triples) {
            List<Integer> members = triple.stream().sorted().toList();
            for (int a = 0; a < members.size(); a++) {
                for (int b = a + 1; b < members.size(); b++) {
                    pairCounts.merge(Set.of(members.get(a), members.get(b)), 1, Integer::sum);
                }
            }
        }
        assertThat(pairCounts).hasSize(N * (N - 1) / 2);
        pairCounts.values().forEach(count -> assertThat(count).isEqualTo(1));
    }
}
