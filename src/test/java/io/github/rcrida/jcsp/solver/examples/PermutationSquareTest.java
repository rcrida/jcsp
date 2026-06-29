package io.github.rcrida.jcsp.solver.examples;
import io.github.rcrida.jcsp.solver.Solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Permutation involution puzzle modelled as a CSP using {@code elementVariableConstraint}.
 *
 * <p>An involution is a permutation p of {1..n} that equals its own inverse:
 * {@code p(p(i)) == i} for every i. The {@code elementVariableConstraint}
 * binds each result variable to {@code p[p[i]]}, which is then fixed to i.
 *
 * <p>The 10 involutions of {1,2,3,4} are:
 * <ul>
 *   <li>[1,2,3,4] — identity</li>
 *   <li>[2,1,3,4], [3,2,1,4], [4,2,3,1], [1,3,2,4], [1,4,3,2], [1,2,4,3] — one transposition</li>
 *   <li>[2,1,4,3], [3,4,1,2], [4,3,2,1] — two disjoint transpositions</li>
 * </ul>
 */
public class PermutationSquareTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    static final Variable<Integer> P1 = F.create("p1");
    static final Variable<Integer> P2 = F.create("p2");
    static final Variable<Integer> P3 = F.create("p3");
    static final Variable<Integer> P4 = F.create("p4");
    static final List<Variable<Integer>> P = List.of(P1, P2, P3, P4);

    // r[i] = p[p[i]]; constrained to equal i
    static final Variable<Integer> R1 = F.create("r1");
    static final Variable<Integer> R2 = F.create("r2");
    static final Variable<Integer> R3 = F.create("r3");
    static final Variable<Integer> R4 = F.create("r4");

    static ConstraintSatisfactionProblem problem() {
        val domain = IntRangeDomain.of(1, 4);
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(P1, domain).variableDomain(P2, domain)
                .variableDomain(P3, domain).variableDomain(P4, domain)
                .variableDomain(R1, domain).variableDomain(R2, domain)
                .variableDomain(R3, domain).variableDomain(R4, domain)
                .allDiffConstraint(Set.of(P1, P2, P3, P4))   // p is a permutation
                .elementVariableConstraint(P1, R1, P)         // r1 = p[p1]
                .elementVariableConstraint(P2, R2, P)         // r2 = p[p2]
                .elementVariableConstraint(P3, R3, P)         // r3 = p[p3]
                .elementVariableConstraint(P4, R4, P)         // r4 = p[p4]
                .equalsConstraint(R1, 1)                      // p(p(1)) = 1
                .equalsConstraint(R2, 2)                      // p(p(2)) = 2
                .equalsConstraint(R3, 3)                      // p(p(3)) = 3
                .equalsConstraint(R4, 4)                      // p(p(4)) = 4
                .build();
    }

    @Test
    void involutions_of_4_elements() {
        val solutions = Solver.Factory.INSTANCE.createSolver(problem()).getSolutions().toList();
        assertThat(solutions).hasSize(10);
        solutions.forEach(System.out::println);
    }
}
