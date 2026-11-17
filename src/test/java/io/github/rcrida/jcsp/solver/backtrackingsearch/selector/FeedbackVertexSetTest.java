package io.github.rcrida.jcsp.solver.backtrackingsearch.selector;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackVertexSetTest {
    private final Variable.Factory variables = new Variable.Factory();

    private List<Variable<Integer>> create(String prefix, int count) {
        List<Variable<Integer>> created = new ArrayList<>();
        for (int i = 0; i < count; i++) created.add(variables.create(prefix + i));
        return created;
    }

    /** One not-equals constraint per edge, over the given variables. */
    private static Set<Constraint> graph(List<Variable<Integer>> all, List<List<Variable<Integer>>> edges) {
        var builder = ConstraintSatisfactionProblem.builder();
        for (Variable<Integer> v : all) builder.variableDomain(v, IntRangeDomain.of(1, 3));
        for (List<Variable<Integer>> edge : edges) builder.notEqualsConstraint(edge.get(0), edge.get(1));
        return new HashSet<>(builder.build().getConstraints());
    }

    /** Whether the edges with no end in {@code cutset} form a forest. */
    private static boolean leavesAForest(List<List<Variable<Integer>>> edges, Set<Variable<?>> cutset) {
        Map<Variable<?>, Variable<?>> parent = new HashMap<>();
        for (List<Variable<Integer>> edge : edges) {
            if (cutset.contains(edge.get(0)) || cutset.contains(edge.get(1))) continue;
            Variable<?> a = root(parent, edge.get(0));
            Variable<?> b = root(parent, edge.get(1));
            if (a == b) return false;
            parent.put(a, b);
        }
        return true;
    }

    private static Variable<?> root(Map<Variable<?>, Variable<?>> parent, Variable<?> v) {
        while (parent.containsKey(v)) v = parent.get(v);
        return v;
    }

    @Test
    void aPathNeedsNoCutset() {
        List<Variable<Integer>> v = create("p", 3);
        var edges = List.of(List.of(v.get(0), v.get(1)), List.of(v.get(1), v.get(2)));

        assertThat(FeedbackVertexSet.within(graph(v, edges), 0)).contains(Set.of());
    }

    @Test
    void aTriangleNeedsOneVariable() {
        List<Variable<Integer>> v = create("t", 3);
        var edges = List.of(List.of(v.get(0), v.get(1)), List.of(v.get(1), v.get(2)), List.of(v.get(2), v.get(0)));

        assertThat(FeedbackVertexSet.within(graph(v, edges), 1)).hasValueSatisfying(cutset -> {
            assertThat(cutset).hasSize(1);
            assertThat(leavesAForest(edges, cutset)).isTrue();
        });
        assertThat(FeedbackVertexSet.within(graph(v, edges), 0)).isEmpty();
    }

    @Test
    void aHubOnNoCycleIsReturnedToTheForest() {
        // h joins four triangles that share nothing else, so it lies on no cycle; it is still the
        // highest-degree variable, and cut first, until the final pass finds it redundant.
        Variable<Integer> h = variables.create("h");
        List<Variable<Integer>> all = new ArrayList<>(List.of(h));
        List<List<Variable<Integer>>> edges = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<Variable<Integer>> triangle = create("y" + i + "_", 3);
            all.addAll(triangle);
            edges.add(List.of(h, triangle.get(0)));
            edges.add(List.of(triangle.get(0), triangle.get(1)));
            edges.add(List.of(triangle.get(1), triangle.get(2)));
            edges.add(List.of(triangle.get(2), triangle.get(0)));
        }

        assertThat(FeedbackVertexSet.within(graph(all, edges), 13)).hasValueSatisfying(cutset -> {
            assertThat(cutset).hasSize(4).doesNotContain(h);
            assertThat(leavesAForest(edges, cutset)).isTrue();
        });
    }

    @Test
    void aScopeTooWideForTheLimitIsRefusedUnbuilt() {
        List<Variable<Integer>> v = create("w", 5);
        var builder = ConstraintSatisfactionProblem.builder();
        for (Variable<Integer> x : v) builder.variableDomain(x, IntRangeDomain.of(1, 5));
        builder.allDiffConstraint(new HashSet<>(v));

        assertThat(FeedbackVertexSet.within(new HashSet<>(builder.build().getConstraints()), 2)).isEmpty();
        assertThat(FeedbackVertexSet.within(new HashSet<>(builder.build().getConstraints()), 3)).isPresent();
    }

    @Test
    void aGraphOverTheAdjacencyBudgetIsRefusedUnbuilt() {
        int width = (int) Math.sqrt(FeedbackVertexSet.MAX_ADJACENCY) + 2;
        List<Variable<Integer>> v = create("b", width);
        var builder = ConstraintSatisfactionProblem.builder();
        for (Variable<Integer> x : v) builder.variableDomain(x, IntRangeDomain.of(1, width));
        builder.allDiffConstraint(new HashSet<>(v));

        assertThat(FeedbackVertexSet.within(new HashSet<>(builder.build().getConstraints()), width)).isEmpty();
    }

    @Test
    void nogoodsAreNotPartOfTheGraph() {
        List<Variable<Integer>> v = create("n", 3);
        var edges = List.of(List.of(v.get(0), v.get(1)), List.of(v.get(1), v.get(2)));
        Set<Constraint> constraints = new HashSet<>(graph(v, edges));
        // A nogood over the path's ends would close a cycle if it counted as an edge.
        constraints.add(GroundNogoodConstraint.of(Map.<Variable<?>, Object>of(v.get(0), 1, v.get(2), 1)));

        assertThat(FeedbackVertexSet.within(constraints, 0)).contains(Set.of());
    }
}
