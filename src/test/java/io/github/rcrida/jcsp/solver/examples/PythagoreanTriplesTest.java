package io.github.rcrida.jcsp.solver.examples;
import io.github.rcrida.jcsp.solver.Solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Pythagorean triple discovery using ProductConstraint.
 *
 * Reformulates a² + b² = c² via the difference-of-squares identity:
 *   (c − b)(c + b) = a²
 *
 * For each fixed a, the search finds all (b, c) with a ≤ b < c using:
 *   productConstraint({c−b, c+b}, EQ, a²)  — constant bound per a value
 * plus two linearConstraints that define the auxiliary variables.
 */
public class PythagoreanTriplesTest {

    private static final Variable.Factory F = Variable.Factory.INSTANCE;

    /**
     * Builds and solves a CSP that finds all (b, c) forming a Pythagorean triple
     * with the given leg {@code a}: a ≤ b < c, where b ≤ bMax and c ≤ cMax.
     *
     * Uses auxiliary variables diff = c−b and sumVar = c+b so that
     * productConstraint({diff, sumVar}, EQ, a*a) encodes (c−b)(c+b) = a².
     */
    private static List<int[]> findTriples(int a, int bMax, int cMax) {
        Variable<Integer> b    = F.create("b");
        Variable<Integer> c    = F.create("c");
        Variable<Integer> diff = F.create("diff");   // c − b
        Variable<Integer> sumV = F.create("sumV");   // c + b

        int aSquared = a * a;

        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(b,    IntRangeDomain.of(a, bMax))
                .variableDomain(c,    IntRangeDomain.of(a + 1, cMax))
                .variableDomain(diff, IntRangeDomain.of(1, cMax - a))
                .variableDomain(sumV, IntRangeDomain.of(2 * a + 1, bMax + cMax))
                // diff = c − b  ↔  c − b − diff = 0
                .linearConstraint(Map.of(c, 1, b, -1, diff, -1), Operator.EQ, 0)
                // sumV = c + b  ↔  c + b − sumV = 0
                .linearConstraint(Map.of(c, 1, b, 1, sumV, -1), Operator.EQ, 0)
                // (c − b)(c + b) = a²
                .productConstraint(Set.of(diff, sumV), Operator.EQ, aSquared)
                .build();

        return Solver.Factory.INSTANCE.createSolver(csp).getSolutions()
                .map(sol -> new int[]{a, sol.getValue(b).orElseThrow(), sol.getValue(c).orElseThrow()})
                .toList();
    }

    @Test
    void triple_3_4_5_uniqueWithAEq3() {
        // (c−b)(c+b) = 9; factor pair (1,9) → b=4, c=5
        var triples = findTriples(3, 20, 25);
        assertThat(triples).hasSize(1);
        assertThat(triples.get(0)).containsExactly(3, 4, 5);
    }

    @Test
    void triple_5_12_13_uniqueWithAEq5() {
        // (c−b)(c+b) = 25; factor pair (1,25) → b=12, c=13
        var triples = findTriples(5, 20, 25);
        assertThat(triples).hasSize(1);
        assertThat(triples.get(0)).containsExactly(5, 12, 13);
    }

    @Test
    void triple_8_15_17_uniqueWithAEq8() {
        // (c−b)(c+b) = 64; factor pair (2,32) → b=15, c=17
        // Factor pair (4,16) → b=6 < a=8 so excluded by b ≥ a domain
        var triples = findTriples(8, 20, 25);
        assertThat(triples).hasSize(1);
        assertThat(triples.get(0)).containsExactly(8, 15, 17);
    }

    @Test
    void noTriple_whenAEq4_bGeq4() {
        // 4 is not the smallest leg of any Pythagorean triple;
        // the triple (3,4,5) is found with a=3, not a=4.
        var triples = findTriples(4, 20, 25);
        assertThat(triples).isEmpty();
    }

    @Test
    void allTriples_smallestLeg3to20_bTo20_cTo25() {
        // For each candidate smallest-leg value a, solve a separate CSP using
        // productConstraint to encode (c−b)(c+b) = a². Collect and verify.
        var found = new ArrayList<List<Integer>>();
        for (int a = 3; a <= 20; a++) {
            findTriples(a, 20, 25).forEach(t -> found.add(List.of(t[0], t[1], t[2])));
        }

        // All Pythagorean triples with a ≤ b ≤ 20 and c ≤ 25
        assertThat(found).containsExactlyInAnyOrder(
                List.of(3,  4,  5),   // primitive
                List.of(5, 12, 13),   // primitive
                List.of(6,  8, 10),   // 2 × (3,4,5)
                List.of(8, 15, 17),   // primitive
                List.of(9, 12, 15),   // 3 × (3,4,5)
                List.of(12, 16, 20),  // 4 × (3,4,5)
                List.of(15, 20, 25)   // 5 × (3,4,5)
        );

        // Every solution is a genuine Pythagorean triple
        found.forEach(t -> assertThat(t.get(0) * t.get(0) + t.get(1) * t.get(1))
                .isEqualTo(t.get(2) * t.get(2)));
    }
}
