package io.github.rcrida.jcsp.assignments;

import lombok.val;
import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.nary.GroundNogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.listener.SolverListener;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AssignmentTest {
    private final Variable.Factory variables = new Variable.Factory();
    Variable<Object> variable = variables.create("variable");
    @Mock
    Object value;
    Variable<Object> anotherVariable = variables.create("anotherVariable");
    @Mock
    Object anotherValue;
    @Mock
    Domain<Object> domain;

    @Test
    void empty() {
        assertThat(Assignment.empty().size()).isZero();
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
        assertThat(partialAssignment).isEqualTo(Assignment.of(Map.of(variable, value)));
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
        assertThat(assignment.getStatistics().getNodesExplored()).isEqualTo(1);
        assertThat(next.getStatistics()).isSameAs(assignment.getStatistics());
    }

    @Test
    void withValueNotifiesListenerOnNodeExplored() {
        Object[] captured = new Object[3];
        SolverListener recorder = new SolverListener() {
            @Override
            public void onNodeExplored(Variable<?> v, Object v2, Assignment a) {
                captured[0] = v;
                captured[1] = v2;
                captured[2] = a;
            }
        };
        val assignment = Assignment.builder().listener(recorder).build();

        val next = assignment.withValue(variable, value);

        assertThat(captured[0]).isSameAs(variable);
        assertThat(captured[1]).isSameAs(value);
        assertThat(captured[2]).isSameAs(next);
    }

    @Test
    void withValuesAddsEveryEntryAndLeavesTheOriginalUntouched() {
        val assignment = Assignment.of(Map.of(variable, value));

        val next = assignment.withValues(Map.of(anotherVariable, anotherValue));

        assertThat(next).isEqualTo(Assignment.of(Map.of(variable, value, anotherVariable, anotherValue)));
        assertThat(assignment).isEqualTo(Assignment.of(Map.of(variable, value)));
    }

    @Test
    void withValuesCountsEachEntryAsAnExploredNode() {
        val assignment = Assignment.empty();

        val next = assignment.withValues(Map.of(variable, value, anotherVariable, anotherValue));

        assertThat(assignment.getStatistics().getNodesExplored()).isEqualTo(2);
        assertThat(next.getStatistics()).isSameAs(assignment.getStatistics());
    }

    @Test
    void withValuesNotifiesTheListenerOncePerEntryWithTheFinishedAssignment() {
        Map<Variable<?>, Assignment> seen = new LinkedHashMap<>();
        SolverListener recorder = new SolverListener() {
            @Override
            public void onNodeExplored(Variable<?> v, Object v2, Assignment a) {
                seen.put(v, a);
            }
        };
        val assignment = Assignment.builder().listener(recorder).build();

        val next = assignment.withValues(Map.of(variable, value, anotherVariable, anotherValue));

        assertThat(seen).containsOnlyKeys(variable, anotherVariable);
        assertThat(seen.values()).allSatisfy(a -> assertThat(a).isSameAs(next));
    }

    @Test
    void withValuesKeepsTheCancellationAndListener() {
        val cancellation = new Cancellation();
        val assignment = Assignment.builder().cancellation(cancellation).build();

        val next = assignment.withValues(Map.of(variable, value));

        assertThat(next.cancellation()).isSameAs(cancellation);
        assertThat(next.listener()).isSameAs(SolverListener.NONE);
    }

    @Test
    void listenerDefaultsToNone() {
        assertThat(Assignment.empty().listener()).isSameAs(SolverListener.NONE);
    }

    @Test
    void merge() {
        val assignment1 = Assignment.of(Map.of(variable, value));
        val assignment2 = Assignment.of(Map.of(anotherVariable, anotherValue));
        assertThat(assignment1.merge(assignment2)).satisfies(merged -> {
            assertThat(merged).isEqualTo(Assignment.of(Map.of(variable, value, anotherVariable, anotherValue)));
        });
    }

    @Test
    void mergeAccumulatesStatistics() {
        val assignment1 = Assignment.empty();
        assignment1.getStatistics().incrementNodesExplored();
        val assignment2 = Assignment.empty();
        assignment2.getStatistics().incrementConstraintChecks();
        val merged = assignment1.merge(assignment2);
        assertThat(merged.getStatistics().getNodesExplored()).isEqualTo(1);
        assertThat(merged.getStatistics().getConstraintChecks()).isEqualTo(1);
    }

    @Test
    void isConsistentIncrementsConstraintChecks() {
        when(domain.contains(value)).thenReturn(true);
        val assignment = Assignment.of(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(variable, domain)
                .predicateConstraint(variable, (Predicate<Object>) v -> !v.equals(anotherValue))
                .build();
        assignment.isConsistent(csp);
        assertThat(assignment.getStatistics().getConstraintChecks()).isEqualTo(1);
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
                .predicateConstraint(variable, (Predicate<Object>) v -> !v.equals(value))
                .build();
        assertThat(assignment.isComplete(csp)).isTrue();
        assertThat(assignment.isConsistent(csp)).isFalse();
        assertThat(assignment.isSolution(csp)).isFalse();
    }

    @Test
    void isConsistentAmong_checksOnlyTheGivenConstraints() {
        val assignment = Assignment.of(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(variable, domain)
                .predicateConstraint(variable, (Predicate<Object>) v -> !v.equals(value))
                .build();
        val theOnlyConstraint = csp.getConstraints().iterator().next();
        assertThat(assignment.isConsistentAmong(Set.of(theOnlyConstraint))).isFalse();
        assertThat(assignment.isConsistentAmong(Set.of())).isTrue();
    }

    @Test
    void isConsistentAmong_violatedByOrdinaryConstraint_doesNotIncrementNogoodRejections() {
        val assignment = Assignment.of(Map.of(variable, value));
        val csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(variable, domain)
                .predicateConstraint(variable, (Predicate<Object>) v -> !v.equals(value))
                .build();
        val theOnlyConstraint = csp.getConstraints().iterator().next();
        assertThat(assignment.isConsistentAmong(Set.of(theOnlyConstraint))).isFalse();
        assertThat(assignment.getStatistics().getNogoodRejections()).isZero();
    }

    @Test
    void isConsistentAmong_violatedByNogoodConstraint_incrementsNogoodRejections() {
        val assignment = Assignment.of(Map.of(variable, value));
        NogoodConstraint nogood = GroundNogoodConstraint.of(Map.of(variable, value));
        assertThat(assignment.isConsistentAmong(Set.of(nogood))).isFalse();
        assertThat(assignment.getStatistics().getNogoodRejections()).isEqualTo(1);
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
    void equalsSameReference() {
        val assignment = Assignment.of(Map.of(variable, value));
        assertThat(assignment.equals(assignment)).isTrue();
    }

    @Test
    void equalsNotAssignment() {
        val assignment = Assignment.of(Map.of(variable, value));
        assertThat(assignment.equals(new Object())).isFalse();
    }

    @Test
    void testToString() {
        val assignment = Assignment.of(Map.of(variable, value));
        assertThat(assignment).asString().isEqualTo("{variable=value}");
    }

    @Test
    void testToStringSortsByVariableName() {
        val a = variables.<Integer>create("a");
        val b = variables.<Integer>create("b");
        val c = variables.<Integer>create("c");
        val assignment = Assignment.builder().value(c, 3).value(a, 1).value(b, 2).build();
        assertThat(assignment).asString().isEqualTo("{a=1, b=2, c=3}");
    }

    @Test
    void builderHandlesZeroOneAndManyEntries() {
        val a = variables.<Integer>create("a");
        val b = variables.<Integer>create("b");
        assertThat(Assignment.builder().build().size()).isZero();
        assertThat(Assignment.builder().value(a, 1).build()).isEqualTo(Assignment.of(Map.of(a, 1)));
        val many = Assignment.builder().value(a, 1).values(Map.of(a, 5, b, 2)).build();
        assertThat(many).isEqualTo(Assignment.of(Map.of(a, 5, b, 2)));
    }

    @Test
    void accessorsReadEntriesWithoutExposingTheMap() {
        val a = variables.<Integer>create("a");
        val b = variables.<Integer>create("b");
        val c = variables.<Integer>create("c");
        val assignment = Assignment.builder().value(a, 1).value(b, 2).build();

        assertThat(assignment.size()).isEqualTo(2);
        assertThat(assignment.variables()).containsExactlyInAnyOrder(a, b);
        assertThat(assignment.getValueOrNull(a)).isEqualTo(1);
        assertThat(assignment.getValueOrNull(c)).isNull();
        assertThat(assignment.containsAll(Set.of(a, b))).isTrue();
        assertThat(assignment.containsAll(Set.of(a, c))).isFalse();
        assertThat(assignment.containsAny(Set.of(c, b))).isTrue();
        assertThat(assignment.containsAny(Set.of(c))).isFalse();
        Map<Variable<?>, Object> seen = new LinkedHashMap<>();
        assignment.forEach(seen::put);
        assertThat(seen).containsExactlyInAnyOrderEntriesOf(Map.of(a, 1, b, 2));
    }

    @Test
    void entriesAreReadOnly() {
        val a = variables.<Integer>create("a");
        val b = variables.<Integer>create("b");
        val assignment = Assignment.builder().value(a, 1).value(b, 2).build();

        assertThat(assignment.entries()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(a, b);
        assertThat(assignment.entryStream().mapToInt(e -> (Integer) e.getValue()).sum()).isEqualTo(3);
        assertThatThrownBy(() -> assignment.entries().iterator().next().setValue(9))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalsAnyValuationWithTheSameEntries() {
        val a = variables.<Integer>create("a");
        val b = variables.<Integer>create("b");
        val assignment = Assignment.builder().value(a, 1).value(b, 2).build();

        assertThat(assignment).isEqualTo(new EntriesOnly(Map.of(a, 1, b, 2)));
        assertThat(assignment).isNotEqualTo(new EntriesOnly(Map.of(a, 1, b, 3)));
        assertThat(assignment).isNotEqualTo(new EntriesOnly(Map.of(a, 1)));
    }

    /** A {@link Valuation} that is not a {@link MapAssignment}, to reach the generic {@code equals} path. */
    private record EntriesOnly(Map<Variable<?>, Object> values) implements Valuation {
        @Override
        public Object getValueOrNull(Variable<?> variable) {
            return values.get(variable);
        }

        @Override
        public boolean isAssigned(Variable<?> variable) {
            return values.containsKey(variable);
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public Set<Variable<?>> variables() {
            return values.keySet();
        }

        @Override
        public Iterable<Map.Entry<Variable<?>, Object>> entries() {
            return values.entrySet();
        }
    }

    @Test
    void builderIsReusableAfterBuild() {
        val a = variables.<Integer>create("a");
        val b = variables.<Integer>create("b");
        val c = variables.<Integer>create("c");
        val builder = Assignment.builder().value(a, 1).value(b, 2);
        val first = builder.build();
        builder.value(c, 3);
        assertThat(first.variables()).containsExactlyInAnyOrder(a, b);
        assertThat(builder.build().variables()).containsExactlyInAnyOrder(a, b, c);
    }
}
