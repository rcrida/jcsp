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

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        int n = successors.size();
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();

        // Step 1: self-loop removal — node i+1 cannot point to itself.
        if (n > 1) {
            for (int i = 0; i < n; i++) {
                DiscreteDomain<Integer> dom = currentDomain(i, domains, updated);
                int self = i + 1;
                if (dom.contains(self)) {
                    DiscreteDomain<Integer> pruned = dom.toBuilder().delete(self).build();
                    if (pruned.isEmpty()) return Optional.empty();
                    log.debug("CircuitConstraint pruned self-loop {} from node {}", self, self);
                    updated.put(successors.get(i), pruned);
                }
            }
        }

        // Step 2: singleton propagation — a fixed successor value is removed from all others.
        for (int i = 0; i < n; i++) {
            DiscreteDomain<Integer> domI = currentDomain(i, domains, updated);
            if (domI.isSingleton()) {
                int j = domI.singleValue().orElseThrow();
                for (int k = 0; k < n; k++) {
                    if (k == i) continue;
                    DiscreteDomain<Integer> domK = currentDomain(k, domains, updated);
                    if (domK.contains(j)) {
                        DiscreteDomain<Integer> pruned = domK.toBuilder().delete(j).build();
                        if (pruned.isEmpty()) return Optional.empty();
                        log.debug("CircuitConstraint pruned duplicate successor {} from node {}", j, k + 1);
                        updated.put(successors.get(k), pruned);
                    }
                }
            }
        }

        // Step 3: sub-tour elimination — an unfinished chain's endpoint cannot close back to its start.
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
                if (visited.size() < n) return Optional.empty();
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

        return Optional.of(updated);
    }

    @SuppressWarnings("unchecked")
    private DiscreteDomain<Integer> currentDomain(int i, Map<Variable<?>, Domain<?>> domains,
                                                  Map<Variable<?>, Domain<?>> updated) {
        Variable<Integer> v = successors.get(i);
        return (DiscreteDomain<Integer>) updated.getOrDefault(v, domains.get(v));
    }

    @Override
    public String getRelation() {
        return "circuit(n=" + successors.size() + ")";
    }
}
