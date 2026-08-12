package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class OrderedConstraintTest {
    @Mock Variable<Integer> v1;
    @Mock Variable<Integer> v2;
    @Mock Variable<Integer> v3;
    @Mock Variable<Integer> v4;

    OrderedConstraint<Integer> lt;
    OrderedConstraint<Integer> leq;
    OrderedConstraint<Integer> geq;
    OrderedConstraint<Integer> gt;

    @BeforeEach
    void setUp() {
        lt = OrderedConstraint.of(List.of(v1, v2, v3, v4), Operator.LT);
        leq = OrderedConstraint.of(List.of(v1, v2, v3, v4), Operator.LEQ);
        geq = OrderedConstraint.of(List.of(v1, v2, v3, v4), Operator.GEQ);
        gt = OrderedConstraint.of(List.of(v1, v2, v3, v4), Operator.GT);
    }

    @Test
    void of_rejectsNonOrderingOperator() {
        assertThatThrownBy(() -> OrderedConstraint.of(List.of(v1, v2), Operator.EQ))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void of_createsEquivalentConstraint() {
        assertThat(OrderedConstraint.of(List.of(v1, v2, v3, v4), Operator.LT)).isEqualTo(lt);
    }

    @Test
    void strict_strictlyIncreasing_satisfied() {
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 3, v4, 4)))).isTrue();
    }

    @Test
    void strict_equalAdjacentValues_notSatisfied() {
        // LEQ allows ties; LT does not -- the distinguishing case relative to IncreasingConstraint.
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 2, v4, 4)))).isFalse();
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 2, v4, 4)))).isTrue();
    }

    @Test
    void nonStrict_nonDecreasing_satisfied() {
        assertThat(leq.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v2, 2, v3, 2, v4, 5)))).isTrue();
    }

    @Test
    void descending_nonIncreasing_satisfied() {
        assertThat(geq.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 3, v3, 3, v4, 1)))).isTrue();
    }

    @Test
    void descending_strictlyDecreasing_satisfied() {
        assertThat(gt.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 4, v3, 3, v4, 2)))).isTrue();
    }

    @Test
    void descending_equalAdjacentValues_notSatisfiedStrictly() {
        assertThat(gt.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 4, v3, 4, v4, 2)))).isFalse();
        assertThat(geq.isSatisfiedBy(Assignment.of(Map.of(v1, 5, v2, 4, v3, 4, v4, 2)))).isTrue();
    }

    @Test
    void violation_notSatisfied() {
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(v1, 4, v2, 3, v3, 2, v4, 1)))).isFalse();
    }

    @Test
    void partialAssignment_satisfiedOptimistically() {
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of()))).isTrue();
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(v1, 5)))).isTrue();
        assertThat(lt.isSatisfiedBy(Assignment.of(Map.of(v1, 1, v3, 3)))).isTrue();
    }

    @Test
    void testToString() {
        assertThat(lt.toString()).isEqualTo("<(v1, v2, v3, v4), ordered(<)>");
    }

    @Test
    void getAsBinaryConstraints_onePerConsecutivePair() {
        assertThat(lt.getAsBinaryConstraints()).hasSize(3);
    }

    @Test
    void solver_strictlyIncreasingSequences() {
        // Count strictly increasing (v1 < v2 < v3) sequences over domain {1,2,3,4}: C(4,3) = 4.
        Variable<Integer> x1 = Variable.Factory.INSTANCE.create("x1");
        Variable<Integer> x2 = Variable.Factory.INSTANCE.create("x2");
        Variable<Integer> x3 = Variable.Factory.INSTANCE.create("x3");
        var domain = IntRangeDomain.of(1, 4);
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(x1, domain).variableDomain(x2, domain).variableDomain(x3, domain)
                .orderedConstraint(List.of(x1, x2, x3), Operator.LT)
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(4);
    }

    // propagate()/explainInfeasible() -- ascending (LT) and descending (GT) directions.

    @Test
    void propagate_ascending_noChange_returnsEmptyMap() {
        var result = lt.propagate(Map.of(
                v1, IntRangeDomain.of(0, 3), v2, IntRangeDomain.of(3, 6),
                v3, IntRangeDomain.of(6, 9), v4, IntRangeDomain.of(9, 12)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_ascending_narrowsRunningBounds() {
        var result = lt.propagate(Map.of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(5, 10),
                v3, IntRangeDomain.of(0, 5), v4, IntRangeDomain.of(0, 10))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(v1)).toList()).containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5);
        assertThat(((DiscreteDomain<Integer>) result.get(v3)).toList()).containsExactly(5);
        assertThat(((DiscreteDomain<Integer>) result.get(v4)).toList()).containsExactlyInAnyOrder(5, 6, 7, 8, 9, 10);
    }

    @Test
    void propagate_leq_alsoTreatedAsAscending() {
        // Distinct from lt: exercises ascending() taking its "operator == LEQ" branch (LT alone,
        // used by every other ascending propagate/explainInfeasible test here, short-circuits
        // before ever evaluating that comparison).
        var result = leq.propagate(Map.of(
                v1, IntRangeDomain.of(0, 3), v2, IntRangeDomain.of(3, 6),
                v3, IntRangeDomain.of(6, 9), v4, IntRangeDomain.of(9, 12)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_ascending_coarseBoundsCrossed_infeasible() {
        // v1={10} forces the running floor to 10; v4={3} forces the running ceiling to 3 -- the
        // coarse newMin(10) > newMax(3) check catches this directly, no gapped domain needed.
        var result = lt.propagate(Map.of(
                v1, IntRangeDomain.of(10, 10), v2, IntRangeDomain.of(0, 20),
                v3, IntRangeDomain.of(0, 20), v4, IntRangeDomain.of(3, 3)));
        assertThat(result).isEmpty();
    }

    @Test
    void propagate_descending_narrowsRunningBounds() {
        var result = gt.propagate(Map.of(
                v1, IntRangeDomain.of(0, 10), v2, IntRangeDomain.of(0, 5),
                v3, IntRangeDomain.of(5, 10), v4, IntRangeDomain.of(0, 10))).orElseThrow();
        assertThat(((DiscreteDomain<Integer>) result.get(v1)).toList()).containsExactlyInAnyOrder(5, 6, 7, 8, 9, 10);
        assertThat(((DiscreteDomain<Integer>) result.get(v3)).toList()).containsExactly(5);
        assertThat(((DiscreteDomain<Integer>) result.get(v4)).toList()).containsExactlyInAnyOrder(0, 1, 2, 3, 4, 5);
    }

    @Test
    void propagate_narrowingEmptiesGappedDomain_infeasible() {
        // v1={8} (singleton), v2={0,10} (gapped, missing everything in between), v3={2} (singleton),
        // descending (GEQ): running floor from v1 forces v2>=8, running ceiling from v3 forces
        // v2<=2 -- v2's actual domain has no value in [2,8], so bounds narrowing empties it even
        // though neither v1 nor v3 alone crosses the coarse newMin<=newMax check against v2's [0,10].
        var v1Domain = NumericDiscreteDomain.of(8);
        var v2Domain = NumericDiscreteDomain.of(0, 10);
        var v3Domain = NumericDiscreteDomain.of(2);
        var result = geq.propagate(Map.of(v1, v1Domain, v2, v2Domain, v3, v3Domain, v4, IntRangeDomain.of(0, 10)));
        assertThat(result).isEmpty();
    }

    @Test
    void explainInfeasible_ascending_citesSingletonPair() {
        var result = lt.explainInfeasible(Map.of(
                v1, IntRangeDomain.of(10, 10), v2, IntRangeDomain.of(0, 20),
                v3, IntRangeDomain.of(0, 20), v4, IntRangeDomain.of(3, 3)));
        assertThat(result).isPresent();
    }

    @Test
    void explainInfeasible_descending_citesSingletonPair() {
        var result = gt.explainInfeasible(Map.of(
                v1, IntRangeDomain.of(1, 1), v2, IntRangeDomain.of(0, 20),
                v3, IntRangeDomain.of(0, 20), v4, IntRangeDomain.of(9, 9)));
        assertThat(result).isPresent();
    }

    @Test
    void explainInfeasible_feasibleChain_returnsEmpty() {
        var result = lt.explainInfeasible(Map.of(
                v1, IntRangeDomain.of(0, 3), v2, IntRangeDomain.of(3, 6),
                v3, IntRangeDomain.of(6, 9), v4, IntRangeDomain.of(9, 12)));
        assertThat(result).isEmpty();
    }
}
