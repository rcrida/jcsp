package org.jcsp.solver.tree.sorter;

import org.jcsp.TreeConstraintSatisfactionProblem;
import org.jcsp.consistency.arc.Arc;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jcsp.solver.AustraliaMapColouringTest.DOMAIN;
import static org.jcsp.solver.AustraliaMapColouringTest.NSW;
import static org.jcsp.solver.AustraliaMapColouringTest.NT;
import static org.jcsp.solver.AustraliaMapColouringTest.Q;
import static org.jcsp.solver.AustraliaMapColouringTest.V;
import static org.jcsp.solver.AustraliaMapColouringTest.WA;

public class BFSTopologicalSorterTest {
    TreeConstraintSatisfactionProblem australiaWithoutSA = new TreeConstraintSatisfactionProblem(
            Map.of(
                    WA, DOMAIN,
                    NT, DOMAIN,
                    Q, DOMAIN,
                    NSW, DOMAIN,
                    V, DOMAIN
            ),
            Set.of(
                    BinaryNotEqualsConstraint.builder().left(WA).right(NT).build(),
                    BinaryNotEqualsConstraint.builder().left(NT).right(Q).build(),
                    BinaryNotEqualsConstraint.builder().left(Q).right(NSW).build(),
                    BinaryNotEqualsConstraint.builder().left(NSW).right(V).build()));

    @Test
    void sort() {
        assertThat(BFSTopologicalSorter.INSTANCE.sort(australiaWithoutSA, WA))
                .isEqualTo(List.of(Arc.of(WA, NT), Arc.of(NT, Q), Arc.of(Q, NSW), Arc.of(NSW, V)));
        assertThat(BFSTopologicalSorter.INSTANCE.sort(australiaWithoutSA, V))
                .isEqualTo(List.of(Arc.of(V, NSW), Arc.of(NSW, Q), Arc.of(Q, NT), Arc.of(NT, WA)));
        assertThat(BFSTopologicalSorter.INSTANCE.sort(australiaWithoutSA, NT)).isIn(
                List.of(Arc.of(NT, WA), Arc.of(NT, Q), Arc.of(Q, NSW), Arc.of(NSW, V)),
                List.of(Arc.of(NT, Q), Arc.of(NT, WA), Arc.of(Q, NSW), Arc.of(NSW, V))
        );
    }
}
