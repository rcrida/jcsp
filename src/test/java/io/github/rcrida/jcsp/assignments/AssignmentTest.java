package io.github.rcrida.jcsp.assignments;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AssignmentTest {
    @Mock
    Variable<Object> variable;
    @Mock
    Object value;
    @Mock
    Variable<Object> anotherVariable;
    @Mock
    Object anotherValue;
    @Mock
    Domain<Object> domain;

    @Test
    void empty() {
        assertThat(Assignment.empty().getValues()).isEmpty();
    }

    @Test
    void getValueKnown() {
        val assignment = Assignment.of(Map.of(variable, value));
        assertThat(assignment.getValue(variable)).contains(value);
    }

    @Test
    void getValueUnknown() {
        val assignment = Assignment.of(Map.of(variable, value));
        assertThat(assignment.getValue(anotherVariable)).isEmpty();
    }
    @Test
    void extractPartialAssignment() {
        val assignment = Assignment.of(Map.of(variable, value, anotherVariable, anotherValue));
        val partialAssignment = assignment.extractPartialAssignment(Set.of(variable));
        assertThat(partialAssignment.getValues()).isEqualTo(Map.of(variable, value));
    }

    @Test
    void withValue() {
        val assignment = Assignment.of(Map.of(variable, value));
        assertThat(assignment.withValue(variable, anotherValue).getValue(variable)).contains(anotherValue);
    }

    @Test
    void withValueIncrementsNodesExplored() {
        val assignment = Assignment.empty();
        val next = assignment.withValue(variable, value);
        assertThat(assignment.getStatistics().getNodesExplored().get()).isEqualTo(1);
        assertThat(next.getStatistics()).isSameAs(assignment.getStatistics());
    }

    @Test
    void merge() {
        val assignment1 = Assignment.of(Map.of(variable, value));
        val assignment2 = Assignment.of(Map.of(anotherVariable, anotherValue));
        assertThat(assignment1.merge(assignment2)).satisfies(merged -> {
            assertThat(merged.getValues()).containsExactlyInAnyOrderEntriesOf(Map.of(variable, value, anotherVariable, anotherValue));
        });
    }

    @Test
    void mergeAccumulatesStatistics() {
        val assignment1 = Assignment.empty();
        assignment1.getStatistics().incrementNodesExplored();
        val assignment2 = Assignment.empty();
        assignment2.getStatistics().incrementConstraintChecks();
        val merged = assignment1.merge(assignment2);
        assertThat(merged.getStatistics().getNodesExplored().get()).isEqualTo(1);
        assertThat(merged.getStatistics().getConstraintChecks().get()).isEqualTo(1);
    }

    @Test
    void isConsistentIncrementsConstraintChecks() {
        when(domain.contains(value)).thenReturn(true);
        val assignment = Assignment.of(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(variable, domain)
                .notEqualsConstraint(variable, anotherValue)
                .build();
        assignment.isConsistent(csp);
        assertThat(assignment.getStatistics().getConstraintChecks().get()).isEqualTo(1);
    }

    @Test
    void isSolution_true() {
        when(domain.contains(value)).thenReturn(true);
        val assignment = Assignment.of(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(variable, domain)
                .build();
        assertThat(assignment.isComplete(csp)).isTrue();
        assertThat(assignment.isConsistent(csp)).isTrue();
        assertThat(assignment.isSolution(csp)).isTrue();
    }

    @Test
    void isSolution_incomplete() {
        when(domain.contains(value)).thenReturn(true);
        val assignment = Assignment.of(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(variable, domain)
                .variableDomain(anotherVariable, domain)
                .build();
        assertThat(assignment.isComplete(csp)).isFalse();
        assertThat(assignment.isConsistent(csp)).isTrue();
        assertThat(assignment.isSolution(csp)).isFalse();
    }

    @Test
    void isSolution_inconsistent() {
        when(domain.contains(value)).thenReturn(true);
        val assignment = Assignment.of(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(variable, domain)
                .notEqualsConstraint(variable, value)
                .build();
        assertThat(assignment.isComplete(csp)).isTrue();
        assertThat(assignment.isConsistent(csp)).isFalse();
        assertThat(assignment.isSolution(csp)).isFalse();
    }

    @Test
    void isSolution_invalid() {
        when(domain.contains(value)).thenReturn(false);
        val assignment = Assignment.of(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(variable, domain)
                .build();
        assertThatThrownBy(() -> assignment.isComplete(csp))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Invalid assigned value for variable 'variable': value");
        assertThatThrownBy(() -> assignment.isConsistent(csp))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Invalid assigned value for variable 'variable': value");
        assertThatThrownBy(() -> assignment.isSolution(csp))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Invalid assigned value for variable 'variable': value");
    }

    @Test
    void testToString() {
        val assignment = Assignment.of(Map.of(variable, value));
        assertThat(assignment).asString().isEqualTo("{variable=value}");
    }
}
