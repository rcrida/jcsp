package io.github.rcrida.jcsp.solver.backtrackingsearch.order;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainValuesOrdererFactoryTest {
    private static final Variable.Factory VF = new Variable.Factory();
    private static final Variable<Integer> X = VF.create("x_dvof");
    private static final ConstraintSatisfactionProblem CSP = ConstraintSatisfactionProblem.builder()
            .variableDomain(X, IntRangeDomain.of(1, 3))
            .build();

    private static List<?> order(DomainValuesOrderer orderer) {
        return orderer.order(CSP, X, Assignment.empty()).toList();
    }

    @Test
    void leastConstraining_isTheSharedInstance() {
        assertThat(DomainValuesOrderer.Factory.LEAST_CONSTRAINING.create()).isSameAs(LeastConstrainingValueOrderer.INSTANCE);
    }

    @Test
    void domainOrder_isTheSharedDefaultOrderer() {
        DomainValuesOrderer orderer = DomainValuesOrderer.Factory.DOMAIN_ORDER.create();

        assertThat(orderer).isSameAs(DefaultValueOrderer.INSTANCE);
        assertThat(order(orderer)).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void reversedDomainOrder_listsTheValuesBackwards() {
        assertThat(order(DomainValuesOrderer.Factory.REVERSED_DOMAIN_ORDER.create())).isEqualTo(List.of(3, 2, 1));
    }

    @Test
    void reversed_passesRestartsOnToWhatItWraps() {
        List<Integer> seen = new ArrayList<>();
        DomainValuesOrderer.Factory recording = () -> new DomainValuesOrderer() {
            @Override
            public java.util.stream.Stream<?> order(ConstraintSatisfactionProblem csp,
                                                    Variable<?> variable, Assignment assignment) {
                return DefaultValueOrderer.INSTANCE.order(csp, variable, assignment);
            }

            @Override
            public void onRestart(int restart) {
                seen.add(restart);
            }
        };

        DomainValuesOrderer.Factory.reversed(recording).create().onRestart(4);

        assertThat(seen).containsExactly(4);
    }

    @Test
    void alternating_startsInDomainOrderAndFlipsEachRestart() {
        DomainValuesOrderer orderer = DomainValuesOrderer.Factory.ALTERNATING.create();

        assertThat(order(orderer)).isEqualTo(List.of(1, 2, 3));
        orderer.onRestart(1);
        assertThat(order(orderer)).isEqualTo(List.of(1, 2, 3));
        orderer.onRestart(2);
        assertThat(order(orderer)).isEqualTo(List.of(3, 2, 1));
        orderer.onRestart(3);
        assertThat(order(orderer)).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void cycle_wrapsRoundAllItsOrdersAndTellsEveryOneOfEveryRestart() {
        List<String> told = new ArrayList<>();
        DomainValuesOrderer.Factory a = tagged("a", told, List.of(1));
        DomainValuesOrderer.Factory b = tagged("b", told, List.of(2));
        DomainValuesOrderer.Factory c = tagged("c", told, List.of(3));
        DomainValuesOrderer orderer = DomainValuesOrderer.Factory.cycle(a, b, c).create();

        List<Object> picked = new ArrayList<>();
        for (int restart = 1; restart <= 4; restart++) {
            orderer.onRestart(restart);
            picked.addAll(order(orderer));
        }

        assertThat(picked).containsExactly(1, 2, 3, 1);
        assertThat(told).containsExactly("a1", "b1", "c1", "a2", "b2", "c2", "a3", "b3", "c3", "a4", "b4", "c4");
    }

    @Test
    void create_givesEverySearchItsOwnState() {
        DomainValuesOrderer first = DomainValuesOrderer.Factory.ALTERNATING.create();
        DomainValuesOrderer second = DomainValuesOrderer.Factory.ALTERNATING.create();

        first.onRestart(2);

        assertThat(order(first)).isEqualTo(List.of(3, 2, 1));
        assertThat(order(second)).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void onRestart_defaultsToDoingNothing() {
        DomainValuesOrderer orderer = (csp, variable, assignment) -> java.util.stream.Stream.of(7);

        orderer.onRestart(1);

        assertThat(order(orderer)).isEqualTo(List.of(7));
    }

    private static DomainValuesOrderer.Factory tagged(String tag, List<String> told, List<Integer> values) {
        return () -> new DomainValuesOrderer() {
            @Override
            public java.util.stream.Stream<?> order(ConstraintSatisfactionProblem csp,
                                                    Variable<?> variable, Assignment assignment) {
                return values.stream();
            }

            @Override
            public void onRestart(int restart) {
                told.add(tag + restart);
            }
        };
    }
}
