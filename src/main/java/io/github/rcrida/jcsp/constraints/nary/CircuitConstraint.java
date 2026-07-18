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
import lombok.Value;
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
 * Enforces that a list of integer "successor" variables forms a single Hamiltonian circuit
 * through all {@code n} nodes. The list is 1-indexed: {@code successors.get(i)} represents
 * node {@code i+1}, and a value {@code j ∈ {1..n}} means "the successor of node {@code i+1}
 * is node {@code j}". Equivalent to MiniZinc's {@code circuit(successors)}.
 */
@Slf4j
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CircuitConstraint extends NaryConstraint implements Propagatable, BinaryDecomposable {
    @Singular("successor") private final List<Variable<Integer>> successors;

    public static CircuitConstraint of(@NonNull List<Variable<Integer>> successors) {
        assert !successors.isEmpty() : "CircuitConstraint requires at least one successor";
        var builder = builder();
        for (var v : successors) {
            builder.variable(v).successor(v);
        }
        return builder.build();
    }

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
        Set<Integer> visited = new HashSet<>();
        int current = 0;
        while (visited.add(current)) {
            current = next[current];
            if (current == 0) break;
        }
        return current == 0 && visited.size() == n;
    }

    @Override
    public Set<BinaryConstraint<?, ?>> getAsBinaryConstraints() {
        return AllDiffConstraint.<Integer>builder()
                .variables(new HashSet<>(successors))
                .build()
                .getAsBinaryConstraints();
    }

    /**
     * All-different successors is a permutation, but a permutation may validly consist of several
     * disjoint sub-cycles rather than one Hamiltonian circuit through every node — the pairwise
     * decomposition above says nothing about sub-tours, so it is not a sound stand-in for this
     * constraint's full semantics.
     */
    @Override
    public boolean isDecompositionComplete() {
        return false;
    }

    /**
     * Outcome of one propagation pass, shared by {@link #propagate} and {@link #explainInfeasible}
     * so each pass's narrowing/failure-detection logic lives in exactly one place. {@code updated}
     * (passed into every pass method) is mutated in place with any narrowed domains regardless of
     * outcome; {@link #FEASIBLE} means the pass found no infeasibility, {@code infeasible(reason)}
     * means it did, carrying the sound explanation for {@link #explainInfeasible} to return
     * (ignored by {@link #propagate}, which only needs to know a pass failed).
     */
    private record PassOutcome(boolean infeasible, Map<Variable<?>, Object> reason) {
        static final PassOutcome FEASIBLE = new PassOutcome(false, Map.of());
        static PassOutcome infeasible(Map<Variable<?>, Object> reason) { return new PassOutcome(true, reason); }
    }

    /** Step 1: self-loop removal — node i+1 cannot point to itself. */
    private PassOutcome selfLoopPass(Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        int n = successors.size();
        if (n <= 1) return PassOutcome.FEASIBLE;
        for (int i = 0; i < n; i++) {
            DiscreteDomain<Integer> dom = currentDomain(i, domains, updated);
            int self = i + 1;
            if (dom.contains(self)) {
                DiscreteDomain<Integer> pruned = dom.toBuilder().delete(self).build();
                if (pruned.isEmpty()) {
                    Map<Variable<?>, Object> reason = new HashMap<>();
                    Propagatable.addIfSingleton(dom, successors.get(i), reason);
                    return PassOutcome.infeasible(reason);
                }
                log.debug("CircuitConstraint pruned self-loop {} from node {}", self, self);
                updated.put(successors.get(i), pruned);
            }
        }
        return PassOutcome.FEASIBLE;
    }

    /** Step 2: singleton propagation — a fixed successor value is removed from all others. */
    private PassOutcome singletonPropagationPass(Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        int n = successors.size();
        for (int i = 0; i < n; i++) {
            DiscreteDomain<Integer> domI = currentDomain(i, domains, updated);
            if (domI.isSingleton()) {
                int j = domI.singleValue().orElseThrow();
                for (int k = 0; k < n; k++) {
                    if (k == i) continue;
                    DiscreteDomain<Integer> domK = currentDomain(k, domains, updated);
                    if (domK.contains(j)) {
                        DiscreteDomain<Integer> pruned = domK.toBuilder().delete(j).build();
                        if (pruned.isEmpty()) {
                            Map<Variable<?>, Object> reason = new HashMap<>();
                            Propagatable.addIfSingleton(domI, successors.get(i), reason);
                            Propagatable.addIfSingleton(domK, successors.get(k), reason);
                            return PassOutcome.infeasible(reason);
                        }
                        log.debug("CircuitConstraint pruned duplicate successor {} from node {}", j, k + 1);
                        updated.put(successors.get(k), pruned);
                    }
                }
            }
        }
        return PassOutcome.FEASIBLE;
    }

    /** Step 3: sub-tour elimination — an unfinished chain's endpoint cannot close back to its start. */
    private PassOutcome subTourPass(Map<Variable<?>, Domain<?>> domains, Map<Variable<?>, Domain<?>> updated) {
        int n = successors.size();
        Map<Integer, Integer> assigned = new HashMap<>();
        for (int i = 0; i < n; i++) {
            DiscreteDomain<Integer> dom = currentDomain(i, domains, updated);
            if (dom.isSingleton()) {
                assigned.put(i, dom.singleValue().orElseThrow() - 1);
            }
        }
        Map<Integer, Integer> chainStart = new HashMap<>();
        for (int start = 0; start < n; start++) {
            if (!assigned.containsKey(start)) continue;
            Set<Integer> visited = new LinkedHashSet<>();
            int current = start;
            while (assigned.containsKey(current) && !visited.contains(current)) {
                visited.add(current);
                current = assigned.get(current);
            }
            if (visited.contains(current)) {
                if (visited.size() < n) {
                    List<Variable<Integer>> chainVars = visited.stream().map(successors::get).toList();
                    Map<Variable<?>, Domain<?>> currentDomains = new HashMap<>(domains);
                    currentDomains.putAll(updated);
                    return PassOutcome.infeasible(Propagatable.allSingletonReason(chainVars, currentDomains));
                }
            } else {
                chainStart.put(current, start);
            }
        }
        for (var entry : chainStart.entrySet()) {
            int endpoint = entry.getKey();
            int valueToRemove = entry.getValue() + 1;
            DiscreteDomain<Integer> dom = currentDomain(endpoint, domains, updated);
            if (dom.contains(valueToRemove)) {
                // endpoint is unassigned (non-singleton), so deleting one value never empties it
                DiscreteDomain<Integer> pruned = dom.toBuilder().delete(valueToRemove).build();
                log.debug("CircuitConstraint pruned sub-tour closure {} from node {}", valueToRemove, endpoint + 1);
                updated.put(successors.get(endpoint), pruned);
            }
        }
        return PassOutcome.FEASIBLE;
    }

    @Override
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        if (selfLoopPass(domains, updated).infeasible()) return Optional.empty();
        if (singletonPropagationPass(domains, updated).infeasible()) return Optional.empty();
        if (subTourPass(domains, updated).infeasible()) return Optional.empty();
        return Optional.of(updated);
    }

    @SuppressWarnings("unchecked")
    private DiscreteDomain<Integer> currentDomain(int i, Map<Variable<?>, Domain<?>> domains,
                                                  Map<Variable<?>, Domain<?>> updated) {
        Variable<Integer> v = successors.get(i);
        return (DiscreteDomain<Integer>) updated.getOrDefault(v, domains.get(v));
    }

    /**
     * Replays {@link #propagate}'s three passes via the same {@link #selfLoopPass}/
     * {@link #singletonPropagationPass}/{@link #subTourPass} helpers (threading the same narrowed
     * domains, since a self-loop or singleton-propagation prune in an earlier pass can change a
     * later pass's findings) until the same failing pass is found, then returns its reason:
     * <ul>
     *     <li>self-loop removal — the domain that was already pinned to its own self-loop value
     *     is sufficient on its own, via {@link Propagatable#addIfSingleton};</li>
     *     <li>duplicate-successor pruning — both nodes forced to the same successor value are
     *     jointly responsible, since neither singleton alone explains the conflict without the
     *     other;</li>
     *     <li>sub-tour closure — every node on the closed chain is cited via
     *     {@link Propagatable#allSingletonReason}, since each is a singleton by construction
     *     (only assigned nodes are followed) and the closure is a joint property of the whole
     *     chain, not any single link.</li>
     * </ul>
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        PassOutcome selfLoop = selfLoopPass(domains, updated);
        if (selfLoop.infeasible()) return GroundNogoodConstraint.fromReason(selfLoop.reason());

        PassOutcome singletonPropagation = singletonPropagationPass(domains, updated);
        if (singletonPropagation.infeasible()) return GroundNogoodConstraint.fromReason(singletonPropagation.reason());

        PassOutcome subTour = subTourPass(domains, updated);
        if (subTour.infeasible()) return GroundNogoodConstraint.fromReason(subTour.reason());

        return Optional.empty();
    }

    @Override
    public String getRelation() {
        return "circuit(n=" + successors.size() + ")";
    }
}
