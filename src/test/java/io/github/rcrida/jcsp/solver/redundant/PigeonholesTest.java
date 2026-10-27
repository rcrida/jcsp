package io.github.rcrida.jcsp.solver.redundant;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.assignments.Statistics;
import io.github.rcrida.jcsp.constraints.nary.NaryConflictTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.PigeonholeConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.IntervalDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.solver.SolverConfig;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PigeonholesTest {
    private final Variable.Factory F = new Variable.Factory();

    private static Set<Variable<?>> set(Variable<?>... variables) {
        return new LinkedHashSet<>(List.of(variables));
    }

    private Variable<Integer> bit(String name) {
        return F.create(name);
    }

    /** {@code variables} all valued {@code value}, as the one forbidden tuple of a conflict table. */
    private static NaryConflictTuplesConstraint forbidAll(List<? extends Variable<?>> variables, Object value) {
        Map<Variable<?>, Object> values = new HashMap<>();
        variables.forEach(v -> values.put(v, value));
        return NaryConflictTuplesConstraint.of(Set.of(Assignment.of(values)));
    }

    /** {@code pigeons} rows of {@code holes} cells: each row a clause, each column pairwise at most one. */
    private List<List<Variable<Integer>>> cells(int pigeons, int holes) {
        List<List<Variable<Integer>>> grid = new ArrayList<>();
        for (int p = 0; p < pigeons; p++) {
            List<Variable<Integer>> row = new ArrayList<>();
            for (int h = 0; h < holes; h++) row.add(bit("x" + p + "_" + h));
            grid.add(row);
        }
        return grid;
    }

    private static ConstraintSatisfactionProblem problem(List<List<Variable<Integer>>> grid) {
        var builder = ConstraintSatisfactionProblem.builder();
        grid.forEach(row -> row.forEach(v -> builder.variableDomain(v, IntRangeDomain.of(0, 1))));
        grid.forEach(row -> builder.constraint(forbidAll(row, 0)));
        for (int h = 0; h < grid.getFirst().size(); h++) {
            for (int p = 0; p < grid.size(); p++) {
                for (int q = p + 1; q < grid.size(); q++) {
                    builder.constraint(forbidAll(List.of(grid.get(p).get(h), grid.get(q).get(h)), 1));
                }
            }
        }
        return builder.build();
    }

    // --- pigeonholesIn ---

    @Test
    void morePigeonsThanHolesIsOneConstraint() {
        var found = Pigeonholes.pigeonholesIn(problem(cells(3, 2)));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getRelation()).isEqualTo("Pigeonhole[3 pigeons, 2 holes]");
    }

    @Test
    void moreHolesThanPigeonsHasNothingToRefute() {
        assertThat(Pigeonholes.pigeonholesIn(problem(cells(2, 3)))).isEmpty();
    }

    @Test
    void aLonePigeonIsNotWorthCounting() {
        assertThat(Pigeonholes.pigeonholesIn(problem(cells(1, 1)))).isEmpty();
    }

    @Test
    void atMostOneAndExactlyOneConstraintsAreHolesToo() {
        List<Variable<Boolean>> row0 = List.of(F.create("p0"), F.create("p1"));
        List<Variable<Boolean>> row1 = List.of(F.create("q0"), F.create("q1"));
        List<Variable<Boolean>> row2 = List.of(F.create("r0"), F.create("r1"));
        var builder = ConstraintSatisfactionProblem.builder();
        List.of(row0, row1, row2).forEach(row -> row.forEach(v -> builder.variableDomain(v, BooleanDomain.INSTANCE)));
        List.of(row0, row1, row2).forEach(row -> builder.constraint(forbidAll(row, false)));
        builder.atMostOneConstraint(Set.of(row0.get(0), row1.get(0), row2.get(0)));
        builder.exactlyOneConstraint(Set.of(row0.get(1), row1.get(1), row2.get(1)));

        var found = Pigeonholes.pigeonholesIn(builder.build());

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getRelation()).isEqualTo("Pigeonhole[3 pigeons, 2 holes]");
    }

    @Test
    void tablesThatAreNeitherClausesNorPairsAreIgnored() {
        var wide = List.of(bit("w0"), bit("w1"));
        var mixed = List.of(bit("m0"), bit("m1"));
        var triple = List.of(bit("t0"), bit("t1"), bit("t2"));
        var single = bit("s");
        var builder = ConstraintSatisfactionProblem.builder();
        wide.forEach(v -> builder.variableDomain(v, IntRangeDomain.of(0, 2)));
        mixed.forEach(v -> builder.variableDomain(v, IntRangeDomain.of(0, 1)));
        triple.forEach(v -> builder.variableDomain(v, IntRangeDomain.of(0, 1)));
        builder.variableDomain(single, IntRangeDomain.of(0, 1));
        builder.constraint(forbidAll(wide, 0));
        builder.constraint(NaryConflictTuplesConstraint.of(Set.of(Assignment.of(Map.of(mixed.get(0), 0, mixed.get(1), 1)))));
        builder.constraint(forbidAll(triple, 1));
        builder.constraint(forbidAll(List.of(single), 0));
        builder.constraint(NaryConflictTuplesConstraint.of(Set.of(
                Assignment.of(Map.of(mixed.get(0), 1, mixed.get(1), 1)), Assignment.of(Map.of(mixed.get(0), 0, mixed.get(1), 0)))));

        assertThat(Pigeonholes.pigeonholesIn(builder.build())).isEmpty();
    }

    @Test
    void tooManyPairsSkipTheCliqueSearchAndSoTheHoles() {
        var csp = problem(cells(3, 2));

        assertThat(Pigeonholes.pigeonholesIn(csp)).hasSize(1);
        assertThat(Pigeonholes.pigeonholesIn(csp, 1)).isEmpty();
    }

    @Test
    void onlyBitDomainsCanHoldAPigeonsVariables() {
        assertThat(Pigeonholes.isBitDomain(IntRangeDomain.of(0, 1))).isTrue();
        assertThat(Pigeonholes.isBitDomain(BooleanDomain.INSTANCE)).isTrue();
        assertThat(Pigeonholes.isBitDomain(IntRangeDomain.of(0, 2))).isFalse();
        assertThat(Pigeonholes.isBitDomain(IntervalDomain.of(0, 1))).isFalse();
    }

    // --- cliquesOf, disjoint, groupsOf ---

    private static Map<Variable<?>, Set<Variable<?>>> graph(Variable<?>[]... edges) {
        Map<Variable<?>, Set<Variable<?>>> neighbours = new HashMap<>();
        for (Variable<?>[] edge : edges) {
            neighbours.computeIfAbsent(edge[0], key -> new HashSet<>()).add(edge[1]);
            neighbours.computeIfAbsent(edge[1], key -> new HashSet<>()).add(edge[0]);
        }
        return neighbours;
    }

    @Test
    void cliquesAreGrownThroughEveryVariable() {
        var a = bit("a");
        var b = bit("b");
        var c = bit("c");
        var d = bit("d");

        var cliques = Pigeonholes.cliquesOf(graph(
                new Variable<?>[]{a, b}, new Variable<?>[]{a, c}, new Variable<?>[]{b, c}, new Variable<?>[]{c, d}));

        assertThat(cliques).containsExactlyInAnyOrder(set(a, b, c), set(c, d));
    }

    @Test
    void clausesSharingAVariableKeepOnlyTheFirstByName() {
        var a = bit("a");
        var b = bit("b");
        var c = bit("c");
        var d = bit("d");
        var e = bit("e");

        var kept = Pigeonholes.disjoint(List.of(set(d, e), set(b, c), set(a, b)));

        assertThat(kept).containsExactly(set(a, b), set(d, e));
    }

    @Test
    void aVariableInNoCliqueGetsAHoleOfItsOwn() {
        var a = bit("a");
        var b = bit("b");
        var c = bit("c");
        var outside = bit("outside");
        var elsewhere = bit("elsewhere");

        var found = Pigeonholes.groupsOf(List.of(set(a, b), set(c)), List.of(set(a, c, outside), set(elsewhere)));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getHoles()).containsExactly(set(a, c), set(b));
    }

    @Test
    void independentGroupsGetAConstraintEach() {
        var v = new ArrayList<Variable<Integer>>();
        for (int i = 0; i < 8; i++) v.add(bit("v" + i));

        var found = Pigeonholes.groupsOf(
                List.of(set(v.get(0), v.get(1)), set(v.get(2), v.get(3)), set(v.get(4), v.get(5)), set(v.get(6), v.get(7))),
                List.of(set(v.get(0), v.get(2)), set(v.get(1), v.get(3)), set(v.get(4), v.get(6)), set(v.get(5), v.get(7))));

        assertThat(found).hasSize(2);
    }

    @Test
    void aGroupBeyondTheSizeLimitIsNotPosted() {
        var pigeons = new ArrayList<Set<Variable<?>>>();
        var hole = new LinkedHashSet<Variable<?>>();
        for (int i = 0; i <= Pigeonholes.MAX_PIGEONS; i++) {
            Variable<Integer> v = bit("g" + i);
            pigeons.add(set(v));
            hole.add(v);
        }

        assertThat(Pigeonholes.groupsOf(pigeons, List.of(hole))).isEmpty();
        assertThat(Pigeonholes.groupsOf(pigeons.subList(0, Pigeonholes.MAX_PIGEONS), List.of(
                hole.stream().limit(Pigeonholes.MAX_PIGEONS).collect(Collectors.toCollection(LinkedHashSet::new))))).hasSize(1);
    }

    // --- solving ---

    @Test
    void morePigeonsThanHolesIsRefutedBeforeAnySearch() {
        var statistics = new Statistics();

        var solution = Solver.Factory.INSTANCE.createSolver(problem(cells(6, 5)),
                SolverConfig.builder().statistics(statistics).build()).getSolution();

        assertThat(solution).isEmpty();
        assertThat(statistics.getNodesExplored()).isZero();
    }

    @Test
    void solvingFindsExactlyTheSolutionsOfTheClausesAndPairs() {
        var grid = cells(2, 2);
        Set<Assignment> expected = new HashSet<>();
        for (int mask = 0; mask < 16; mask++) {
            int[][] cell = {{mask & 1, mask >> 1 & 1}, {mask >> 2 & 1, mask >> 3 & 1}};
            boolean pigeonsHoused = cell[0][0] + cell[0][1] > 0 && cell[1][0] + cell[1][1] > 0;
            boolean holesExclusive = cell[0][0] + cell[1][0] <= 1 && cell[0][1] + cell[1][1] <= 1;
            if (pigeonsHoused && holesExclusive) {
                Map<Variable<?>, Object> values = new HashMap<>();
                for (int p = 0; p < 2; p++) for (int h = 0; h < 2; h++) values.put(grid.get(p).get(h), cell[p][h]);
                expected.add(Assignment.of(values));
            }
        }

        var solutions = Solver.Factory.INSTANCE.createSolver(problem(grid)).getSolutions().collect(Collectors.toSet());

        assertThat(expected).hasSize(2);
        assertThat(solutions).isEqualTo(expected);
    }

    @Test
    void anOptimisationOverPigeonsAndHolesStillFindsItsOptimum() {
        var grid = cells(2, 2);

        var best = Solver.Factory.INSTANCE.createSolver(problem(grid), (Assignment a) -> grid.stream()
                .flatMap(List::stream).filter(a::isAssigned).mapToInt(v -> (Integer) a.getValueOrNull(v)).sum()).getSolution();

        assertThat(best).isPresent();
        assertThat(grid.stream().flatMap(List::stream).mapToInt(v -> (Integer) best.get().getValueOrNull(v)).sum()).isEqualTo(2);
    }

    @Test
    void theFoundConstraintIsAPigeonhole() {
        assertThat(Pigeonholes.pigeonholesIn(problem(cells(3, 2))).getFirst()).isInstanceOf(PigeonholeConstraint.class);
    }
}
