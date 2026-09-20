package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Propagation shared by {@link CircuitConstraint} and {@link SubCircuitConstraint}.
 * <p>
 * Both read a 1-indexed successor list, and both require that list to be a permutation — a
 * Hamiltonian circuit is one, and a sub-circuit plus self-loops on the excluded nodes is one too.
 * Everything that follows only from permutation-ness therefore belongs here; everything that
 * depends on whether self-loops are legal stays in the two constraints.
 */
final class CircuitPropagation {

    private CircuitPropagation() {
    }

    @SuppressWarnings("unchecked")
    static DiscreteDomain<Integer> currentDomain(List<Variable<Integer>> successors, int i,
                                                 Map<Variable<?>, Domain<?>> domains,
                                                 Map<Variable<?>, Domain<?>> updated) {
        Variable<Integer> v = successors.get(i);
        return (DiscreteDomain<Integer>) updated.getOrDefault(v, domains.get(v));
    }

    static Map<Variable<?>, Domain<?>> merged(Map<Variable<?>, Domain<?>> domains,
                                              Map<Variable<?>, Domain<?>> updated) {
        Map<Variable<?>, Domain<?>> all = new HashMap<>(domains);
        all.putAll(updated);
        return all;
    }

    /**
     * Removes a node's fixed successor value from every other node's domain: the successor list is
     * a permutation, so no value may be taken twice.
     * <p>
     * On a wipeout both nodes are cited, since neither singleton alone explains the conflict
     * without the other.
     */
    static PassOutcome duplicateSuccessorPass(List<Variable<Integer>> successors,
                                              Map<Variable<?>, Domain<?>> domains,
                                              Map<Variable<?>, Domain<?>> updated) {
        int n = successors.size();
        for (int i = 0; i < n; i++) {
            DiscreteDomain<Integer> domI = currentDomain(successors, i, domains, updated);
            if (!domI.isSingleton()) continue;
            int j = domI.singleValue().orElseThrow();
            for (int k = 0; k < n; k++) {
                if (k == i) continue;
                DiscreteDomain<Integer> domK = currentDomain(successors, k, domains, updated);
                if (!domK.contains(j)) continue;
                DiscreteDomain<Integer> pruned = domK.toBuilder().delete(j).build();
                if (pruned.isEmpty()) {
                    Map<Variable<?>, Object> reason = new HashMap<>();
                    Propagatable.addIfSingleton(domI, successors.get(i), reason);
                    Propagatable.addIfSingleton(domK, successors.get(k), reason);
                    return PassOutcome.infeasible(reason);
                }
                updated.put(successors.get(k), pruned);
            }
        }
        return PassOutcome.FEASIBLE;
    }
}
