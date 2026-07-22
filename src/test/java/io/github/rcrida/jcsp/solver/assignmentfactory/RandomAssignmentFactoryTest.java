package io.github.rcrida.jcsp.solver.assignmentfactory;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.SetIntervalDomain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class RandomAssignmentFactoryTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void getAssignment_discreteDomain_isCompleteAndWithinDomain() {
        val x = F.<Integer>create("x");
        val csp = ConstraintSatisfactionProblem.builder().variableDomain(x, IntRangeDomain.of(1, 5)).build();
        val assignment = RandomAssignmentFactory.INSTANCE.getAssignment(csp);
        assertThat(assignment.isComplete(csp)).isTrue();
        assertThat(assignment.getValue(x)).hasValueSatisfying(v -> assertThat(v).isBetween(1, 5));
    }

    @Test
    void getAssignment_setBoundedDomain_producesAValidSetValue() {
        val group = F.<Set<String>>create("group");
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d"), 2, 2);
        val csp = ConstraintSatisfactionProblem.builder().variableDomain(group, domain).build();
        val assignment = RandomAssignmentFactory.INSTANCE.getAssignment(csp);
        assertThat(assignment.isComplete(csp)).isTrue();
        assertThat(assignment.getValue(group)).hasValueSatisfying(v -> {
            assertThat(v).hasSize(2);
            assertThat(domain.contains(v)).isTrue();
        });
    }

    @Test
    void getAssignment_setBoundedDomain_freeCardinality_variesInSize() {
        // Same reasoning as SetDomainMovesTest's randomValue variation test: negligible chance
        // 30 independent draws all land on the same cardinality out of 5 possibilities.
        val group = F.<Set<String>>create("group2");
        val domain = SetIntervalDomain.of(Set.of(), Set.of("a", "b", "c", "d"), 0, 4);
        val csp = ConstraintSatisfactionProblem.builder().variableDomain(group, domain).build();
        val sizesSeen = IntStream.range(0, 30)
                .mapToObj(i -> RandomAssignmentFactory.INSTANCE.getAssignment(csp).getValue(group).orElseThrow().size())
                .collect(Collectors.toSet());
        assertThat(sizesSeen).hasSizeGreaterThan(1);
    }
}
