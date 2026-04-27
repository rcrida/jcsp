package org.jcsp.solver.tree.sorter;

import org.jcsp.ConstraintSatisfactionProblem;
import org.jcsp.consistency.arc.Arc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jcsp.solver.AustraliaMapColouringTest.DOMAIN;
import static org.jcsp.solver.AustraliaMapColouringTest.NSW;
import static org.jcsp.solver.AustraliaMapColouringTest.NT;
import static org.jcsp.solver.AustraliaMapColouringTest.Q;
import static org.jcsp.solver.AustraliaMapColouringTest.V;
import static org.jcsp.solver.AustraliaMapColouringTest.WA;

public class BFSTopologicalSorterTest {
    ConstraintSatisfactionProblem australiaWithoutSA = ConstraintSatisfactionProblem.builder()
            .variableDomain(WA, DOMAIN)
            .variableDomain(NT, DOMAIN)
            .variableDomain(Q, DOMAIN)
            .variableDomain(NSW, DOMAIN)
            .variableDomain(V, DOMAIN)
            .notEqualsConstraint(WA, NT)
            .notEqualsConstraint(NT, Q)
            .notEqualsConstraint(Q, NSW)
            .notEqualsConstraint(NSW, V)
            .build();

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

    @Test
    void sort_assertIsTree() {
        assertThatThrownBy(() -> BFSTopologicalSorter.INSTANCE.sort(ConstraintSatisfactionProblem.builder().build(), WA))
                .isInstanceOf(AssertionError.class);
    }
}
