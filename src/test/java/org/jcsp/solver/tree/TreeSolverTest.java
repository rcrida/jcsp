package org.jcsp.solver.tree;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeSolverTest {
    @Test
    void factory() {
        assertThat(TreeSolver.Factory.INSTANCE.createTreeSolver()).isNotNull();
    }
}
