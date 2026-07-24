package io.github.rcrida.jcsp.solver.examples.csplib;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryLogicConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Balanced Incomplete Block Design (CSPLib prob028): arrange {@code V} objects into {@code B}
 * blocks of size {@code K} each, such that every object occurs in exactly {@code R} blocks and
 * every pair of distinct objects occurs together in exactly {@code LAMBDA} blocks. Instance data
 * ({@code (V,B,R,K,LAMBDA) = (7,7,3,3,1)}) is CSPLib's own smallest published instance — this
 * particular instance is the Fano plane, the unique order-2 projective plane.
 * <p>
 * Modelled directly from CSPLib's reference model ({@code Problems/prob028/models/bibd.mzn}): a
 * {@code V x B} boolean incidence matrix {@code m[object][block]} (object belongs to block?),
 * with row sums fixed to {@code R}, column sums fixed to {@code K}, and — for every pair of
 * distinct objects — the count of blocks containing <em>both</em> fixed to {@code LAMBDA}. That
 * last requirement is a row-pair scalar product in the reference model's own algebraic terms;
 * here it's expressed by reifying an {@code AND} of each block's two membership booleans and
 * counting how many of those reified indicators are true, the same boolean/reification bridge
 * {@code Prob038SteelMillSlabDesignTest} uses where jcsp's type system needs one. No new
 * constraint type is needed for this model — every piece is an existing constraint.
 * <p>
 * A set-variable model (blocks as {@code Variable<Set<Integer>>}, pairwise block intersection via
 * {@code intersectionCardinalityConstraint(..., Operator.EQ, LAMBDA)}) was considered instead,
 * since jcsp already has strong set-CP support. It was rejected: it only works for this specific
 * instance because {@code V == B} makes it a <em>symmetric</em> BIBD, for which a dual theorem
 * happens to guarantee that any two blocks also intersect in exactly {@code LAMBDA} objects — but
 * that's a coincidental property of symmetric designs, not BIBD's actual definition, and it isn't
 * CSPLib's reference model. It would also need a second, entirely new mechanism (jcsp has no
 * constraint for "how many of these set variables contain element e") to express the row-sum
 * requirement, and would need {@code IntersectionCardinalityConstraint}'s {@code EQ} operator
 * (currently unimplemented, deferred pending a real instance that needs it) — strictly more new
 * work than the boolean model above, to express a strictly less general form of the problem.
 * <p>
 * The reference model's own symmetry-breaking constraint — lexicographic ordering between
 * consecutive rows and consecutive columns — is included directly via {@code lexConstraint},
 * since {@link Boolean} is {@link Comparable} and needs no adaptation. Building this model with
 * chained {@code lexConstraint}s over boolean rows/columns (unexercised combination before this
 * test) surfaced a genuine soundness bug in {@code LexConstraint#explainInfeasible} — see that
 * class's own Javadoc and {@code LexConstraintTest} for the fix.
 */
public class Prob028BalancedIncompleteBlockDesignTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;
    static final int V = 7;
    static final int B = 7;
    static final int R = 3;
    static final int K = 3;
    static final int LAMBDA = 1;
    static final String[] OBJECTS = {"1", "2", "3", "4", "5", "6", "7"};
    static final String[] BLOCKS = {"1", "2", "3", "4", "5", "6", "7"};

    record BibdProblem(ConstraintSatisfactionProblem csp, Variable<Boolean>[][] m) {}

    static Variable<Boolean>[] row(Variable<Boolean>[][] m, int i) {
        return m[i];
    }

    static List<Variable<Boolean>> column(Variable<Boolean>[][] m, int j) {
        List<Variable<Boolean>> col = new ArrayList<>();
        for (Variable<Boolean>[] r : m) col.add(r[j]);
        return col;
    }

    static BibdProblem buildCsp() {
        val builder = ConstraintSatisfactionProblem.builder();
        Variable<Boolean>[][] m = builder.create2dVariableArray(OBJECTS, BLOCKS, "m", BooleanDomain.INSTANCE);

        // Each object occurs in exactly R blocks (row sums).
        for (int i = 0; i < V; i++) {
            builder.countConstraint(Set.copyOf(List.of(row(m, i))), true, Operator.EQ, R);
        }

        // Each block contains exactly K objects (column sums).
        for (int j = 0; j < B; j++) {
            builder.countConstraint(Set.copyOf(column(m, j)), true, Operator.EQ, K);
        }

        // Every pair of distinct objects occurs together in exactly LAMBDA blocks (row-pair
        // scalar product): reify each block's "both objects present" indicator, then count.
        for (int i1 = 0; i1 < V; i1++) {
            for (int i2 = i1 + 1; i2 < V; i2++) {
                Variable<Boolean>[] both = new Variable[B];
                for (int j = 0; j < B; j++) {
                    both[j] = F.create("y" + i1 + "_" + i2 + "_" + j);
                    builder.variableDomain(both[j], BooleanDomain.INSTANCE);
                    builder.reifyConstraint(both[j], BinaryLogicConstraint.of(m[i1][j], LogicOperator.AND, m[i2][j]));
                }
                builder.countConstraint(Set.copyOf(List.of(both)), true, Operator.EQ, LAMBDA);
            }
        }

        // Symmetry breaking: consecutive rows and consecutive columns are lexicographically ordered.
        for (int i = 0; i < V - 1; i++) {
            builder.lexConstraint(List.of(row(m, i)), Operator.LEQ, List.of(row(m, i + 1)));
        }
        for (int j = 0; j < B - 1; j++) {
            builder.lexConstraint(column(m, j), Operator.LEQ, column(m, j + 1));
        }

        return new BibdProblem(builder.build(), m);
    }

    static final BibdProblem PROBLEM = buildCsp();

    @Test
    void getSolution_findsAValidDesign() {
        val solution = Solver.Factory.INSTANCE.createSolver(PROBLEM.csp()).getSolution();
        assertThat(solution).isPresent();
        assertThat(solution.get().isSolution(PROBLEM.csp())).isTrue();
        assertValidDesign(solution.get(), PROBLEM.m());
        printDesign(solution.get(), PROBLEM.m());
    }

    /**
     * Directly checks the three BIBD properties against a found assignment, independent of how
     * {@code buildCsp} decomposed them into booleans/reifications: row sums, column sums, and
     * every pair of distinct objects co-occurring in exactly {@code LAMBDA} blocks.
     */
    static void assertValidDesign(Assignment assignment, Variable<Boolean>[][] m) {
        boolean[][] incidence = new boolean[V][B];
        for (int i = 0; i < V; i++) {
            for (int j = 0; j < B; j++) {
                incidence[i][j] = assignment.getValue(m[i][j]).orElseThrow();
            }
        }
        for (int i = 0; i < V; i++) {
            int rowSum = 0;
            for (int j = 0; j < B; j++) if (incidence[i][j]) rowSum++;
            assertThat(rowSum).as("object %d's block count", i).isEqualTo(R);
        }
        for (int j = 0; j < B; j++) {
            int colSum = 0;
            for (int i = 0; i < V; i++) if (incidence[i][j]) colSum++;
            assertThat(colSum).as("block %d's object count", j).isEqualTo(K);
        }
        for (int i1 = 0; i1 < V; i1++) {
            for (int i2 = i1 + 1; i2 < V; i2++) {
                int together = 0;
                for (int j = 0; j < B; j++) if (incidence[i1][j] && incidence[i2][j]) together++;
                assertThat(together).as("objects %d and %d's shared blocks", i1, i2).isEqualTo(LAMBDA);
            }
        }
    }

    static void printDesign(Assignment assignment, Variable<Boolean>[][] m) {
        for (int i = 0; i < V; i++) {
            val line = new StringBuilder();
            for (int j = 0; j < B; j++) {
                if (!line.isEmpty()) line.append(" ");
                line.append(assignment.getValue(m[i][j]).orElseThrow() ? "1" : "0");
            }
            System.out.println(line);
        }
    }
}
