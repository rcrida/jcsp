package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer;

import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector.ArbitraryVariableSelector;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector.MinimumDegreeVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeDecomposerImplTest {
    // Its own factory, so V0..V5 are indices 0..5 and the decomposer, which names a tree variable after
    // its clique's set of variables, lists them in the same order whatever else has run before.
    static final Variable.Factory VARIABLE_FACTORY = new Variable.Factory();
    static Domain<Integer> DOMAIN = IntRangeDomain.of(1, 3);
    static final Variable<Integer> V0 = VARIABLE_FACTORY.create("V0");
    static final Variable<Integer> V1 = VARIABLE_FACTORY.create("V1");
    static final Variable<Integer> V2 = VARIABLE_FACTORY.create("V2");
    static final Variable<Integer> V3 = VARIABLE_FACTORY.create("V3");
    static final Variable<Integer> V4 = VARIABLE_FACTORY.create("V4");
    static final Variable<Integer> V5 = VARIABLE_FACTORY.create("V5");
    TreeDecomposerImpl treeDecomposer;

    private static final Set<Set<String>> GRAPH_EDGES = Set.of(
            Set.of("V0", "V1"), Set.of("V0", "V2"), Set.of("V1", "V2"), Set.of("V1", "V3"),
            Set.of("V2", "V4"), Set.of("V3", "V4"), Set.of("V3", "V5"), Set.of("V4", "V5"));

    /**
     * Which of several equally good decompositions the variable selector settles on is a tie-break, so
     * this asserts what any of them must be rather than one answer: every clique has three of the six
     * variables, every variable and every edge of the graph lies in some clique, and the cliques are
     * joined into a tree by one constraint per edge whose separator is exactly the two cliques' overlap.
     */
    private static void assertWidthTwoTreeDecomposition(ConstraintSatisfactionProblem tree) {
        // A tree variable is named after its clique's variables: "[V1, V3, V4]"
        Map<Variable<?>, Set<String>> cliques = new HashMap<>();
        tree.getVariableDomains().keySet().forEach(treeVariable ->
                cliques.put(treeVariable, Set.of(treeVariable.getName().replaceAll("[\\[\\]]", "").split(", "))));

        assertThat(cliques.values()).allSatisfy(clique -> assertThat(clique).hasSize(3));
        assertThat(cliques.values().stream().flatMap(Set::stream).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("V0", "V1", "V2", "V3", "V4", "V5");
        for (Set<String> edge : GRAPH_EDGES) {
            assertThat(cliques.values()).anySatisfy(clique -> assertThat(clique).containsAll(edge));
        }

        assertThat(tree.getConstraints()).hasSize(cliques.size() - 1).allSatisfy(constraint -> {
            val joint = (AssignmentVariableConsistencyConstraint) constraint;
            Set<String> overlap = new HashSet<>(cliques.get(joint.getLeft()));
            overlap.retainAll(cliques.get(joint.getRight()));
            assertThat(joint.cliqueVariables.stream().map(Variable::getName)).containsExactlyInAnyOrderElementsOf(overlap);
        });

        // n cliques joined by n - 1 constraints are a tree only if they are also connected
        Set<Variable<?>> reached = new HashSet<>(Set.of(cliques.keySet().iterator().next()));
        for (boolean grew = true; grew; ) {
            grew = false;
            for (val constraint : tree.getConstraints()) {
                val joint = (AssignmentVariableConsistencyConstraint) constraint;
                if (reached.contains(joint.getLeft()) != reached.contains(joint.getRight())) {
                    reached.add(joint.getLeft());
                    reached.add(joint.getRight());
                    grew = true;
                }
            }
        }
        assertThat(reached).isEqualTo(cliques.keySet());
    }

    @BeforeEach
    void setUp() {
        treeDecomposer = new TreeDecomposerImpl(ArbitraryVariableSelector.Factory.INSTANCE, Cancellation.NEVER);
    }

    @Test
    void decompose_minimumDegree() {
        treeDecomposer = new TreeDecomposerImpl(MinimumDegreeVariableSelector.Factory.INSTANCE, Cancellation.NEVER);
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V0, DOMAIN)
                .variableDomain(V1, DOMAIN)
                .variableDomain(V2, DOMAIN)
                .variableDomain(V3, DOMAIN)
                .variableDomain(V4, DOMAIN)
                .variableDomain(V5, DOMAIN)
                .notEqualsConstraint(V0, V1)
                .notEqualsConstraint(V0, V2)
                .notEqualsConstraint(V1, V2)
                .notEqualsConstraint(V1, V3)
                .notEqualsConstraint(V2, V4)
                .notEqualsConstraint(V3, V4)
                .notEqualsConstraint(V3, V5)
                .notEqualsConstraint(V4, V5)
                .build();
        val treeDecomposition = treeDecomposer.decompose(csp, 1024).get();
        assertWidthTwoTreeDecomposition(treeDecomposition);
    }

    @Test
    void decompose() {
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V0, DOMAIN)
                .variableDomain(V1, DOMAIN)
                .variableDomain(V2, DOMAIN)
                .variableDomain(V3, DOMAIN)
                .variableDomain(V4, DOMAIN)
                .variableDomain(V5, DOMAIN)
                .notEqualsConstraint(V0, V1)
                .notEqualsConstraint(V0, V2)
                .notEqualsConstraint(V1, V2)
                .notEqualsConstraint(V1, V3)
                .notEqualsConstraint(V2, V4)
                .notEqualsConstraint(V3, V4)
                .notEqualsConstraint(V3, V5)
                .notEqualsConstraint(V4, V5)
                .build();
        val treeDecomposition = treeDecomposer.decompose(csp, 1024).get();
        assertWidthTwoTreeDecomposition(treeDecomposition);
    }

    @Test
    void decompose_emptyCsp_returnsEmpty() {
        assertThat(treeDecomposer.decompose(ConstraintSatisfactionProblem.builder().build(), 1024)).isEmpty();
    }

    private static ConstraintSatisfactionProblem twoTriangles() {
        return ConstraintSatisfactionProblem.builder()
                .variableDomain(V0, DOMAIN)
                .variableDomain(V1, DOMAIN)
                .variableDomain(V2, DOMAIN)
                .variableDomain(V3, DOMAIN)
                .notEqualsConstraint(V0, V1)
                .notEqualsConstraint(V0, V2)
                .notEqualsConstraint(V1, V2)
                .notEqualsConstraint(V1, V3)
                .notEqualsConstraint(V2, V3)
                .build();
    }

    @Test
    void decompose_cancelledBeforeElimination_abandonsTheDecomposition() {
        // Abandoned as empty rather than thrown, because decompose() is on the getSolutions() path
        // too, where cancellation truncates silently.
        val cancellation = new Cancellation();
        cancellation.cancel();
        val cancelledDecomposer = new TreeDecomposerImpl(ArbitraryVariableSelector.Factory.INSTANCE, cancellation);

        // Not vacuous: the same problem decomposes successfully when nothing is cancelled.
        assertThat(treeDecomposer.decompose(twoTriangles(), 1024)).isPresent();
        assertThat(cancelledDecomposer.decompose(twoTriangles(), 1024)).isEmpty();
    }

    @Test
    void decompose_cancelledAfterElimination_abandonsCliqueEnumeration() {
        // Enumerating one clique's joint domain is the other long stretch on the satisfaction chain;
        // before it checked Cancellation, a 60s limit on Tpp-3-3-20-1 ran for 70s+ and had to be
        // killed. Cancelling from inside the last elimination step lets elimination finish and
        // leaves the enumeration as the first thing to see the signal.
        val cancellation = new Cancellation();
        val cancellingOnLastVertex = new TreeDecomposerImpl(neighbours -> {
            if (neighbours.size() == 1) {
                cancellation.cancel();
            }
            return ArbitraryVariableSelector.Factory.INSTANCE.create(neighbours);
        }, cancellation);

        assertThat(cancellingOnLastVertex.decompose(twoTriangles(), 1024)).isEmpty();
        assertThat(cancellation.isCancelled()).isTrue();
    }

    @Test
    void decompose_bagOverTheDomainCap_abandonsDuringElimination() {
        // Each triangle's joint domain is 3^3 = 27. Over the cap the decomposition can only be
        // declined, so it is declined while the bags are built -- before the fill-in, the
        // maximal-clique filter and the clique graph that a problem too wide to decompose would
        // otherwise spend its time on. bmc-ibm-4-04_X2 took 70s on a 10s limit doing exactly that.
        assertThat(treeDecomposer.decompose(twoTriangles(), 27)).isPresent();
        assertThat(treeDecomposer.decompose(twoTriangles(), 26)).isEmpty();
    }

    @Test
    void decompose_treeCsp_returnsEmpty() {
        // A chain V0-V1-V2 is a tree — tree decomposition has no benefit and returns empty.
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(V0, DOMAIN)
                .variableDomain(V1, DOMAIN)
                .variableDomain(V2, DOMAIN)
                .notEqualsConstraint(V0, V1)
                .notEqualsConstraint(V1, V2)
                .build();
        assertThat(treeDecomposer.decompose(csp, 1024)).isEmpty();
    }
}
