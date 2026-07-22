package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.NumericDiscreteDomain;
import io.github.rcrida.jcsp.solver.Solver;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CircuitConstraintTest {
    static final Variable.Factory F = Variable.Factory.INSTANCE;

    Variable<Integer> s0 = F.create("s0");
    Variable<Integer> s1 = F.create("s1");
    Variable<Integer> s2 = F.create("s2");
    Variable<Integer> s3 = F.create("s3");

    static NumericDiscreteDomain<Integer> dom(Integer... values) {
        return NumericDiscreteDomain.of(values);
    }

    // --- factory ---

    @Test
    void of_empty_asserts() {
        assertThatThrownBy(() -> CircuitConstraint.of(List.of()))
                .isInstanceOf(AssertionError.class);
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_validCircuit() {
        // 1->2->3->1
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s0, 2, s1, 3, s2, 1)))).isTrue();
    }

    @Test
    void isSatisfiedBy_subTour() {
        // 1->2->1 (node 3 isolated)
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s0, 2, s1, 1, s2, 3)))).isFalse();
    }

    @Test
    void isSatisfiedBy_revisitsNonStartNode() {
        // 1->2, 2->3, 3->2, 4->4: path from node 1 revisits node 2 without closing to node 1
        var c = CircuitConstraint.of(List.of(s0, s1, s2, s3));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s0, 2, s1, 3, s2, 2, s3, 4)))).isFalse();
    }

    @Test
    void isSatisfiedBy_partialAssignment_optimistic() {
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s0, 2)))).isTrue();
    }

    @Test
    void isSatisfiedBy_valueAboveRange() {
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s0, 4, s1, 1, s2, 2)))).isFalse();
    }

    @Test
    void isSatisfiedBy_valueBelowRange() {
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        assertThat(c.isSatisfiedBy(Assignment.of(Map.of(s0, 0, s1, 1, s2, 2)))).isFalse();
    }

    // --- getAsBinaryConstraints ---

    @Test
    void getAsBinaryConstraints_nThreeGivesThreePairs() {
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        assertThat(c.getAsBinaryConstraints()).hasSize(3);
    }

    // --- propagate ---

    @Test
    void propagate_singleNode_noChange() {
        // n == 1: self-loop removal skipped; node 1 -> 1 is the only (degenerate) circuit
        var c = CircuitConstraint.of(List.of(s0));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(s0, dom(1)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_noChange_returnsEmptyMap() {
        // Self-loop free, all-different consistent, no singletons.
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                s0, dom(2, 3), s1, dom(1, 3), s2, dom(1, 2)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_selfLoopRemoval() {
        // Every node may point to itself; propagation strips the self-value from each.
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                s0, dom(1, 2, 3), s1, dom(1, 2, 3), s2, dom(1, 2, 3)));
        assertThat(result).isPresent();
        assertThat(result.get().get(s0)).isEqualTo(dom(2, 3));
        assertThat(result.get().get(s1)).isEqualTo(dom(1, 3));
        assertThat(result.get().get(s2)).isEqualTo(dom(1, 2));
    }

    @Test
    void propagate_selfLoopRemovalCausesEmptyDomain() {
        // Node 1 can only point to itself -> empty after self-loop removal.
        var c = CircuitConstraint.of(List.of(s0, s1));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(s0, dom(1), s1, dom(1, 2)));
        assertThat(result).isEmpty();
    }

    @Test
    void propagate_singletonPropagation() {
        // s0 fixed to 2 -> value 2 removed from every other domain that holds it.
        var c = CircuitConstraint.of(List.of(s0, s1, s2, s3));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                s0, dom(2), s1, dom(3, 4), s2, dom(1, 2, 4), s3, dom(1, 3)));
        assertThat(result).isPresent();
        assertThat(result.get().get(s2)).isEqualTo(dom(1, 4));
        assertThat(result.get()).containsOnlyKeys(s2);
    }

    @Test
    void propagate_singletonPropagationCausesEmptyDomain() {
        // s0 fixed to 3; s1's only value is 3 -> emptied.
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                s0, dom(3), s1, dom(3), s2, dom(1, 2)));
        assertThat(result).isEmpty();
    }

    @Test
    void propagate_subTourElimination() {
        // s0 fixed to 2, s3 fixed to 3: open chains 1->2 and 4->3 whose endpoints
        // (nodes 2 and 3) must not close back to their chain starts (nodes 1 and 4).
        var c = CircuitConstraint.of(List.of(s0, s1, s2, s3));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                s0, dom(2), s1, dom(1, 4), s2, dom(1, 4), s3, dom(2, 3)));
        assertThat(result).isPresent();
        // s3 -> {3} via singleton step (s0=2 removes 2 from s3)
        assertThat(result.get().get(s3)).isEqualTo(dom(3));
        // endpoint node 2 (s1) cannot return to chain start node 1: drop value 1
        assertThat(result.get().get(s1)).isEqualTo(dom(4));
        // endpoint node 3 (s2) cannot return to chain start node 4: drop value 4
        assertThat(result.get().get(s2)).isEqualTo(dom(1));
    }

    @Test
    void propagate_prematureCycle_returnsEmpty() {
        // s0=2, s1=1 form the sub-cycle 1->2->1 before all 4 nodes are covered.
        var c = CircuitConstraint.of(List.of(s0, s1, s2, s3));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                s0, dom(2), s1, dom(1), s2, dom(1, 4), s3, dom(1, 3)));
        assertThat(result).isEmpty();
    }

    @Test
    void propagate_fullCircuit_noPruning() {
        // All singletons forming the full circuit 1->2->3->1: valid, nothing to prune.
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        var result = c.propagate(Map.<Variable<?>, Domain<?>>of(
                s0, dom(2), s1, dom(3), s2, dom(1)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_selfLoopForced_attributesSingleNode() {
        // Node 1 can only point to itself -> the singleton self-loop alone is a sufficient reason.
        var c = CircuitConstraint.of(List.of(s0, s1));
        var domains = Map.<Variable<?>, Domain<?>>of(s0, dom(1), s1, dom(1, 2));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(s0, 1)));
    }

    @Test
    void explainInfeasible_duplicateSuccessor_attributesBothNodes() {
        // s0 fixed to 3; s1's only value is 3 -> both singletons pointing to the same node 3.
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        var domains = Map.<Variable<?>, Domain<?>>of(s0, dom(3), s1, dom(3), s2, dom(1, 2));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(s0, 3, s1, 3)));
    }

    @Test
    void explainInfeasible_prematureCycle_attributesWholeChain() {
        // s0=2, s1=1 form the sub-cycle 1->2->1 before all 4 nodes are covered.
        var c = CircuitConstraint.of(List.of(s0, s1, s2, s3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                s0, dom(2), s1, dom(1), s2, dom(1, 4), s3, dom(1, 3));
        assertThat(c.propagate(domains)).isEmpty();
        assertThat(c.explainInfeasible(domains)).contains(GroundNogoodConstraint.of(Map.of(s0, 2, s1, 1)));
    }

    @Test
    void explainInfeasible_noConflict_returnsEmptyReason() {
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        var domains = Map.<Variable<?>, Domain<?>>of(
                s0, dom(2, 3), s1, dom(1, 3), s2, dom(1, 2));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_singleNode_returnsEmptyReason() {
        // n == 1: self-loop removal is skipped entirely (n > 1 guard is false).
        var c = CircuitConstraint.of(List.of(s0));
        var domains = Map.<Variable<?>, Domain<?>>of(s0, dom(1));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_selfLoopPrunedWithoutEmptying_cascadesToValidCircuit() {
        // s0's self-loop (1) is pruned from {1,3} leaving {3} (non-empty) rather than emptying it;
        // that singleton then cascades through singleton-propagation into a fully valid circuit
        // 1->3->2->1, so no conflict is ever found -- exercises the self-loop prune's non-empty path.
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        var domains = Map.<Variable<?>, Domain<?>>of(s0, dom(1, 3), s1, dom(1, 3), s2, dom(1, 2));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_chainReachesUnassignedNode_returnsEmptyReason() {
        // s0 is fixed to node 2, but node 2 (s1) is left open -- the chain from s0 breaks off at
        // an unassigned node rather than closing a cycle, so subtour detection finds nothing.
        var c = CircuitConstraint.of(List.of(s0, s1, s2, s3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                s0, dom(2), s1, dom(1, 3), s2, dom(1, 4), s3, dom(1, 3));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    @Test
    void explainInfeasible_fullValidCircuit_returnsEmptyReason() {
        // 1->2->3->4->1: every node singleton, forming one complete Hamiltonian circuit across
        // all four nodes -- the chain closes with visited.size() == n, so it's not a sub-tour.
        var c = CircuitConstraint.of(List.of(s0, s1, s2, s3));
        var domains = Map.<Variable<?>, Domain<?>>of(
                s0, dom(2), s1, dom(3), s2, dom(4), s3, dom(1));
        assertThat(c.explainInfeasible(domains)).isEmpty();
    }

    // --- toString / equality ---

    @Test
    void toString_showsRelation() {
        var c = CircuitConstraint.of(List.of(s0, s1, s2));
        assertThat(c.toString()).isEqualTo("<(s0, s1, s2), circuit(n=3)>");
    }

    @Test
    void equality() {
        var a = CircuitConstraint.of(List.of(s0, s1, s2));
        var b = CircuitConstraint.of(List.of(s0, s1, s2));
        var different = CircuitConstraint.of(List.of(s0, s1));
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(different);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("not a constraint");
    }

    // --- CSP builder method ---

    @Test
    void cspBuilder_circuitConstraint_method() {
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, dom(2, 3))
                .variableDomain(s1, dom(1, 3))
                .variableDomain(s2, dom(1, 2))
                .circuitConstraint(List.of(s0, s1, s2))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(2);
    }

    // --- solver integration ---

    @Test
    void solver_threeNodeCircuit_solutionCount() {
        // Domains exclude self-loops; the only Hamiltonian circuits are
        // 1->2->3->1 and 1->3->2->1.
        var csp = ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, dom(2, 3))
                .variableDomain(s1, dom(1, 3))
                .variableDomain(s2, dom(1, 2))
                .constraint(CircuitConstraint.of(List.of(s0, s1, s2)))
                .build();
        assertThat(Solver.Factory.INSTANCE.createSolver(csp).getSolutions()).hasSize(2);
    }
}
