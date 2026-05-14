package io.github.rcrida.jcsp;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.val;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConstraint;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable structural representation of a constraint graph. Captures the constraint set and all derived
 * graph properties (neighbours, binary constraints, cycle/connectivity analysis) computed once at construction.
 * Shared across all {@link ConstraintSatisfactionProblem} instances that result from domain-only modifications,
 * avoiding redundant recomputation during solving.
 */
@Value
class ConstraintGraph {
    Set<Constraint> constraints;
    boolean isCyclic;
    boolean isFullyConnected;
    /**
     * A map containing each variable in the problem that has at least one neighbour, along with its neighbours.
     */
    @EqualsAndHashCode.Exclude Map<Variable<?>, Set<Variable<?>>> neighbours;
    /**
     * A set of all binary constraints applicable to this problem. Where possible casts n-ary constrains
     * as additional binary constraints. Ignores n-ary constraints that aren't decomposable.
     */
    @EqualsAndHashCode.Exclude Set<BinaryConstraint<?, ?>> allBinaryConstraints;

    ConstraintGraph(@NonNull Set<Constraint> constraints, @NonNull Set<Variable<?>> variables) {
        this.constraints = constraints;
        this.neighbours = computeNeighbours(constraints, variables);
        this.allBinaryConstraints = computeAllBinaryConstraints(constraints);
        val visited = new HashSet<Variable<?>>();
        if (this.neighbours.isEmpty()) {
            this.isCyclic = false;
            this.isFullyConnected = false;
        } else {
            val startingVariable = this.neighbours.keySet().iterator().next();
            this.isCyclic = isCyclicFrom(startingVariable, null, this.neighbours, visited);
            this.isFullyConnected = visited.size() == this.neighbours.size();
        }
    }

    /**
     * @return true if the problem graph is a tree
     */
    boolean isTree() {
        return !isCyclic && isFullyConnected;
    }

    private static boolean isCyclicFrom(Variable<?> src, Variable<?> parent, Map<Variable<?>, Set<Variable<?>>> neighbours, Set<Variable<?>> visited) {
        visited.add(src);
        for (Variable<?> neighbour : neighbours.get(src)) {
            if (!visited.contains(neighbour)) {
                if (isCyclicFrom(neighbour, src, neighbours, visited)) {
                    return true;
                }
            } else if (neighbour != parent) {
                return true;
            }
        }
        return false;
    }

    private static Map<Variable<?>, Set<Variable<?>>> computeNeighbours(Set<Constraint> constraints, Set<Variable<?>> variables) {
        val neighbours = new HashMap<Variable<?>, Set<Variable<?>>>();
        for (Variable<?> variable : variables) {
            neighbours.put(variable, new HashSet<>());
        }
        for (Constraint constraint : constraints) {
            val constraintVariables = constraint.getVariables();
            for (Variable<?> variable : constraintVariables) {
                neighbours.get(variable).addAll(constraintVariables);
            }
        }
        val result = new HashMap<Variable<?>, Set<Variable<?>>>();
        for (Map.Entry<Variable<?>, Set<Variable<?>>> entry : neighbours.entrySet()) {
            entry.getValue().remove(entry.getKey());
            result.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Set<BinaryConstraint<?, ?>> computeAllBinaryConstraints(Set<Constraint> constraints) {
        val binaryConstraints = constraints.stream()
                .filter(c -> c instanceof BinaryConstraint)
                .map(c -> (BinaryConstraint<?, ?>) c)
                .toList();
        val inferredBinaryConstraints = constraints.stream()
                .filter(c -> c instanceof NaryConstraint)
                .map(c -> (NaryConstraint) c)
                .flatMap(c -> c.getAsBinaryConstraints().stream())
                .flatMap(Collection::stream)
                .toList();
        return Stream.concat(binaryConstraints.stream(), inferredBinaryConstraints.stream())
                .collect(Collectors.toUnmodifiableSet());
    }
}
