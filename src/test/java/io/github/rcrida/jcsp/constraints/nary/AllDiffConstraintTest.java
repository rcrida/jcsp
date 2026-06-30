package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class AllDiffConstraintTest {
    @Mock
    Variable<Object> variable1;
    @Mock
    Variable<Object> variable2;
    @Mock
    Variable<Object> variable3;
    @Mock
    Object value1;
    @Mock
    Object value2;
    @Mock
    Object value3;
    @Mock
    Domain domain;
    AllDiffConstraint allDiffConstraint;

    @BeforeEach
    void setUp() {
        allDiffConstraint = AllDiffConstraint.builder().variables(Set.of(variable1, variable2, variable3)).build();
    }

    @Test
    void isSatisfiedByEmpty() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
    }

    @Test
    void isSatisfiedBySingle() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1)))).isTrue();
    }

    @Test
    void isSatisfiedByDoubleDifferent() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1, variable2, value2)))).isTrue();
    }

    @Test
    void isSatisfiedByDoubleSame() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1, variable2, value1)))).isFalse();
    }

    @Test
    void isSatisfiedByTripleDifferent() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1, variable2, value2, variable3, value3)))).isTrue();
    }

    @Test
    void isSatisfiedByTripleSame() {
        assertThat(allDiffConstraint.isSatisfiedBy(Assignment.of(Map.of(variable1, value1, variable2, value2, variable3, value2)))).isFalse();
    }

    @Test
    void testToString() {
        assertThat(allDiffConstraint.toString()).isEqualTo("<(variable1, variable2, variable3), AllDiff>");
    }

    // --- propagate() (Régin's GAC) ---

    static final Variable.Factory F = Variable.Factory.INSTANCE;

    @Test
    void propagate_noAllDiffConstraints_returnsUnchanged() {
        var csp = ConstraintSatisfactionProblem.builder().build();
        var x = F.create("x");
        var constraint = AllDiffConstraint.<Integer>builder()
                .variable(x).build();
        var domains = Map.<Variable<?>, Domain<?>>of(x, IntRangeDomain.of(1, 3));
        assertThat(constraint.propagate(domains)).isPresent();
    }

    @Test
    void propagate_nakedPair_prunesOtherDomains() {
        // x1∈{1,2}, x2∈{1,2}, x3∈{1,2,3} — naked pair on {1,2}: x3 must be {3}
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllDiffConstraint.<Integer>builder().variables(Set.of(x1, x2, x3)).build();
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 2),
                x2, IntRangeDomain.of(1, 2),
                x3, IntRangeDomain.of(1, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x3)).isEqualTo(IntRangeDomain.of(3, 3));
    }

    @Test
    void propagate_nakedTriple_prunesRemainingVariable() {
        // x1–x3 ∈ {1,2,3}, x4 ∈ {1,2,3,4} — naked triple: x4 must be {4}
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"),
                          x3 = F.create("x3"), x4 = F.create("x4");
        var c = AllDiffConstraint.<Integer>builder().variables(Set.of(x1, x2, x3, x4)).build();
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 3),
                x2, IntRangeDomain.of(1, 3),
                x3, IntRangeDomain.of(1, 3),
                x4, IntRangeDomain.of(1, 4));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x4)).isEqualTo(IntRangeDomain.of(4, 4));
    }

    @Test
    void propagate_singleton_prunesValueFromOthers() {
        // x1={1}, x2∈{1,2,3} — x2 cannot be 1
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllDiffConstraint.<Integer>builder().variables(Set.of(x1, x2)).build();
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 1),
                x2, IntRangeDomain.of(1, 3));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get().get(x2)).isEqualTo(IntRangeDomain.of(2, 3));
    }

    @Test
    void propagate_infeasible_returnsEmpty() {
        // x1∈{1,2}, x2∈{1,2}, x3∈{1,2} — 3 variables, only 2 values → infeasible
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2"), x3 = F.create("x3");
        var c = AllDiffConstraint.<Integer>builder().variables(Set.of(x1, x2, x3)).build();
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 2),
                x2, IntRangeDomain.of(1, 2),
                x3, IntRangeDomain.of(1, 2));
        assertThat(c.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_wideDomains_noChange() {
        Variable<Integer> x1 = F.create("x1"), x2 = F.create("x2");
        var c = AllDiffConstraint.<Integer>builder().variables(Set.of(x1, x2)).build();
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 5),
                x2, IntRangeDomain.of(1, 5));
        var result = c.propagate(domains);
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagateWithReasons_infeasible_returnsNonEmptyReason() {
        Variable<Integer> x1 = F.create("wr_x1"), x2 = F.create("wr_x2"), x3 = F.create("wr_x3");
        var c = AllDiffConstraint.<Integer>builder().variables(Set.of(x1, x2, x3)).build();
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 2),
                x2, IntRangeDomain.of(1, 2),
                x3, IntRangeDomain.of(1, 2));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isTrue();
        assertThat(result.reason()).isNotEmpty();
    }

    @Test
    void propagateWithReasons_feasible_returnsEmptyReason() {
        Variable<Integer> x1 = F.create("wr_f1"), x2 = F.create("wr_f2");
        var c = AllDiffConstraint.<Integer>builder().variables(Set.of(x1, x2)).build();
        var domains = Map.<Variable<?>, Domain<?>>of(
                x1, IntRangeDomain.of(1, 5),
                x2, IntRangeDomain.of(1, 5));
        var result = c.propagateWithReasons(domains);
        assertThat(result.isInfeasible()).isFalse();
        assertThat(result.reason()).isEmpty();
    }
}
