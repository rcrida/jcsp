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

import static org.assertj.core.api.Assertions.assertThat;

public class TreeDecomposerImplTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    static Domain<Integer> DOMAIN = IntRangeDomain.of(1, 3);
    static final Variable<Integer> V0 = VARIABLE_FACTORY.create("V0");
    static final Variable<Integer> V1 = VARIABLE_FACTORY.create("V1");
    static final Variable<Integer> V2 = VARIABLE_FACTORY.create("V2");
    static final Variable<Integer> V3 = VARIABLE_FACTORY.create("V3");
    static final Variable<Integer> V4 = VARIABLE_FACTORY.create("V4");
    static final Variable<Integer> V5 = VARIABLE_FACTORY.create("V5");
    TreeDecomposerImpl treeDecomposer;

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
        // Minimum degree eliminates V0 and V5 first (both degree 2), producing the same
        // optimal width-2 decomposition as arbitrary on this symmetric graph
        Variable<Assignment> treeVariable1 = VARIABLE_FACTORY.create("[V0, V1, V2]");
        Variable<Assignment> treeVariable2 = VARIABLE_FACTORY.create("[V1, V2, V3]");
        Variable<Assignment> treeVariable3 = VARIABLE_FACTORY.create("[V2, V3, V4]");
        Variable<Assignment> treeVariable4 = VARIABLE_FACTORY.create("[V3, V4, V5]");
        assertThat(treeDecomposition.getVariableDomains()).containsOnlyKeys(treeVariable1, treeVariable2, treeVariable3, treeVariable4);
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
        Variable<Assignment> treeVariable1 = VARIABLE_FACTORY.create("[V0, V1, V2]");
        Variable<Assignment> treeVariable2 = VARIABLE_FACTORY.create("[V1, V2, V3]");
        Variable<Assignment> treeVariable3 = VARIABLE_FACTORY.create("[V2, V3, V4]");
        Variable<Assignment> treeVariable4 = VARIABLE_FACTORY.create("[V3, V4, V5]");
        assertThat(treeDecomposition.getVariableDomains()).containsOnlyKeys(treeVariable1, treeVariable2, treeVariable3, treeVariable4);
        assertThat(treeDecomposition.getConstraints()).containsOnly(
                // One constraint per tree edge, carrying the whole separator
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable1).right(treeVariable2).cliqueVariable(V1).cliqueVariable(V2).build(),
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable2).right(treeVariable3).cliqueVariable(V2).cliqueVariable(V3).build(),
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable3).right(treeVariable4).cliqueVariable(V3).cliqueVariable(V4).build()
        );
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
