package io.github.rcrida.jcsp.assignments;

import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MapValuationTest {
    private final Variable.Factory factory = new Variable.Factory();
    private final Variable<Integer> x = factory.create("x");
    private final Variable<Integer> y = factory.create("y");
    private final Variable<Integer> z = factory.create("z");

    @Test
    void ofHoldsACopyOfTheGivenEntries() {
        Map<Variable<?>, Object> values = new HashMap<>(Map.of(x, 1, y, 2));
        Valuation valuation = Valuation.of(values);
        values.put(z, 3);

        assertThat(valuation.size()).isEqualTo(2);
        assertThat(valuation.getValueOrNull(x)).isEqualTo(1);
        assertThat(valuation.getValueOrNull(z)).isNull();
        assertThat(valuation.isAssigned(y)).isTrue();
        assertThat(valuation.isAssigned(z)).isFalse();
        assertThat(valuation.variables()).containsExactlyInAnyOrder(x, y);
        assertThat(valuation.entries()).containsExactlyInAnyOrder(Map.entry(x, 1), Map.entry(y, 2));
        List<Object> visited = new ArrayList<>();
        valuation.forEach((variable, value) -> visited.add(value));
        assertThat(visited).containsExactlyInAnyOrder(1, 2);
        assertThat(valuation).hasToString("{x=1, y=2}");
    }

    @Test
    void equalsAndHashesAsAnAssignmentWithTheSameEntriesDoes() {
        Valuation valuation = Valuation.of(Map.of(x, 1, y, 2));
        Assignment assignment = Assignment.of(Map.of(x, 1, y, 2));

        assertThat(valuation).isEqualTo(Valuation.of(Map.of(y, 2, x, 1)));
        assertThat(valuation).isEqualTo(assignment);
        assertThat(assignment).isEqualTo(valuation);
        assertThat(valuation.hashCode()).isEqualTo(assignment.hashCode());
        assertThat(valuation).isNotEqualTo(Valuation.of(Map.of(x, 1, y, 3)));
        assertThat(valuation).isNotEqualTo(Assignment.of(Map.of(x, 1)));
        assertThat(Set.of(valuation)).contains(assignment);
    }

    @Test
    void copyOfReturnsAValuationAsItIsAndCopiesAnAssignmentWithoutItsSolveState() {
        Valuation valuation = Valuation.of(Map.of(x, 1));
        Assignment assignment = Assignment.of(Map.of(x, 1, y, 2));

        assertThat(Valuation.copyOf(valuation)).isSameAs(valuation);
        Valuation copied = Valuation.copyOf(assignment);
        assertThat(copied).isNotInstanceOf(Assignment.class).isEqualTo(assignment);
    }

    @Test
    void partialValuationHoldsOnlyTheAssignedOnesOfTheGivenVariables() {
        Assignment assignment = Assignment.of(Map.of(x, 1, y, 2));

        Valuation partial = assignment.partialValuation(Set.of(x, z));

        assertThat(partial).isNotInstanceOf(Assignment.class).isEqualTo(Valuation.of(Map.of(x, 1)));
    }
}
