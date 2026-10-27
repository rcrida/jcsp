package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.nary.ParitySystemConstraint.Row;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParitySystemConstraintTest {
    private final Variable.Factory F = new Variable.Factory();
    private final Variable<Integer> x = F.create("x");
    private final Variable<Integer> y = F.create("y");
    private final Variable<Integer> z = F.create("z");

    private static Set<Variable<?>> vars(Variable<?>... variables) {
        return new LinkedHashSet<>(List.of(variables));
    }

    private static Domain<?> open() {
        return IntRangeDomain.of(0, 1);
    }

    private static Domain<?> fixed(int bit) {
        return IntRangeDomain.of(bit, bit);
    }

    private static Object soleValue(Domain<?> domain) {
        assertThat(domain.isSingleton()).isTrue();
        return domain.singleValue().orElseThrow();
    }

    // --- construction ---

    @Test
    void aRowNeedsAVariableAndAParityOfZeroOrOne() {
        assertThatThrownBy(() -> new Row(Set.of(), 0)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> new Row(Set.of(x), 2)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ParitySystemConstraint.of(List.of())).isInstanceOf(AssertionError.class);
    }

    @Test
    void theSystemCoversEveryRowsVariables() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y), 1), new Row(vars(y, z), 0)));

        assertThat(constraint.getVariables()).containsExactlyInAnyOrder(x, y, z);
        assertThat(constraint.getRows()).hasSize(2);
        assertThat(constraint.getRelation()).isEqualTo("ParitySystem[2 rows, 3 variables]");
    }

    @Test
    void systemsWithTheSameRowsAreEqual() {
        var first = ParitySystemConstraint.of(List.of(new Row(vars(x, y), 1)));
        var second = ParitySystemConstraint.of(List.of(new Row(vars(x, y), 1)));
        first.propagate(Map.of(x, open(), y, open()));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(ParitySystemConstraint.of(List.of(new Row(vars(x, y), 0))));
    }

    // --- isSatisfiedBy ---

    @Test
    void aRowIsOnlyCheckedOnceAllItsVariablesAreAssigned() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y, z), 1)));

        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1, z, 1)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 1, y, 1, z, 0)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 0, z, 1)))).isTrue();
    }

    @Test
    void everyRowMustHold() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y), 1), new Row(vars(y, z), 1)));

        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 1, z, 0)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(x, 0, y, 1, z, 1)))).isFalse();
    }

    @Test
    void booleanAndIntegerVariablesCountAlike() {
        Variable<Boolean> p = F.create("p");
        Variable<Boolean> q = F.create("q");
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(p, q, x), 0)));

        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(p, true, q, false, x, 1)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(p, true, q, true, x, 1)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(p, false, q, false, x, 0)))).isTrue();
    }

    // --- propagate ---

    @Test
    void rowsThatDisagreeAreInfeasible() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y), 0), new Row(vars(x, y), 1)));

        assertThat(constraint.propagate(Map.of(x, open(), y, open()))).isEmpty();
    }

    @Test
    void rowsThatAgreeOnlyOnceCombinedAreInfeasible() {
        var constraint = ParitySystemConstraint.of(List.of(
                new Row(vars(x, y), 1), new Row(vars(y, z), 1), new Row(vars(x, z), 1)));

        assertThat(constraint.propagate(Map.of(x, open(), y, open(), z, open()))).isEmpty();
    }

    @Test
    void aRowNothingCanDeriveFromLeavesTheDomainsAlone() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y), 1)));

        assertThat(constraint.propagate(Map.of(x, open(), y, open()))).hasValue(Map.of());
    }

    @Test
    void aVariableTwoRowsPinBetweenThemIsForced() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y, z), 1), new Row(vars(x, y), 0)));

        var updated = constraint.propagate(Map.of(x, open(), y, open(), z, open())).orElseThrow();

        assertThat(updated).containsOnlyKeys(z);
        assertThat(soleValue(updated.get(z))).isEqualTo(1);
    }

    @Test
    void assignedVariablesAreFoldedIntoTheRow() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y, z), 1)));

        var bothOnes = constraint.propagate(Map.of(x, fixed(1), y, fixed(1), z, open())).orElseThrow();
        var oneAndZero = constraint.propagate(Map.of(x, fixed(1), y, fixed(0), z, open())).orElseThrow();

        assertThat(soleValue(bothOnes.get(z))).isEqualTo(1);
        assertThat(soleValue(oneAndZero.get(z))).isEqualTo(0);
    }

    @Test
    void assignedVariablesThatBreakARowAreInfeasible() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y), 1), new Row(vars(x, y, z), 1)));

        assertThat(constraint.propagate(Map.of(x, fixed(1), y, fixed(1), z, open()))).isEmpty();
    }

    @Test
    void aForcedVariableKeepsItsBooleanType() {
        Variable<Boolean> p = F.create("p");
        Variable<Boolean> q = F.create("q");
        Variable<Boolean> r = F.create("r");
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(p, q, r), 0)));

        var updated = constraint.propagate(
                Map.of(p, BooleanDomain.TRUE_ONLY, q, BooleanDomain.FALSE_ONLY, r, BooleanDomain.INSTANCE)).orElseThrow();

        assertThat(soleValue(updated.get(r))).isEqualTo(true);
    }

    @Test
    void aMixedRowForcesAnIntegerVariable() {
        Variable<Boolean> p = F.create("p");
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(p, x), 1)));

        var updated = constraint.propagate(Map.of(p, BooleanDomain.TRUE_ONLY, x, open())).orElseThrow();

        assertThat(soleValue(updated.get(x))).isEqualTo(0);
    }

    @Test
    void aForcedValueTheDomainLacksIsInfeasible() {
        var constraint = ParitySystemConstraint.of(List.of(new Row(vars(x, y), 1)));

        assertThat(constraint.propagate(Map.of(x, DiscreteDomain.of(1, 2), y, fixed(1)))).isEmpty();
    }

    // --- more than 64 columns ---

    private ParitySystemConstraint wideRow(List<Variable<Integer>> variables, int parity) {
        return ParitySystemConstraint.of(List.of(new Row(new LinkedHashSet<>(variables), parity)));
    }

    private List<Variable<Integer>> wideVariables() {
        List<Variable<Integer>> variables = new ArrayList<>();
        for (int i = 0; i < 70; i++) variables.add(F.create("w" + i));
        return variables;
    }

    private Map<Variable<?>, Domain<?>> wideDomains(List<Variable<Integer>> variables, int... openIndexes) {
        Map<Variable<?>, Domain<?>> domains = new HashMap<>();
        variables.forEach(v -> domains.put(v, fixed(0)));
        for (int index : openIndexes) domains.put(variables.get(index), open());
        return domains;
    }

    @Test
    void theOnlyOpenVariableInTheSecondWordIsForced() {
        var variables = wideVariables();

        var updated = wideRow(variables, 1).propagate(wideDomains(variables, 69)).orElseThrow();

        assertThat(updated).containsOnlyKeys(variables.get(69));
        assertThat(soleValue(updated.get(variables.get(69)))).isEqualTo(1);
    }

    @Test
    void openVariablesInDifferentWordsForceNothing() {
        var variables = wideVariables();

        assertThat(wideRow(variables, 1).propagate(wideDomains(variables, 0, 64))).hasValue(Map.of());
    }

    @Test
    void openVariablesInOneWordForceNothing() {
        var variables = wideVariables();

        assertThat(wideRow(variables, 1).propagate(wideDomains(variables, 65, 66))).hasValue(Map.of());
    }
}
