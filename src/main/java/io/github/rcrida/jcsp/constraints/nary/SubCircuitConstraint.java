package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.BinaryDecomposable;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces that a list of integer "successor" variables forms a single circuit through
 * <em>some</em> of the {@code n} nodes, with every node outside that circuit pointing at itself.
 * The list is 1-indexed: {@code successors.get(i)} represents node {@code i+1}, a value
 * {@code j != i+1} means "the successor of node {@code i+1} is node {@code j}", and
 * {@code successors.get(i) == i+1} means node {@code i+1} takes no part in the circuit.
 * <p>
 * This is XCSP3's {@code circuit} and Choco's {@code subCircuit}, and it is strictly weaker than
 * {@link CircuitConstraint}, which is MiniZinc's {@code circuit} and admits no self-loops at all.
 * Reading one as the other is not a conservative approximation in either direction: requiring a
 * Hamiltonian circuit rejects valid sub-circuit solutions, and a caller wanting every node visited
 * gets no such guarantee from this class. See
 * <a href="../../../../../../../docs/adr/0027-xcsp3-circuit-is-a-sub-circuit.md">ADR-0027</a>.
 * <p>
 * Three conditions, matching {@code org.xcsp.parser.callbacks.SolutionChecker}'s own reading of
 * {@code circuit} exactly:
 * <ul>
 *   <li>the successors are a permutation of {@code 1..n} — a circuit plus self-loops on the
 *       excluded nodes is a bijection;</li>
 *   <li>at least one node is <em>not</em> a self-loop, so the circuit is non-empty;</li>
 *   <li>following successors from any non-self-loop node returns to it after visiting every
 *       non-self-loop node — one circuit, not several.</li>
 * </ul>
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SubCircuitConstraint extends NaryConstraint implements Propagatable, BinaryDecomposable {
    @Singular("successor") private final List<Variable<Integer>> successors;

    public static SubCircuitConstraint of(@NonNull List<Variable<Integer>> successors) {
        assert !successors.isEmpty() : "SubCircuitConstraint requires at least one successor";
        var builder = builder();
        for (var v : successors) {
            builder.variable(v).successor(v);
        }
        return builder.build();
    }

    /**
     * The permutation check runs before the walk, and the walk relies on it: in a permutation every
     * node lies on exactly one cycle, so following successors from any node is guaranteed to return
     * to it, and a self-loop is its own cycle and can never be reached from elsewhere. That is what
     * lets the walk below terminate with no step counter and no self-loop guard.
     */
    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        int n = successors.size();
        int[] next = new int[n];
        for (int i = 0; i < n; i++) {
            var value = assignment.getValue(successors.get(i));
            if (value.isEmpty()) return true;
            int j = value.get();
            if (j < 1 || j > n) return false;
            next[i] = j - 1;
        }
        boolean[] taken = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (taken[next[i]]) return false;
            taken[next[i]] = true;
        }
        int active = 0;
        int start = -1;
        for (int i = 0; i < n; i++) {
            if (next[i] != i) {
                active++;
                if (start < 0) start = i;
            }
        }
        if (active == 0) return false;
        int current = next[start];
        int steps = 1;
        while (current != start) {
            current = next[current];
            steps++;
        }
        return steps == active;
    }

    /**
     * The same all-different decomposition {@link CircuitConstraint} uses, sound here for the same
     * reason: the successors are a permutation either way.
     */
    @Override
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        return AllDiffConstraint.<Integer>builder()
                .variables(new HashSet<>(successors))
                .build()
                .getAsBinaryConstraints();
    }

    /**
     * A permutation may consist of several disjoint cycles, or of nothing but self-loops; the
     * pairwise decomposition rules out neither, so it is not a sound stand-in for this constraint.
     */
    @Override
    public boolean isDecompositionComplete() {
        return false;
    }

    /** The node a fixed successor points at, or {@code -1} when this node is not yet fixed. */
    private int fixedNext(int i, Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        DiscreteDomain<Integer> dom = CircuitPropagation.currentDomain(successors, i, domains, updated);
        return dom.isSingleton() ? dom.singleValue().orElseThrow() - 1 : -1;
    }

    /**
     * Once the fixed successors close a circuit, that circuit is the whole circuit — this constraint
     * permits exactly one — so every node outside it must be a self-loop, and is forced to be.
     * A node outside that <em>cannot</em> be a self-loop makes the closure infeasible.
     * <p>
     * Only a closed circuit licenses this. An open chain may still grow to take in further nodes,
     * so nothing outside it can be forced yet.
     */
    private Optional<Set<Variable<?>>> closedCircuitPass(Map<Variable<?>, Domain<?>> domains,
                                                          Map<Variable<?>, Domain<?>> updated) {
        int n = successors.size();
        Set<Integer> circuit = closedTravellingNodes(domains, updated);
        if (circuit == null) return Optional.empty();
        if (!isSingleCircuit(circuit, domains, updated)) {
            return Optional.of(citing(circuit));
        }
        for (int k = 0; k < n; k++) {
            if (circuit.contains(k)) continue;
            DiscreteDomain<Integer> dom = CircuitPropagation.currentDomain(successors, k, domains, updated);
            int self = k + 1;
            if (!dom.contains(self)) {
                Set<Variable<?>> cited = citing(circuit);
                cited.add(successors.get(k));
                return Optional.of(cited);
            }
            if (!dom.isSingleton()) {
                log.debug("SubCircuitConstraint forced node {} to a self-loop outside the closed circuit", self);
                updated.put(successors.get(k), DiscreteDomain.of(self));
            }
        }
        return Optional.empty();
    }

    private Set<Variable<?>> citing(Set<Integer> nodes) {
        Set<Variable<?>> cited = new HashSet<>();
        nodes.forEach(i -> cited.add(successors.get(i)));
        return cited;
    }

    /**
     * The fixed nodes that travel (are not self-loops), if that set is closed under successor —
     * meaning no chain among them still leads somewhere undecided — or {@code null} otherwise.
     * <p>
     * Closure is what makes the set final: every travelling node's successor is itself a fixed
     * travelling node, so nothing further can join. Combined with the duplicate-successor pass
     * having already established in-degree at most one, such a set is exactly a disjoint union of
     * circuits, which is what lets {@link #isSingleCircuit} walk it without a revisit guard.
     */
    private Set<Integer> closedTravellingNodes(Map<Variable<?>, Domain<?>> domains,
                                               Map<Variable<?>, Domain<?>> updated) {
        int n = successors.size();
        Set<Integer> travelling = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            int following = fixedNext(i, domains, updated);
            if (following >= 0 && following != i) travelling.add(i);
        }
        if (travelling.isEmpty()) return null;
        for (int i : travelling) {
            if (!travelling.contains(fixedNext(i, domains, updated))) return null;
        }
        return travelling;
    }

    /**
     * Whether {@code circuit} — already known to be a disjoint union of circuits — is just one of
     * them, found by walking from any member and counting. Two or more means the successors have
     * settled into separate tours, which this constraint forbids outright.
     */
    private boolean isSingleCircuit(Set<Integer> circuit, Map<Variable<?>, Domain<?>> domains,
                                    Map<Variable<?>, Domain<?>> updated) {
        int start = circuit.iterator().next();
        int current = fixedNext(start, domains, updated);
        int steps = 1;
        while (current != start) {
            current = fixedNext(current, domains, updated);
            steps++;
        }
        return steps == circuit.size();
    }

    /**
     * At least one node must leave its self-loop. Detected only once every node is fixed, since
     * until then any still-open node may yet become part of the circuit.
     */
    private Optional<Set<Variable<?>>> nonEmptyCircuitPass(Map<Variable<?>, Domain<?>> domains,
                                                            Map<Variable<?>, Domain<?>> updated) {
        int n = successors.size();
        for (int i = 0; i < n; i++) {
            if (fixedNext(i, domains, updated) != i) return Optional.empty();
        }
        Set<Variable<?>> cited = new HashSet<>(successors);
        return Optional.of(cited);
    }

    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (CircuitPropagation.duplicateSuccessorPass(successors, domains, updated).infeasible()) {
            return Optional.empty();
        }
        if (closedCircuitPass(domains, updated).isPresent()) return Optional.empty();
        if (nonEmptyCircuitPass(domains, updated).isPresent()) return Optional.empty();
        return Optional.of(updated);
    }

    /**
     * Replays {@link #propagate}'s passes on the same threaded domains until one fails, then cites
     * that pass's own variables via {@link ValueSetNogoodConstraint#fromCurrentState} — which keeps
     * a cited variable that is not yet singleton by naming its exact current value set, rather than
     * dropping it from the explanation.
     * <p>
     * Every pass cites the node its conclusion actually rests on, the closure pass included: that
     * the outside node cannot be a self-loop is a fact about <em>that node's</em> current domain, so
     * omitting it would assert the circuit alone is contradictory when it is not.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        PassOutcome duplicate = CircuitPropagation.duplicateSuccessorPass(successors, domains, updated);
        if (duplicate.infeasible()) return GroundNogoodConstraint.fromReason(duplicate.reason());

        Optional<Set<Variable<?>>> closed = closedCircuitPass(domains, updated);
        if (closed.isPresent()) {
            return ValueSetNogoodConstraint.fromCurrentState(closed.get(),
                    CircuitPropagation.merged(domains, updated));
        }
        Optional<Set<Variable<?>>> empty = nonEmptyCircuitPass(domains, updated);
        if (empty.isPresent()) {
            return ValueSetNogoodConstraint.fromCurrentState(empty.get(),
                    CircuitPropagation.merged(domains, updated));
        }
        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "subCircuit(n=" + successors.size() + ")";
    }
}
