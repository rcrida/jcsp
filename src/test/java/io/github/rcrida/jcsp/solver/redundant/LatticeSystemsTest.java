package io.github.rcrida.jcsp.solver.redundant;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryPredicateConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LatticeSystemsTest {
    private final Variable.Factory variableFactory = new Variable.Factory();

    /** {@code count} 0/1 variables named {@code x0..}, issued by {@code builder}. */
    private static List<Variable<Integer>> bits(ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder, int count) {
        List<Variable<Integer>> variables = new ArrayList<>();
        for (int i = 0; i < count; i++) variables.add(builder.createVariable("x" + i, IntRangeDomain.of(0, 1)));
        return variables;
    }

    /** One {@code Σ a[i][j]·x_j = d[i]} constraint per row, over {@code a[0].length} bits. */
    private ConstraintSatisfactionProblem system(long[][] a, long[] d) {
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder = ConstraintSatisfactionProblem.builder();
        List<Variable<Integer>> v = bits(builder, a[0].length);
        for (int i = 0; i < a.length; i++) {
            Map<Variable<Integer>, Integer> coefficients = new HashMap<>();
            for (int j = 0; j < v.size(); j++) coefficients.put(v.get(j), (int) a[i][j]);
            builder.linearConstraint(coefficients, Operator.EQ, (int) d[i]);
        }
        return builder.build();
    }

    private static List<Constraint> systems(ConstraintSatisfactionProblem csp) {
        return LatticeSystems.systemsIn(csp, Cancellation.NEVER);
    }

    private static long[] halfSums(long[][] a) {
        long[] d = new long[a.length];
        for (int i = 0; i < a.length; i++) {
            long total = 0;
            for (long c : a[i]) total += c;
            d[i] = total / 2;
        }
        return d;
    }

    /** m=5, n=40, no solution, and an enumeration long enough to hit a small budget or a cancellation. */
    private static long[][] hardCoefficients() {
        Random random = new Random(1L);
        long[][] a = new long[5][40];
        for (long[] row : a) for (int j = 0; j < 40; j++) row[j] = random.nextInt(100);
        return a;
    }

    /** {@code MarketSplit-01}: exactly one solution. */
    private static final long[][] MARKET_SPLIT_01 = {
            {79, 22, 49, 32, 75, 71, 17, 55, 28, 65, 13, 23, 98, 4, 17, 44, 63, 87, 18, 8, 49, 44, 12, 44, 39, 21, 45, 0, 79, 87},
            {3, 10, 9, 4, 94, 84, 76, 12, 92, 4, 29, 57, 80, 27, 61, 97, 23, 77, 36, 41, 85, 85, 37, 49, 81, 77, 22, 26, 77, 53},
            {65, 32, 64, 27, 36, 58, 11, 12, 22, 55, 69, 51, 12, 49, 30, 26, 98, 53, 55, 34, 46, 92, 71, 84, 41, 52, 61, 15, 30, 90},
            {68, 95, 22, 32, 22, 58, 43, 86, 23, 65, 93, 92, 17, 6, 93, 99, 84, 91, 53, 39, 77, 51, 31, 48, 35, 24, 52, 48, 91, 82}};
    private static final long[] MARKET_SPLIT_01_RHS = {644, 754, 720, 860};
    private static final int[] MARKET_SPLIT_01_SOLUTION =
            {1, 0, 0, 0, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1};

    /** A generated market-split instance (right-hand sides are half the row sums) with no solution. */
    private static final long[][] MARKET_SPLIT_INFEASIBLE = {
            {17, 72, 97, 8, 32, 15, 63, 97, 57, 60, 83, 48, 26, 12, 62, 3, 49, 55, 77, 97, 98, 0, 89, 57, 34, 92, 29, 75, 13, 40},
            {3, 2, 3, 83, 69, 1, 48, 87, 27, 54, 92, 3, 67, 28, 97, 56, 63, 70, 29, 44, 29, 86, 28, 97, 58, 37, 2, 53, 71, 82},
            {12, 23, 80, 92, 37, 15, 95, 42, 92, 91, 64, 54, 64, 85, 24, 38, 36, 75, 63, 64, 50, 75, 4, 61, 31, 95, 51, 53, 85, 22},
            {46, 70, 89, 99, 86, 94, 47, 11, 56, 84, 65, 13, 99, 20, 66, 50, 47, 62, 93, 3, 60, 5, 39, 90, 78, 75, 74, 50, 82, 21}};
    private static final long[] MARKET_SPLIT_INFEASIBLE_RHS = {778, 734, 836, 887};

    // ── what is posted ──

    @Test
    void marketSplit_isPostedAsATableOfItsOneSolution() {
        ConstraintSatisfactionProblem csp = system(MARKET_SPLIT_01, MARKET_SPLIT_01_RHS);

        List<Constraint> posted = systems(csp);

        assertThat(posted).hasSize(1).first().isInstanceOf(NaryTuplesConstraint.class);
        Set<Assignment> tuples = ((NaryTuplesConstraint) posted.get(0)).getTuples();
        assertThat(tuples).hasSize(1);
        Assignment solution = tuples.iterator().next();
        for (int j = 0; j < 30; j++) {
            assertThat(solution.getValue(variableNamed(csp, "x" + j)).orElseThrow()).isEqualTo(MARKET_SPLIT_01_SOLUTION[j]);
        }
    }

    private static Variable<?> variableNamed(ConstraintSatisfactionProblem csp, String name) {
        return csp.getVariableDomains().keySet().stream().filter(v -> v.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void anInfeasibleMarketSplit_isPostedAsAnAlwaysFalseConstraint() {
        List<Constraint> posted = systems(system(MARKET_SPLIT_INFEASIBLE, MARKET_SPLIT_INFEASIBLE_RHS));

        assertThat(posted).hasSize(1).first().isInstanceOf(UnaryPredicateConstraint.class);
    }

    @Test
    void contradictoryEquations_areInfeasible() {
        List<Constraint> posted = systems(system(new long[][]{{1, 1, 1}, {1, 1, 1}}, new long[]{1, 2}));

        assertThat(posted).hasSize(1).first().isInstanceOf(UnaryPredicateConstraint.class);
    }

    @Test
    void integerSolutionsThatAreNotBinary_areInfeasible() {
        List<Constraint> posted = systems(system(new long[][]{{1, 1, 0}, {1, 0, 1}, {0, 1, 1}}, new long[]{2, 2, 0}));

        assertThat(posted).hasSize(1).first().isInstanceOf(UnaryPredicateConstraint.class);
    }

    @Test
    void severalSolutions_areAllListed() {
        long[][] a = {{1, 1, 1, 0}, {0, 0, 1, 1}};
        long[] d = {1, 1};

        List<Constraint> posted = systems(system(a, d));

        assertThat(posted).hasSize(1).first().isInstanceOf(NaryTuplesConstraint.class);
        // x0+x1+x2 = 1 and x2+x3 = 1: (x2 = 1: x0 = x1 = x3 = 0) or (x2 = 0: x3 = 1 and one of x0, x1)
        assertThat(((NaryTuplesConstraint) posted.get(0)).getTuples()).hasSize(3);
    }

    @Test
    void sumConstraints_areEquationsToo() {
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder = ConstraintSatisfactionProblem.builder();
        List<Variable<Integer>> v = bits(builder, 4);
        builder.sumConstraint(Set.of(v.get(0), v.get(1), v.get(2)), Operator.EQ, 1);
        builder.sumConstraint(Set.of(v.get(2), v.get(3)), Operator.EQ, 1);

        List<Constraint> posted = systems(builder.build());

        assertThat(posted).hasSize(1).first().isInstanceOf(NaryTuplesConstraint.class);
        assertThat(((NaryTuplesConstraint) posted.get(0)).getTuples()).hasSize(3);
    }

    @Test
    void aVariableWithZeroCoefficientsEverywhere_isNotInTheSystem() {
        List<Constraint> posted = systems(system(new long[][]{{1, 1, 0, 0}, {1, 0, 1, 0}}, new long[]{1, 1}));

        assertThat(posted).hasSize(1);
        NaryTuplesConstraint table = (NaryTuplesConstraint) posted.get(0);
        assertThat(table.getVariables()).extracting(Variable::getName).containsExactlyInAnyOrder("x0", "x1", "x2");
        assertThat(table.getTuples()).hasSize(2);
    }

    @Test
    void independentGroups_areSolvedSeparately() {
        List<Constraint> posted = systems(system(
                new long[][]{{1, 1, 0, 0, 0, 0}, {1, 0, 1, 0, 0, 0}, {0, 0, 0, 1, 1, 0}, {0, 0, 0, 1, 0, 1}},
                new long[]{1, 1, 1, 1}));

        assertThat(posted).hasSize(2).allMatch(NaryTuplesConstraint.class::isInstance);
        assertThat(posted.stream().map(c -> c.getVariables().size()).collect(Collectors.toSet())).containsExactly(3);
    }

    // ── what is left alone ──

    @Test
    void aSingleEquation_isLeftToTheSubsetSumPropagation() {
        assertThat(systems(system(new long[][]{{3, 5, 7, 2}}, new long[]{8}))).isEmpty();
    }

    @Test
    void equationsThatShareNoVariable_areEachAGroupOfOne() {
        assertThat(systems(system(new long[][]{{1, 1, 0, 0}, {0, 0, 1, 1}}, new long[]{1, 1}))).isEmpty();
        assertThat(systems(system(new long[][]{{3, 5, 0, 0}, {0, 0, 7, 2}}, new long[]{8, 9}))).isEmpty();
    }

    @Test
    void inequalities_areNotEquations() {
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder = ConstraintSatisfactionProblem.builder();
        List<Variable<Integer>> v = bits(builder, 3);
        builder.linearConstraint(Map.of(v.get(0), 1, v.get(1), 2), Operator.LEQ, 2);
        builder.linearConstraint(Map.of(v.get(1), 1, v.get(2), 2), Operator.EQ, 2);

        assertThat(systems(builder.build())).isEmpty();
    }

    @Test
    void anEquationOverAVariableWithMoreThanTwoValues_isNotUsed() {
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder = ConstraintSatisfactionProblem.builder();
        List<Variable<Integer>> v = List.of(
                builder.createVariable("x0", IntRangeDomain.of(0, 2)),
                builder.createVariable("x1", IntRangeDomain.of(0, 1)),
                builder.createVariable("x2", IntRangeDomain.of(0, 1)));
        builder.linearConstraint(Map.of(v.get(0), 1, v.get(1), 1), Operator.EQ, 1);
        builder.linearConstraint(Map.of(v.get(1), 1, v.get(2), 1), Operator.EQ, 1);

        assertThat(systems(builder.build())).isEmpty();
    }

    @Test
    void anEquationWithNoNonzeroCoefficient_isNotUsed() {
        assertThat(systems(system(new long[][]{{0, 0, 0}, {1, 1, 1}}, new long[]{0, 1}))).isEmpty();
    }

    @Test
    void aDoubleValuedEquation_isNotUsed() {
        Variable<Double> a = variableFactory.create("a");
        Variable<Double> b = variableFactory.create("b");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(a, NumericDiscreteDomain.of(0.0, 1.0))
                .variableDomain(b, NumericDiscreteDomain.of(0.0, 1.0))
                .linearConstraint(Map.of(a, 1.0, b, 1.0), Operator.EQ, 1.0)
                .linearConstraint(Map.of(a, 1.0, b, -1.0), Operator.EQ, 0.0)
                .build();

        assertThat(systems(csp)).isEmpty();
    }

    /** Integral, but not {@code Integer}: the domain's values are not the ints 0 and 1 that the system is solved over. */
    @Test
    void equationsOverOtherIntegralTypes_areNotUsed() {
        Variable<Long> la = variableFactory.create("la");
        Variable<Long> lb = variableFactory.create("lb");
        Variable<Short> sa = variableFactory.create("sa");
        Variable<Short> sb = variableFactory.create("sb");
        Variable<Byte> ba = variableFactory.create("ba");
        Variable<Byte> bb = variableFactory.create("bb");
        ConstraintSatisfactionProblem csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(la, NumericDiscreteDomain.of(0L, 1L))
                .variableDomain(lb, NumericDiscreteDomain.of(0L, 1L))
                .variableDomain(sa, NumericDiscreteDomain.of((short) 0, (short) 1))
                .variableDomain(sb, NumericDiscreteDomain.of((short) 0, (short) 1))
                .variableDomain(ba, NumericDiscreteDomain.of((byte) 0, (byte) 1))
                .variableDomain(bb, NumericDiscreteDomain.of((byte) 0, (byte) 1))
                .linearConstraint(Map.of(la, 1L, lb, 1L), Operator.EQ, 1L)
                .linearConstraint(Map.of(la, 1L, lb, -1L), Operator.EQ, 0L)
                .linearConstraint(Map.of(sa, (short) 1, sb, (short) 1), Operator.EQ, (short) 1)
                .linearConstraint(Map.of(sa, (short) 1, sb, (short) -1), Operator.EQ, (short) 0)
                .linearConstraint(Map.of(ba, (byte) 1, bb, (byte) 1), Operator.EQ, (byte) 1)
                .linearConstraint(Map.of(ba, (byte) 1, bb, (byte) -1), Operator.EQ, (byte) 0)
                .build();

        assertThat(systems(csp)).isEmpty();
    }

    /** A bound that is an integer but a coefficient that is not: only reachable through a supertype such as {@code Number}. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aNonIntegralCoefficient_isNotUsed() {
        Variable<Number> a = variableFactory.create("a");
        Variable<Number> b = variableFactory.create("b");
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder = ConstraintSatisfactionProblem.builder();
        builder.variableDomain((Variable) a, (Domain) IntRangeDomain.of(0, 1));
        builder.variableDomain((Variable) b, (Domain) IntRangeDomain.of(0, 1));
        builder.constraint(LinearBoundConstraint.<Number>of(Map.of(a, (Number) 0.5, b, (Number) 1), Operator.EQ, (Number) 1));
        builder.constraint(LinearBoundConstraint.<Number>of(Map.of(a, (Number) 1, b, (Number) 1), Operator.EQ, (Number) 1));

        assertThat(systems(builder.build())).isEmpty();
    }

    @Test
    void moreVariablesThanTheLimit_isLeftToSearch() {
        int n = LatticeSystems.MAX_VARIABLES + 1;
        long[][] a = new long[2][n];
        for (int j = 0; j < n; j++) {
            a[0][j] = 1;
            a[1][j] = j % 2;
        }

        assertThat(systems(system(a, new long[]{1, 1}))).isEmpty();
    }

    // ── what it will not claim ──

    @Test
    void anEnumerationOverTheBudget_isLeftToSearch() {
        long[][] a = hardCoefficients();

        assertThat(LatticeSystems.systemsIn(system(a, halfSums(a)), 1_000, Cancellation.NEVER)).isEmpty();
    }

    @Test
    void aCancelledEnumeration_isLeftToSearch() {
        long[][] a = hardCoefficients();
        Cancellation cancelled = new Cancellation();
        cancelled.cancel();

        assertThat(LatticeSystems.systemsIn(system(a, halfSums(a)), LatticeSystems.NODE_BUDGET, cancelled)).isEmpty();
    }

    @Test
    void moreSolutionsThanTheLimit_isLeftToSearch() {
        long[][] a = new long[2][20];
        for (int j = 0; j < 20; j++) {
            a[0][j] = 1;
            a[1][j] = j < 10 ? 1 : 0;
        }

        assertThat(systems(system(a, new long[]{10, 5}))).isEmpty();
    }

    // ── through the solver ──

    @Test
    void redundantConstraints_addTheTableAndKeepTheEquations() {
        ConstraintSatisfactionProblem csp = system(MARKET_SPLIT_01, MARKET_SPLIT_01_RHS);

        ConstraintSatisfactionProblem augmented = RedundantConstraints.withRedundantConstraints(csp, Cancellation.NEVER);

        assertThat(augmented.getConstraints()).hasSize(csp.getConstraints().size() + 1);
        assertThat(augmented.getConstraints()).anyMatch(NaryTuplesConstraint.class::isInstance);
        assertThat(csp.getConstraints()).noneMatch(NaryTuplesConstraint.class::isInstance);
    }

    @Test
    void marketSplit_isSolvedToItsOneSolution() {
        ConstraintSatisfactionProblem csp = system(MARKET_SPLIT_01, MARKET_SPLIT_01_RHS);

        List<Assignment> solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();

        assertThat(solutions).hasSize(1);
        for (int j = 0; j < 30; j++) {
            assertThat(solutions.get(0).getValue(variableNamed(csp, "x" + j)).orElseThrow()).isEqualTo(MARKET_SPLIT_01_SOLUTION[j]);
        }
    }

    @Test
    void marketSplit_firstSolutionIsTheOneSolution() {
        ConstraintSatisfactionProblem csp = system(MARKET_SPLIT_01, MARKET_SPLIT_01_RHS);

        Optional<Assignment> solution = Solver.Factory.INSTANCE.createSolver(csp).getSolution();

        assertThat(solution).isPresent();
        assertThat(solution.get().getValue(variableNamed(csp, "x0")).orElseThrow()).isEqualTo(1);
    }

    @Test
    void anInfeasibleMarketSplit_hasNoSolution() {
        ConstraintSatisfactionProblem csp = system(MARKET_SPLIT_INFEASIBLE, MARKET_SPLIT_INFEASIBLE_RHS);

        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList()).isEmpty();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolution()).isEmpty();
    }
}
