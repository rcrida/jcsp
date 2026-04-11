package org.jcsp;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.val;
import org.jcsp.constraints.Constraint;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.constraints.nary.NaryConstraint;
import org.jcsp.domains.Domain;
import org.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a constraint satisfaction problem (CSP), which consists of a set of variables,
 * each associated with a domain of values, and a set of constraints that must be satisfied.
 * This class provides methods for constructing and analyzing the problem.
 * It supports both binary and n-ary constraints and allows for the generation
 * of equivalent binary constraints from n-ary ones.
 */
@Value
@Builder(toBuilder = true)
@NonFinal
public class ConstraintSatisfactionProblem {
    @Singular
    Map<Variable, Domain> variableDomains;
    @Singular
    Set<Constraint> constraints;

    public Optional<Domain> getDomain(@NonNull Variable variable) {
        return Optional.ofNullable(variableDomains.get(variable));
    }

    public boolean isAllowedValue(@NonNull Variable variable, @NonNull Object value) {
        return getDomain(variable)
                .map(domain -> domain.contains(value))
                .orElse(false);
    }

    public Set<BinaryConstraint> getAllBinaryConstraints() {
        val binaryConstraints = getConstraints().stream()
                .filter(c -> c instanceof BinaryConstraint)
                .map(c -> (BinaryConstraint) c)
                .toList();
        val inferredBinaryConstraints = getConstraints().stream()
                .filter(c -> c instanceof NaryConstraint)
                .map(c -> (NaryConstraint) c)
                .flatMap(c -> c.getAsBinaryConstraints().stream())
                .flatMap(Collection::stream)
                .toList();
        return Stream.concat(binaryConstraints.stream(), inferredBinaryConstraints.stream()).collect(Collectors.toSet());
    }

    public BigInteger getSearchSpace() {
        return getVariableDomains().values().stream().map(Domain::size).map(BigInteger::valueOf).reduce(BigInteger.ONE, BigInteger::multiply);
    }

    @NonNull
    public Map<Variable, Set<Variable>> getNeighbours() {
        val neighbours = new HashMap<Variable, Set<Variable>>();
        for (Variable variable : getVariableDomains().keySet()) {
            neighbours.put(variable, new HashSet<>());
        }
        for (Constraint constraint : getConstraints()) {
            val variables = constraint.getVariables();
            for (Variable variable : variables) {
                neighbours.get(variable).addAll(variables);
            }
        }
        for (Map.Entry<Variable, Set<Variable>> entry : neighbours.entrySet()) {
            entry.getValue().remove(entry.getKey());
        }
        return Map.copyOf(neighbours);
    }

    @NonNull
    public Set<ConstraintSatisfactionProblem> decomposeSubproblems() {
        val neighbours = getNeighbours();
        val unassignedVariables = new HashSet<>(neighbours.keySet());
        val subproblems = new HashSet<ConstraintSatisfactionProblem>();
        while (!unassignedVariables.isEmpty()) {
            val subCsp = ConstraintSatisfactionProblem.builder();
            val queue = new ArrayDeque<Variable>();
            addUnassignedVariable(queue, unassignedVariables.iterator().next(), unassignedVariables);
            while (!queue.isEmpty()) {
                val variable = queue.poll();
                val domain = getDomain(variable).orElseThrow();
                subCsp.variableDomain(variable, domain);
                subCsp.constraints(getConstraints().stream()
                        .filter(c -> c.getVariables().contains(variable))
                        .collect(Collectors.toSet()));
                neighbours.get(variable).stream()
                        .filter(unassignedVariables::contains)
                        .forEach(neighbour -> addUnassignedVariable(queue, neighbour, unassignedVariables));
            }
            subproblems.add(subCsp.build());
        }
        return subproblems;
    }

    private void addUnassignedVariable(@NonNull Queue<Variable> queue, @NonNull Variable variable, @NonNull Set<Variable> unassignedVariables) {
        queue.add(variable);
        unassignedVariables.remove(variable);
    }

    public static class ConstraintSatisfactionProblemBuilder {
        private final Variable.Factory variableFactory = Variable.Factory.INSTANCE;

        public Variable createVariable(String name, Domain domain) {
            final var variable = variableFactory.create(name);
            variableDomain(variable, domain);
            return variable;
        }

        public Variable[] create1dVariableArray(@NonNull String[] labels, @NonNull String namePrefix, @NonNull Domain domain) {
            final var variables = new Variable[labels.length];
            for (int i = 0; i < labels.length; i++) {
                variables[i] = createVariable(String.format("%s%s", namePrefix, labels[i]), domain);
            }
            return variables;
        }

        public Variable[][] create2dVariableArray(@NonNull String[] rows, @NonNull String[] columns, @NonNull String namePrefix, @NonNull Domain domain) {
            final var variables = new Variable[rows.length][columns.length];
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < columns.length; j++) {
                    variables[i][j] = createVariable(String.format("%s%s%s", namePrefix, rows[i], columns[j]), domain);
                }
            }
            return variables;
        }
    }
}
