package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PigeonholeConstraintTest {
    private final Variable.Factory F = new Variable.Factory();
    private final Variable<Integer> a = F.create("a");
    private final Variable<Integer> b = F.create("b");
    private final Variable<Integer> c = F.create("c");
    private final Variable<Integer> d = F.create("d");
    private final Variable<Integer> e = F.create("e");
    private final Variable<Integer> f = F.create("f");

    private static Set<Variable<?>> set(Variable<?>... variables) {
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

    /** Pigeons {a,b}, {c,d}, {e,f}; holes {a,c,e} and {b,d,f}: three pigeons, two holes. */
    private PigeonholeConstraint threeInTwo() {
        return PigeonholeConstraint.of(List.of(set(a, b), set(c, d), set(e, f)), List.of(set(a, c, e), set(b, d, f)));
    }

    // --- construction ---

    @Test
    void aPigeonholeNeedsPigeonsThatAreDisjointAndHoused() {
        assertThatThrownBy(() -> PigeonholeConstraint.of(List.of(), List.of())).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> PigeonholeConstraint.of(List.of(set()), List.of())).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> PigeonholeConstraint.of(List.of(set(a, b), set(b, c)), List.of(set(a, b, c))))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> PigeonholeConstraint.of(List.of(set(a, b)), List.of(set(a, b, c))))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> PigeonholeConstraint.of(List.of(set(a, b)), List.of(set(a))))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void theConstraintCoversTheSolversPigeonVariables() {
        var constraint = threeInTwo();

        assertThat(constraint.getVariables()).containsExactlyInAnyOrder(a, b, c, d, e, f);
        assertThat(constraint.getPigeons()).hasSize(3);
        assertThat(constraint.getHoles()).hasSize(2);
        assertThat(constraint.getRelation()).isEqualTo("Pigeonhole[3 pigeons, 2 holes]");
    }

    @Test
    void constraintsOverTheSamePigeonsAndHolesAreEqual() {
        var first = threeInTwo();
        var second = threeInTwo();
        first.propagate(Map.of(a, open(), b, open(), c, open(), d, open(), e, open(), f, open()));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    // --- isSatisfiedBy ---

    @Test
    void aPigeonWithEveryVariableAssignedNeedsOneTrue() {
        var constraint = PigeonholeConstraint.of(List.of(set(a, b)), List.of(set(a), set(b)));

        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 0, b, 0)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 0, b, 1)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 0)))).isTrue();
    }

    @Test
    void aHoleHoldsAtMostOneTrueEvenWhenPartlyAssigned() {
        var constraint = PigeonholeConstraint.of(List.of(set(a, b), set(c, d)), List.of(set(a, c), set(b, d)));

        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 1, c, 1)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 1, c, 0)))).isTrue();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(a, 1, d, 1)))).isTrue();
    }

    @Test
    void booleanVariablesCountToo() {
        Variable<Boolean> p = F.create("p");
        Variable<Boolean> q = F.create("q");
        var constraint = PigeonholeConstraint.of(List.of(set(p), set(q)), List.of(set(p, q)));

        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(p, true, q, true)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(p, true, q, false)))).isFalse();
        assertThat(constraint.isSatisfiedBy(Assignment.of(Map.of(p, true)))).isTrue();
    }

    // --- propagate ---

    private static Map<Variable<?>, Domain<?>> allOpen(Variable<?>... variables) {
        Map<Variable<?>, Domain<?>> domains = new HashMap<>();
        for (Variable<?> variable : variables) domains.put(variable, open());
        return domains;
    }

    @Test
    void morePigeonsThanHolesIsInfeasible() {
        assertThat(threeInTwo().propagate(allOpen(a, b, c, d, e, f))).isEmpty();
    }

    @Test
    void pigeonsThatFitLeaveTheDomainsAlone() {
        var constraint = PigeonholeConstraint.of(List.of(set(a, b), set(c, d)), List.of(set(a, c), set(b, d)));

        assertThat(constraint.propagate(allOpen(a, b, c, d))).hasValue(Map.of());
        assertThat(constraint.propagate(allOpen(a, b, c, d))).hasValue(Map.of());
    }

    @Test
    void aPigeonWithNothingLeftToBeTrueIsInfeasible() {
        var constraint = PigeonholeConstraint.of(List.of(set(a, b), set(c)), List.of(set(a, c), set(b)));
        Map<Variable<?>, Domain<?>> domains = allOpen(c);
        domains.put(a, fixed(0));
        domains.put(b, fixed(0));

        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void aVariableWhoseHoleAnotherPigeonMustTakeIsSetFalse() {
        var constraint = PigeonholeConstraint.of(List.of(set(a, b), set(c)), List.of(set(a, c), set(b)));

        var updated = constraint.propagate(allOpen(a, b, c)).orElseThrow();

        assertThat(updated).containsOnlyKeys(a);
        assertThat(soleValue(updated.get(a))).isEqualTo(0);
    }

    @Test
    void aVariableAlreadyFalseIsLeftOutOfTheChoice() {
        var constraint = PigeonholeConstraint.of(List.of(set(a, b, e), set(c)), List.of(set(a), set(b, c), set(e)));
        Map<Variable<?>, Domain<?>> domains = allOpen(b, c, e);
        domains.put(a, fixed(0));

        var updated = constraint.propagate(domains).orElseThrow();

        assertThat(updated).containsOnlyKeys(b);
        assertThat(soleValue(updated.get(b))).isEqualTo(0);
    }

    @Test
    void aPigeonAlreadySatisfiedOnlyUsesTheHolesOfItsTrueVariable() {
        var constraint = PigeonholeConstraint.of(List.of(set(a, b), set(c, d)), List.of(set(a, c), set(b, d)));
        Map<Variable<?>, Domain<?>> domains = allOpen(b, c, d);
        domains.put(a, fixed(1));

        var updated = constraint.propagate(domains).orElseThrow();

        assertThat(updated).containsOnlyKeys(c);
        assertThat(soleValue(updated.get(c))).isEqualTo(0);
    }

    @Test
    void aSatisfiedPigeonKeepsItsOtherVariablesEvenWhenItsHolesNarrow() {
        var constraint = PigeonholeConstraint.of(List.of(set(a), set(c)), List.of(set(a, c), set(a)));
        Map<Variable<?>, Domain<?>> domains = Map.of(a, fixed(1), c, open());

        assertThat(constraint.propagate(domains)).hasValue(Map.of());
    }

    @Test
    void aVariableInTwoHolesOccupiesBoth() {
        var constraint = PigeonholeConstraint.of(List.of(set(a), set(c), set(e)), List.of(set(a, c), set(a, e)));

        assertThat(constraint.propagate(allOpen(a, c, e))).isEmpty();
    }

    @Test
    void booleanVariablesAreSetFalseAsBooleans() {
        Variable<Boolean> p = F.create("p");
        Variable<Boolean> q = F.create("q");
        Variable<Boolean> r = F.create("r");
        var constraint = PigeonholeConstraint.of(List.of(set(p, q), set(r)), List.of(set(p, r), set(q)));

        var updated = constraint.propagate(Map.of(p, BooleanDomain.INSTANCE, q, BooleanDomain.INSTANCE,
                r, BooleanDomain.INSTANCE)).orElseThrow();

        assertThat(updated).containsOnlyKeys(p);
        assertThat(soleValue(updated.get(p))).isEqualTo(false);
    }
}
