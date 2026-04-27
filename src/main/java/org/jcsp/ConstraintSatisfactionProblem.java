package org.jcsp;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.val;
import org.jcsp.constraints.Constraint;
import org.jcsp.constraints.binary.BinaryConstraint;
import org.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import org.jcsp.constraints.nary.AllDiffConstraint;
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
import java.util.function.Predicate;
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
@NonFinal
public class ConstraintSatisfactionProblem {
    Map<Variable, Domain> variableDomains;
    Set<Constraint> constraints;
    boolean isCyclic;
    boolean isFullyConnected;

    @Builder(toBuilder = true)
    ConstraintSatisfactionProblem(@Singular Map<Variable, Domain> variableDomains, @Singular Set<Constraint> constraints) {
        this.variableDomains = variableDomains;
        this.constraints = constraints;
        validateConstraints();

        val visited = new HashSet<Variable>();
        val neighbours = getNeighbours();
        if (neighbours.isEmpty()) {
            isCyclic = false;
            isFullyConnected = false;
        } else {
            val startingVariable = neighbours.keySet().iterator().next();
            isCyclic = isCyclic(startingVariable, null, neighbours, visited);
            isFullyConnected = visited.size() == neighbours.size();
        }
    }

    private void validateConstraints() {
        val unknownVariables = constraints.stream()
                .flatMap(c -> c.getVariables().stream())
                .filter(Predicate.not(variableDomains::containsKey))
                .collect(Collectors.toSet());
        if (!unknownVariables.isEmpty()) {
            throw new IllegalArgumentException(String.format("Constraints reference unknown variables %s", unknownVariables));
        }
    }

    public boolean isEmpty() {
        return variableDomains.isEmpty();
    }

    private boolean isCyclic(Variable src, Variable prt, Map<Variable, Set<Variable>> neighbours, Set<Variable> visited) {
        visited.add(src);
        for (Variable neighbour : neighbours.get(src)) {
            if (!visited.contains(neighbour)) {
                if (isCyclic(neighbour, src, neighbours, visited)) {
                    return true;
                }
            } else {
                if (neighbour != prt) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isTree() {
        return !isCyclic && isFullyConnected;
    }

    public int getNumVariables() {
        return variableDomains.size();
    }

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

    /**
     * When there are N-ary constraints that can't be recreated as binary constraints
     * then we don't want to split up the constraint during cycle cutset conditioning.
     * The unsplittable constraints should belong entirely in the cycle cutset.
     *
     * @return the set of variables which should not be included in a tree during cycle
     * cutset conditioning.
     */
    @NonNull
    public Set<Variable> getUnsplittableVariables() {
        return constraints.stream()
                .filter(c -> c instanceof NaryConstraint)
                .map(c -> (NaryConstraint) c)
                .filter(c -> c.getAsBinaryConstraints().isEmpty())
                .flatMap(c -> c.getVariables().stream())
                .collect(Collectors.toSet());
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

    public int countConstraints(@NonNull Variable variable) {
        return Math.toIntExact(constraints.stream()
                .filter(constraint -> constraint.getVariables().contains(variable))
                .count());
    }

    public ConstraintSatisfactionProblem withVariableSubset(@NonNull Predicate<Variable> variablePredicate) {
        val variableDomainSubset = getVariableDomains().entrySet().stream()
                .filter(e -> variablePredicate.test(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        val allConstraints = new HashSet<>(getConstraints());
        // include inferred binary constraints for when multi-variable constraints are split between subsets to capture the constraints
        // within the subset
        allConstraints.addAll(getAllBinaryConstraints());
        val constraintSubset = allConstraints.stream()
                .filter(constraint -> constraint.getVariables().stream()
                        .allMatch(variableDomainSubset::containsKey))
                .toList();
        return toBuilder()
                .clearVariableDomains()
                .variableDomains(variableDomainSubset)
                .clearConstraints()
                .constraints(constraintSubset)
                .build();
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

        public ConstraintSatisfactionProblemBuilder deleteVariable(@NonNull Variable variable) {
            val index = this.variableDomains$key.indexOf(variable);
            this.variableDomains$key.remove(index);
            this.variableDomains$value.remove(index);

            val binaryConstraintsOnVariable = this.constraints.stream()
                    .filter(bc -> bc.getVariables().contains(variable))
                    .toList();
            this.constraints.removeAll(binaryConstraintsOnVariable);
            return this;
        }

        public ConstraintSatisfactionProblemBuilder allDiffConstraint(@NonNull Set<Variable> variables) {
            return this.constraint(AllDiffConstraint.builder().variables(variables).build());
        }

        public ConstraintSatisfactionProblemBuilder notEqualsConstraint(@NonNull Variable left, @NonNull Variable right) {
            return this.constraint(BinaryNotEqualsConstraint.builder().left(left).right(right).build());
        }
    }
}
