package io.github.rcrida.jcsp.solver.tree.decomposition.decomposer;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector.ArbitraryVariableSelector;
import io.github.rcrida.jcsp.solver.tree.decomposition.decomposer.variableselector.MinimumDegreeVariableSelector;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeDecomposerImplTest {
    static final Variable.Factory VARIABLE_FACTORY = Variable.Factory.INSTANCE;
    static Domain DOMAIN = IntRangeDomain.of(1, 3);
    static final Variable V0 = VARIABLE_FACTORY.create("V0");
    static final Variable V1 = VARIABLE_FACTORY.create("V1");
    static final Variable V2 = VARIABLE_FACTORY.create("V2");
    static final Variable V3 = VARIABLE_FACTORY.create("V3");
    static final Variable V4 = VARIABLE_FACTORY.create("V4");
    static final Variable V5 = VARIABLE_FACTORY.create("V5");
    TreeDecomposerImpl treeDecomposer;

    @BeforeEach
    void setUp() {
        treeDecomposer = new TreeDecomposerImpl(ArbitraryVariableSelector.Factory.INSTANCE);
    }

    @Test
    void decompose_minimumDegree() {
        treeDecomposer = new TreeDecomposerImpl(MinimumDegreeVariableSelector.Factory.INSTANCE);
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
        val treeVariable1 = VARIABLE_FACTORY.create("[V0, V1, V2]");
        val treeVariable2 = VARIABLE_FACTORY.create("[V1, V2, V3]");
        val treeVariable3 = VARIABLE_FACTORY.create("[V2, V3, V4]");
        val treeVariable4 = VARIABLE_FACTORY.create("[V3, V4, V5]");
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
        val treeVariable1 = VARIABLE_FACTORY.create("[V0, V1, V2]");
        val treeVariable2 = VARIABLE_FACTORY.create("[V1, V2, V3]");
        val treeVariable3 = VARIABLE_FACTORY.create("[V2, V3, V4]");
        val treeVariable4 = VARIABLE_FACTORY.create("[V3, V4, V5]");
        assertThat(treeDecomposition.getVariableDomains()).containsOnlyKeys(treeVariable1, treeVariable2, treeVariable3, treeVariable4);
        assertThat(treeDecomposition.getConstraints()).containsOnly(
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable1).right(treeVariable2).cliqueVariable(V1).build(),
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable1).right(treeVariable2).cliqueVariable(V2).build(),
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable2).right(treeVariable3).cliqueVariable(V2).build(),
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable2).right(treeVariable3).cliqueVariable(V3).build(),
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable3).right(treeVariable4).cliqueVariable(V3).build(),
                AssignmentVariableConsistencyConstraint.builder().left(treeVariable3).right(treeVariable4).cliqueVariable(V4).build()
        );
    }
}
