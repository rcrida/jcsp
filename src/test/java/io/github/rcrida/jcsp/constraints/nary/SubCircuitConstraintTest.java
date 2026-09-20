package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SubCircuitConstraint} — XCSP3's {@code circuit}, where a node may sit out by pointing at
 * itself. Contrast {@link CircuitConstraintTest}, whose constraint forbids self-loops outright.
 */
class SubCircuitConstraintTest {

    private Variable<Integer> s0;
    private Variable<Integer> s1;
    private Variable<Integer> s2;
    private Variable<Integer> s3;
    private List<Variable<Integer>> successors;
    private SubCircuitConstraint constraint;

    @BeforeEach
    void setUp() {
        var factory = Variable.Factory.INSTANCE;
        s0 = factory.create("s0");
        s1 = factory.create("s1");
        s2 = factory.create("s2");
        s3 = factory.create("s3");
        successors = List.of(s0, s1, s2, s3);
        constraint = SubCircuitConstraint.of(successors);
    }

    /** 1-indexed successor values, one per node, in list order. */
    private Assignment assign(int... values) {
        Map<Variable<?>, Object> map = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put(successors.get(i), values[i]);
        }
        return Assignment.of(map);
    }

    private Map<Variable<?>, Domain<?>> domains(DiscreteDomain<Integer> d0, DiscreteDomain<Integer> d1,
                                                DiscreteDomain<Integer> d2, DiscreteDomain<Integer> d3) {
        Map<Variable<?>, Domain<?>> map = new HashMap<>();
        map.put(s0, d0);
        map.put(s1, d1);
        map.put(s2, d2);
        map.put(s3, d3);
        return map;
    }

    // --- isSatisfiedBy ---

    @Test
    void isSatisfiedBy_hamiltonianCircuit_isAcceptedToo() {
        // 1→2→3→4→1: every node active, which is the special case where sub-circuit and circuit agree.
        assertThat(constraint.isSatisfiedBy(assign(2, 3, 4, 1))).isTrue();
    }

    @Test
    void isSatisfiedBy_circuitOverSubsetWithSelfLoops_isAccepted() {
        // 1→3→1 with nodes 2 and 4 sitting out. This is the shape CircuitConstraint rejects, and
        // the shape Mario-easy-4/Tpp-3-3-20-1's real solutions actually have.
        assertThat(constraint.isSatisfiedBy(assign(3, 2, 1, 4))).isTrue();
    }

    @Test
    void isSatisfiedBy_allSelfLoops_isRejected() {
        // The circuit must run through at least one node.
        assertThat(constraint.isSatisfiedBy(assign(1, 2, 3, 4))).isFalse();
    }

    @Test
    void isSatisfiedBy_twoDisjointCircuits_isRejected() {
        // 1→2→1 and 3→4→3: a permutation with no self-loops, but two circuits rather than one.
        assertThat(constraint.isSatisfiedBy(assign(2, 1, 4, 3))).isFalse();
    }

    @Test
    void isSatisfiedBy_circuitPlusDisjointCircuit_isRejected() {
        // 1→2→1 active, 3→4→3 active: both circuits are non-trivial, so neither is "sitting out".
        assertThat(constraint.isSatisfiedBy(assign(2, 1, 4, 3))).isFalse();
    }

    @Test
    void isSatisfiedBy_notAPermutation_isRejected() {
        // Nodes 1 and 2 both claim node 3 as successor.
        assertThat(constraint.isSatisfiedBy(assign(3, 3, 1, 4))).isFalse();
    }

    @Test
    void isSatisfiedBy_valueOutOfRange_isRejected() {
        assertThat(constraint.isSatisfiedBy(assign(5, 2, 3, 4))).isFalse();
        assertThat(constraint.isSatisfiedBy(assign(0, 2, 3, 4))).isFalse();
    }

    @Test
    void isSatisfiedBy_partialAssignment_isAccepted() {
        Map<Variable<?>, Object> map = new HashMap<>();
        map.put(s0, 2);
        assertThat(constraint.isSatisfiedBy(Assignment.of(map))).isTrue();
    }

    // --- propagate ---

    @Test
    void propagate_duplicateSuccessor_isPruned() {
        // s0 fixed to 3, so no other node may take 3.
        var result = constraint.propagate(domains(
                DiscreteDomain.of(3), DiscreteDomain.of(2, 3), DiscreteDomain.of(1, 3), DiscreteDomain.of(3, 4)));
        assertThat(result).isPresent();
        assertThat(result.get().get(s1)).isEqualTo(DiscreteDomain.of(2));
        assertThat(result.get().get(s3)).isEqualTo(DiscreteDomain.of(4));
    }

    @Test
    void propagate_duplicateSuccessorEmptiesDomain_isInfeasible() {
        // s0 and s1 are both fixed to 3.
        var domains = domains(
                DiscreteDomain.of(3), DiscreteDomain.of(3), DiscreteDomain.of(1, 2), DiscreteDomain.of(1, 2));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_closedCircuit_forcesEveryOutsideNodeToSelfLoop() {
        // 1→3→1 is already closed, so nodes 2 and 4 must sit out.
        var result = constraint.propagate(domains(
                DiscreteDomain.of(3), DiscreteDomain.of(2, 4), DiscreteDomain.of(1), DiscreteDomain.of(2, 4)));
        assertThat(result).isPresent();
        assertThat(result.get().get(s1)).isEqualTo(DiscreteDomain.of(2));
        assertThat(result.get().get(s3)).isEqualTo(DiscreteDomain.of(4));
    }

    @Test
    void propagate_closedCircuitWithOutsideNodeUnableToSelfLoop_isInfeasible() {
        // 1→3→1 closed, but node 2 cannot take its own value 2, so it can neither join nor sit out.
        var domains = domains(
                DiscreteDomain.of(3), DiscreteDomain.of(4), DiscreteDomain.of(1), DiscreteDomain.of(2, 4));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_closedCircuitCoveringEveryNode_forcesNothing() {
        var result = constraint.propagate(domains(
                DiscreteDomain.of(2), DiscreteDomain.of(3), DiscreteDomain.of(4), DiscreteDomain.of(1)));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void propagate_outsideNodeAlreadySelfLoop_isNotRewritten() {
        // Node 2 is already pinned to its self-loop, so the closure pass has nothing to add for it.
        var result = constraint.propagate(domains(
                DiscreteDomain.of(3), DiscreteDomain.of(2), DiscreteDomain.of(1), DiscreteDomain.of(2, 4)));
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainKey(s1);
        assertThat(result.get().get(s3)).isEqualTo(DiscreteDomain.of(4));
    }

    @Test
    void propagate_openChain_forcesNothingOutside() {
        // 1→3 but node 3 is still open, so the chain may yet grow; nothing may be forced to sit out.
        var result = constraint.propagate(domains(
                DiscreteDomain.of(3), DiscreteDomain.of(2, 4), DiscreteDomain.of(1, 2), DiscreteDomain.of(2, 4)));
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainKey(s3);
    }

    @Test
    void propagate_twoDisjointFixedCircuits_isInfeasible() {
        // 1→2→1 and 3→4→3, all fixed and all travelling: a permutation with no self-loops, but two
        // tours rather than one. Reachable only once every node is fixed, since a still-open node
        // could otherwise merge them.
        var domains = domains(
                DiscreteDomain.of(2), DiscreteDomain.of(1), DiscreteDomain.of(4), DiscreteDomain.of(3));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_travellingNodePointingAtAnUndecidedNode_forcesNothing() {
        // 1→3 but node 3 is open, so the travelling set is not closed and no circuit has settled.
        var result = constraint.propagate(domains(
                DiscreteDomain.of(3), DiscreteDomain.of(2, 4), DiscreteDomain.of(1, 2), DiscreteDomain.of(2, 4)));
        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContainKey(s3);
    }

    @Test
    void propagate_everyNodeFixedToSelfLoop_isInfeasible() {
        var domains = domains(
                DiscreteDomain.of(1), DiscreteDomain.of(2), DiscreteDomain.of(3), DiscreteDomain.of(4));
        assertThat(constraint.propagate(domains)).isEmpty();
    }

    @Test
    void propagate_openDomains_areLeftAlone() {
        var all = DiscreteDomain.of(1, 2, 3, 4);
        var result = constraint.propagate(domains(all, all, all, all));
        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    // --- explainInfeasible ---

    @Test
    void explainInfeasible_duplicateSuccessor_citesBothNodes() {
        var domains = domains(
                DiscreteDomain.of(3), DiscreteDomain.of(3), DiscreteDomain.of(1, 2), DiscreteDomain.of(1, 2));
        assertThat(constraint.explainInfeasible(domains))
                .contains(GroundNogoodConstraint.of(Map.of(s0, 3, s1, 3)));
    }

    @Test
    void explainInfeasible_closedCircuit_citesTheCircuitAndTheStrandedNode() {
        // Five nodes, so the duplicate-successor pass leaves slack rather than collapsing everything
        // into a two-circuit conflict first. 1→3→1 is closed; node 2 cannot take its own value 2, so
        // it can neither join the settled circuit nor sit out.
        var factory = Variable.Factory.INSTANCE;
        Variable<Integer> t0 = factory.create("t0");
        Variable<Integer> t1 = factory.create("t1");
        Variable<Integer> t2 = factory.create("t2");
        Variable<Integer> t3 = factory.create("t3");
        Variable<Integer> t4 = factory.create("t4");
        var wider = SubCircuitConstraint.of(List.of(t0, t1, t2, t3, t4));

        Map<Variable<?>, Domain<?>> domains = new HashMap<>();
        domains.put(t0, DiscreteDomain.of(3));
        domains.put(t1, DiscreteDomain.of(4, 5));
        domains.put(t2, DiscreteDomain.of(1));
        domains.put(t3, DiscreteDomain.of(2, 4, 5));
        domains.put(t4, DiscreteDomain.of(2, 4, 5));

        assertThat(wider.propagate(domains)).isEmpty();
        var explanation = wider.explainInfeasible(domains);
        assertThat(explanation).isPresent();
        // The stranded node must be cited: that it cannot self-loop is a fact about its own domain,
        // so the circuit alone is not contradictory.
        assertThat(explanation.get().getVariables()).containsExactlyInAnyOrder(t0, t2, t1);
    }

    @Test
    void explainInfeasible_twoDisjointFixedCircuits_citesEveryTravellingNode() {
        var domains = domains(
                DiscreteDomain.of(2), DiscreteDomain.of(1), DiscreteDomain.of(4), DiscreteDomain.of(3));
        var explanation = constraint.explainInfeasible(domains);
        assertThat(explanation).isPresent();
        assertThat(explanation.get().getVariables()).containsExactlyInAnyOrder(s0, s1, s2, s3);
    }

    @Test
    void explainInfeasible_everyNodeSelfLooped_citesEveryNode() {
        var domains = domains(
                DiscreteDomain.of(1), DiscreteDomain.of(2), DiscreteDomain.of(3), DiscreteDomain.of(4));
        var explanation = constraint.explainInfeasible(domains);
        assertThat(explanation).isPresent();
        assertThat(explanation.get().getVariables()).containsExactlyInAnyOrder(s0, s1, s2, s3);
    }

    @Test
    void explainInfeasible_feasibleDomains_returnsEmpty() {
        var all = DiscreteDomain.of(1, 2, 3, 4);
        assertThat(constraint.explainInfeasible(domains(all, all, all, all))).isEmpty();
    }

    // --- decomposition ---

    @Test
    void getAsBinaryConstraints_isTheAllDifferentDecomposition() {
        // 4 nodes → C(4,2) = 6 pairwise inequalities.
        assertThat(constraint.getAsBinaryConstraints()).hasSize(6);
    }

    @Test
    void isDecompositionComplete_isFalse() {
        assertThat(constraint.isDecompositionComplete()).isFalse();
    }

    @Test
    void getRelation_namesTheConstraintAndItsSize() {
        assertThat(constraint.getRelation()).isEqualTo("subCircuit(n=4)");
    }

    @Test
    void of_empty_asserts() {
        assertThatThrownBy(() -> SubCircuitConstraint.of(List.of()))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void cspBuilder_subCircuitConstraint_method() {
        // Three nodes, self-loops permitted. Solutions: the two Hamiltonian circuits
        // 1->2->3->1 and 1->3->2->1, plus each 2-circuit with the third node sitting out.
        var all = DiscreteDomain.of(1, 2, 3);
        var csp = io.github.rcrida.jcsp.ConstraintSatisfactionProblem.builder()
                .variableDomain(s0, all)
                .variableDomain(s1, all)
                .variableDomain(s2, all)
                .subCircuitConstraint(List.of(s0, s1, s2))
                .build();
        var solutions = io.github.rcrida.jcsp.solver.Solver.Factory.INSTANCE.createSolver(csp).getSolutions().toList();
        assertThat(solutions).hasSize(5);
        // Every Hamiltonian circuit is also a valid sub-circuit, so both of those appear here too.
        assertThat(solutions).anySatisfy(a -> assertThat(a.getValue(s0)).contains(2));
    }

    @Test
    void of_registersEverySuccessorAsAVariable() {
        assertThat(constraint.getVariables()).containsExactlyInAnyOrder(s0, s1, s2, s3);
    }
}
