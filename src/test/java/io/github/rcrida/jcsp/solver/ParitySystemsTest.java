package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.nary.NaryConflictTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.ParitySystemConstraint;
import io.github.rcrida.jcsp.constraints.nary.ParitySystemConstraint.Row;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ParitySystemsTest {
    private static final Variable.Factory F = Variable.Factory.INSTANCE;

    private static List<Variable<Integer>> bits(String prefix, int count) {
        List<Variable<Integer>> variables = new ArrayList<>();
        for (int i = 0; i < count; i++) variables.add(F.create(prefix + i));
        return variables;
    }

    /** Every assignment of {@code variables} whose number of ones has parity {@code parity}, valued by {@code bit}. */
    private static Set<Assignment> tuplesOfParity(List<? extends Variable<?>> variables, int parity,
                                                   Function<Integer, Object> bit) {
        Set<Assignment> tuples = new HashSet<>();
        for (int mask = 0; mask < 1 << variables.size(); mask++) {
            if ((Integer.bitCount(mask) & 1) != parity) continue;
            Map<Variable<?>, Object> values = new HashMap<>();
            for (int i = 0; i < variables.size(); i++) values.put(variables.get(i), bit.apply(mask >> i & 1));
            tuples.add(Assignment.of(values));
        }
        return tuples;
    }

    private static Set<Assignment> integerTuples(List<Variable<Integer>> variables, int parity) {
        return tuplesOfParity(variables, parity, bit -> bit);
    }

    private static ConstraintSatisfactionProblem problem(List<Variable<Integer>> variables,
                                                         NaryTuplesConstraint... tables) {
        var builder = ConstraintSatisfactionProblem.builder();
        variables.forEach(v -> builder.variableDomain(v, IntRangeDomain.of(0, 1)));
        for (var table : tables) builder.constraint(table);
        return builder.build();
    }

    // --- rowOf ---

    @Test
    void allTuplesOfOneParityAreARowOfThatParity() {
        var v = bits("a", 3);

        assertThat(ParitySystems.rowOf(integerTuples(v, 1), false))
                .hasValueSatisfying(row -> {
                    assertThat((Set<?>) row.variables()).isEqualTo(Set.copyOf(v));
                    assertThat(row.parity()).isEqualTo(1);
                });
        assertThat(ParitySystems.rowOf(integerTuples(v, 0), false)).hasValueSatisfying(row -> assertThat(row.parity()).isZero());
    }

    @Test
    void forbiddenTuplesOfOneParityAreARowOfTheOther() {
        var v = bits("a", 3);

        assertThat(ParitySystems.rowOf(integerTuples(v, 0), true))
                .hasValueSatisfying(row -> assertThat(row.parity()).isEqualTo(1));
    }

    @Test
    void booleanTuplesAreRowsToo() {
        List<Variable<Boolean>> v = List.of(F.create("p"), F.create("q"), F.create("r"));

        assertThat(ParitySystems.rowOf(tuplesOfParity(v, 1, bit -> bit == 1), false))
                .hasValueSatisfying(row -> assertThat(row.parity()).isEqualTo(1));
    }

    @Test
    void tablesThatAreNotAParityAreNotRows() {
        var v = bits("a", 3);
        Set<Assignment> mixed = new HashSet<>(integerTuples(v, 0));
        mixed.remove(mixed.iterator().next());
        mixed.add(integerTuples(v, 1).iterator().next());
        Set<Assignment> outsideZeroOne = new HashSet<>(integerTuples(v, 1));
        outsideZeroOne.remove(outsideZeroOne.iterator().next());
        outsideZeroOne.add(Assignment.of(Map.of(v.get(0), 2, v.get(1), 0, v.get(2), 0)));
        Set<Assignment> notNumbers = new HashSet<>(integerTuples(v, 1));
        notNumbers.remove(notNumbers.iterator().next());
        notNumbers.add(Assignment.of(Map.of(v.get(0), "one", v.get(1), "zero", v.get(2), "zero")));

        assertThat(ParitySystems.rowOf(Set.of(), false)).isEmpty();
        assertThat(ParitySystems.rowOf(integerTuples(bits("one", 1), 1), false)).isEmpty();
        Map<Variable<?>, Object> wide = new HashMap<>();
        bits("big", ParitySystems.MAX_ARITY + 1).forEach(variable -> wide.put(variable, 0));
        assertThat(ParitySystems.rowOf(Set.of(Assignment.of(wide)), false)).isEmpty();
        assertThat(ParitySystems.rowOf(mixed, false)).isEmpty();
        assertThat(ParitySystems.rowOf(outsideZeroOne, false)).isEmpty();
        assertThat(ParitySystems.rowOf(notNumbers, false)).isEmpty();
        assertThat(ParitySystems.rowOf(integerTuples(v, 1).stream().limit(3).collect(Collectors.toSet()), false)).isEmpty();
    }

    // --- systemsOf ---

    private static Row row(int parity, Variable<?>... variables) {
        return new Row(new LinkedHashSet<>(List.of(variables)), parity);
    }

    @Test
    void rowsSharingVariablesFormOneSystemAndOthersFormTheirOwn() {
        var v = bits("a", 7);
        var systems = ParitySystems.systemsOf(List.of(
                row(1, v.get(0), v.get(1), v.get(2)), row(0, v.get(2), v.get(3), v.get(4)),
                row(1, v.get(5), v.get(6), v.get(0)), row(1, v.get(5), v.get(6), v.get(1))));

        assertThat(systems).hasSize(1);
        assertThat(systems.getFirst().getRows()).hasSize(4);
    }

    @Test
    void independentGroupsGetASystemEach() {
        var a = bits("a", 4);
        var b = bits("b", 4);
        var systems = ParitySystems.systemsOf(List.of(
                row(1, a.get(0), a.get(1), a.get(2)), row(0, a.get(1), a.get(2), a.get(3)),
                row(1, b.get(0), b.get(1), b.get(2)), row(0, b.get(1), b.get(2), b.get(3))));

        assertThat(systems).hasSize(2);
    }

    @Test
    void aLoneRowIsLeftToItsOwnTable() {
        var v = bits("a", 3);

        assertThat(ParitySystems.systemsOf(List.of(row(1, v.get(0), v.get(1), v.get(2))))).isEmpty();
    }

    @Test
    void aGroupOfPairsIsLeftToArcConsistency() {
        var v = bits("a", 3);

        assertThat(ParitySystems.systemsOf(List.of(row(1, v.get(0), v.get(1)), row(1, v.get(1), v.get(2))))).isEmpty();
    }

    @Test
    void aGroupOverTheSizeLimitIsNotPosted() {
        var v = bits("a", ParitySystems.MAX_ROWS + 3);
        List<Row> chain = new ArrayList<>();
        for (int i = 0; i <= ParitySystems.MAX_ROWS; i++) chain.add(row(1, v.get(i), v.get(i + 1), v.get(i + 2)));

        assertThat(ParitySystems.systemsOf(chain)).isEmpty();
        assertThat(ParitySystems.systemsOf(chain.subList(0, ParitySystems.MAX_ROWS))).hasSize(1);
    }

    // --- withParitySystems ---

    @Test
    void parityTablesGainASystemAndTheTablesStay() {
        var v = bits("a", 4);
        var csp = problem(v,
                NaryTuplesConstraint.of(integerTuples(v.subList(0, 3), 1)),
                NaryTuplesConstraint.of(integerTuples(v.subList(1, 4), 0)));

        var augmented = ParitySystems.withParitySystems(csp);

        assertThat(augmented.getConstraints()).hasSize(csp.getConstraints().size() + 1);
        assertThat(augmented.getConstraints()).anyMatch(ParitySystemConstraint.class::isInstance);
        assertThat(csp.getConstraints()).noneMatch(ParitySystemConstraint.class::isInstance);
    }

    @Test
    void forbiddenParityTablesCountToo() {
        var v = bits("a", 4);
        var builder = ConstraintSatisfactionProblem.builder();
        v.forEach(variable -> builder.variableDomain(variable, IntRangeDomain.of(0, 1)));
        builder.constraint(NaryConflictTuplesConstraint.of(integerTuples(v.subList(0, 3), 0)));
        builder.constraint(NaryConflictTuplesConstraint.of(integerTuples(v.subList(1, 4), 1)));

        var augmented = ParitySystems.withParitySystems(builder.build());

        assertThat(augmented.getConstraints()).anyMatch(ParitySystemConstraint.class::isInstance);
    }

    @Test
    void aProblemWithoutParityTablesIsReturnedAsItIs() {
        var v = bits("a", 3);
        var builder = ConstraintSatisfactionProblem.builder();
        v.forEach(variable -> builder.variableDomain(variable, IntRangeDomain.of(0, 1)));
        builder.allDiffConstraint(Set.copyOf(v));
        var csp = builder.build();

        assertThat(ParitySystems.withParitySystems(csp)).isSameAs(csp);
    }

    // --- solving ---

    /** x0 ^ x1 ^ x2 = 1, x1 ^ x2 ^ x3 = 0, x2 ^ x3 ^ x4 = 1, x0 ^ x3 ^ x4 = 1 over five bits. */
    private static ConstraintSatisfactionProblem satisfiableSystem(List<Variable<Integer>> v) {
        return problem(v,
                NaryTuplesConstraint.of(integerTuples(List.of(v.get(0), v.get(1), v.get(2)), 1)),
                NaryTuplesConstraint.of(integerTuples(List.of(v.get(1), v.get(2), v.get(3)), 0)),
                NaryTuplesConstraint.of(integerTuples(List.of(v.get(2), v.get(3), v.get(4)), 1)),
                NaryTuplesConstraint.of(integerTuples(List.of(v.get(0), v.get(3), v.get(4)), 1)));
    }

    /** The system's solutions by brute force over all 32 assignments. */
    private static Set<Assignment> satisfiableSolutions(List<Variable<Integer>> v) {
        Set<Assignment> solutions = new HashSet<>();
        for (int mask = 0; mask < 32; mask++) {
            int[] b = new int[5];
            for (int i = 0; i < 5; i++) b[i] = mask >> i & 1;
            if ((b[0] ^ b[1] ^ b[2]) == 1 && (b[1] ^ b[2] ^ b[3]) == 0 && (b[2] ^ b[3] ^ b[4]) == 1
                    && (b[0] ^ b[3] ^ b[4]) == 1) {
                Map<Variable<?>, Object> values = new HashMap<>();
                for (int i = 0; i < 5; i++) values.put(v.get(i), b[i]);
                solutions.add(Assignment.of(values));
            }
        }
        return solutions;
    }

    @Test
    void solvingFindsExactlyTheSolutionsOfTheTables() {
        var v = bits("s", 5);
        var csp = satisfiableSystem(v);
        var expected = satisfiableSolutions(v);

        var solutions = Solver.Factory.INSTANCE.createSolver(csp).getSolutions().collect(Collectors.toSet());

        assertThat(expected).isNotEmpty();
        assertThat(solutions).isEqualTo(expected);
    }

    @Test
    void anInconsistentSystemIsRefutedBeforeAnySearch() {
        var v = bits("u", 3);
        var csp = problem(v,
                NaryTuplesConstraint.of(integerTuples(List.of(v.get(0), v.get(1)), 1)),
                NaryTuplesConstraint.of(integerTuples(List.of(v.get(1), v.get(2)), 1)),
                NaryTuplesConstraint.of(integerTuples(List.of(v.get(0), v.get(2), v.get(1)), 1)),
                NaryTuplesConstraint.of(integerTuples(List.of(v.get(0), v.get(2)), 1)));
        var statistics = new Statistics();

        var solution = Solver.Factory.INSTANCE.createSolver(csp,
                SolverConfig.builder().statistics(statistics).build()).getSolution();

        assertThat(solution).isEmpty();
        assertThat(statistics.getNodesExplored()).isZero();
    }

    @Test
    void anOptimisationOverAParitySystemStillFindsItsOptimum() {
        var v = bits("o", 5);
        var csp = satisfiableSystem(v);

        int expected = satisfiableSolutions(v).stream().mapToInt(a -> onesOf(v, a)).min().orElseThrow();

        var best = Solver.Factory.INSTANCE.createSolver(csp, (Assignment a) -> onesOf(v, a)).getSolution();

        assertThat(best).isPresent();
        assertThat(onesOf(v, best.get())).isEqualTo(expected);
    }

    private static int onesOf(List<Variable<Integer>> variables, Assignment assignment) {
        return variables.stream().filter(assignment::isAssigned).mapToInt(x -> (Integer) assignment.getValueOrNull(x)).sum();
    }

    @Test
    void booleanParityTablesAreRecognisedToo() {
        List<Variable<Boolean>> v = List.of(F.create("b0"), F.create("b1"), F.create("b2"), F.create("b3"));
        var builder = ConstraintSatisfactionProblem.builder();
        v.forEach(variable -> builder.variableDomain(variable, BooleanDomain.INSTANCE));
        builder.constraint(NaryTuplesConstraint.of(tuplesOfParity(v.subList(0, 3), 1, bit -> bit == 1)));
        builder.constraint(NaryTuplesConstraint.of(tuplesOfParity(v.subList(1, 4), 1, bit -> bit == 1)));

        assertThat(ParitySystems.withParitySystems(builder.build()).getConstraints())
                .anyMatch(ParitySystemConstraint.class::isInstance);
    }
}
