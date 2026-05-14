package io.github.rcrida.jcsp;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.val;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryPredicateConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.Operator;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryPredicateConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a constraint satisfaction problem (CSP), which consists of a set of variables,
 * each associated with a domain of values, and a set of constraints that must be satisfied.
 * This class provides methods for constructing and analyzing the problem.
 * It supports both binary and n-ary constraints and allows for the generation
 * of equivalent binary constraints from n-ary ones.
 */
@Value
@NonFinal
@AllArgsConstructor(access = AccessLevel.NONE)
public class ConstraintSatisfactionProblem {
    Map<Variable, Domain<?>> variableDomains;
    @Getter(AccessLevel.NONE) @EqualsAndHashCode.Exclude ConstraintGraph constraintGraph;

    /**
     * Constructor ensures constraints reference known variables and determines whether graph is cyclic and/or
     * fully connected. When a {@code constraintGraph} is supplied whose constraint set matches {@code constraints}
     * (e.g. via {@link #toBuilder()} during domain-only updates) it is reused directly, avoiding redundant
     * recomputation of neighbours and binary constraints.
     *
     * @param variableDomains the variables and their corresponding domains for the problem
     * @param constraints the constraints that will apply to the solution
     * @param constraintGraph pre-computed constraint graph to reuse, or {@code null} to compute fresh
     */
    @Builder
    ConstraintSatisfactionProblem(@Singular Map<Variable, Domain<?>> variableDomains, @Singular Set<Constraint> constraints, @Nullable ConstraintGraph constraintGraph) {
        this.variableDomains = variableDomains;
        if (constraintGraph != null && constraintGraph.getConstraints().equals(constraints)) {
            this.constraintGraph = constraintGraph;
        } else {
            validateConstraints(variableDomains, constraints);
            this.constraintGraph = new ConstraintGraph(constraints, variableDomains.keySet());
        }
    }

    /**
     * Returns a builder pre-populated from this instance, sharing the existing {@link ConstraintGraph}
     * reference. Domain-only modifications via the builder will reuse the constraint graph without
     * recomputation; modifications to the constraint set will trigger a fresh computation.
     */
    public ConstraintSatisfactionProblemBuilder toBuilder() {
        return builder()
                .variableDomains(variableDomains)
                .constraints(constraintGraph.getConstraints())
                .constraintGraph(constraintGraph);
    }

    private static void validateConstraints(Map<Variable, Domain<?>> variableDomains, Set<Constraint> constraints) {
        val unknownVariables = constraints.stream()
                .flatMap(c -> c.getVariables().stream())
                .filter(Predicate.not(variableDomains::containsKey))
                .collect(Collectors.toSet());
        if (!unknownVariables.isEmpty()) {
            throw new IllegalArgumentException(String.format("Constraints reference unknown variables %s", unknownVariables));
        }
    }

    /**
     * Check if the problem is empty.
     *
     * @return true if the problem does not contain any variables.
     */
    public boolean isEmpty() {
        return variableDomains.isEmpty();
    }

    /**
     * @return true if the problem graph is a tree
     */
    public boolean isTree() {
        return constraintGraph.isTree();
    }

    /**
     * @return true if the constraint graph contains a cycle
     */
    public boolean isCyclic() {
        return constraintGraph.isCyclic();
    }

    /**
     * @return true if all variables with constraints are reachable from each other
     */
    public boolean isFullyConnected() {
        return constraintGraph.isFullyConnected();
    }

    /**
     * A map containing each variable in the problem that has at least one neighbour, along with its neighbours.
     */
    public Map<Variable, Set<Variable>> getNeighbours() {
        return constraintGraph.getNeighbours();
    }

    /**
     * A set of all binary constraints applicable to this problem. Where possible casts n-ary constrains
     * as additional binary constraints. Ignores n-ary constraints that aren't decomposable.
     */
    public Set<BinaryConstraint<?, ?>> getAllBinaryConstraints() {
        return constraintGraph.getAllBinaryConstraints();
    }

    public Set<Constraint> getConstraints() {
        return constraintGraph.getConstraints();
    }

    /**
     * @return the number of variables in the problem
     */
    public int getNumVariables() {
        return variableDomains.size();
    }

    /**
     * @param variable whose domain we are interested in
     * @return the domain of the specified variable, or empty, if the problem does not contain the variable
     */
    public Optional<Domain<?>> findDomain(@NonNull Variable variable) {
        return Optional.ofNullable(variableDomains.get(variable));
    }

    /**
     * @param variable whose domain we are interested in
     * @return the domain of the specified variable
     * @throws java.util.NoSuchElementException if the problem does not contain the variable
     */
    public Domain<?> getDomain(@NonNull Variable variable) {
        return Optional.ofNullable(variableDomains.get(variable)).orElseThrow();
    }

    /**
     * Check whether the specified variable is allowed to take the specified value
     *
     * @param variable the variable of interest
     * @param value is this value allowed for the variable?
     * @return true if the problem contains the variable and the domain of the variable contains the value
     */
    public boolean isAllowedValue(@NonNull Variable variable, @NonNull Object value) {
        return findDomain(variable)
                .map(domain -> domain.contains(value))
                .orElse(false);
    }

    /**
     * Calculates the size of the search space as the product of the sizes of all of the variable domains.
     *
     * @return the size of the search space
     */
    public BigInteger getSearchSpace() {
        return getVariableDomains().values().stream().map(Domain::size).map(BigInteger::valueOf).reduce(BigInteger.ONE, BigInteger::multiply);
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
        return getConstraints().stream()
                .filter(c -> c instanceof NaryConstraint)
                .map(c -> (NaryConstraint) c)
                .filter(c -> c.getAsBinaryConstraints().isEmpty())
                .flatMap(c -> c.getVariables().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Decomposes the current problem into a set of disconnected problems that can be solved
     * independently.
     *
     * @return set of disconnected problems
     */
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
                val domain = getDomain(variable);
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

    /**
     * Extract a subset of the current problem that contains only the variables accepted by the
     * specified predicate. This is used by cycle-cutset conditioning to separate the cycle-cutset
     * from the remaining tree.
     *
     * @param variablePredicate determines which variables to include in the sub-problem
     * @return a sub-problem with a reduced set of variables
     */
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

        public Variable createVariable(String name, Domain<?> domain) {
            final var variable = variableFactory.create(name);
            variableDomain(variable, domain);
            return variable;
        }

        public Variable[] create1dVariableArray(@NonNull String[] labels, @NonNull String namePrefix, @NonNull Domain<?> domain) {
            final var variables = new Variable[labels.length];
            for (int i = 0; i < labels.length; i++) {
                variables[i] = createVariable(String.format("%s%s", namePrefix, labels[i]), domain);
            }
            return variables;
        }

        public Variable[][] create2dVariableArray(@NonNull String[] rows, @NonNull String[] columns, @NonNull String namePrefix, @NonNull Domain<?> domain) {
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
            this.constraintGraph = null;
            return this;
        }

        /**
         * Create a unary constraint that constrains the specified variable to the specified value.
         *
         * @param variable to be constrained
         * @param value the value the variable must take
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder equalsConstraint(@NonNull Variable variable, @NonNull T value) {
            return this.constraint(UnaryValueConstraint.<T>builder().variable(variable).value(value).build());
        }

        /**
         * Create a binary constraint that constraints two specified variables to have the same value.
         *
         * @param left first variable of the pair
         * @param right second variable of the pair
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder equalsConstraint(@NonNull Variable left, @NonNull Variable right) {
            return this.constraint(BinaryEqualsConstraint.builder().left(left).right(right).build());
        }

        /**
         * Create a unary constraint that constrains the specified variable to not take a specified value.
         *
         * @param variable to be constrained
         * @param value the value the variable must not take
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder notEqualsConstraint(@NonNull Variable variable, @NonNull T value) {
            return this.constraint(UnaryNotEqualsConstraint.<T>builder().variable(variable).value(value).build());
        }

        /**
         * Create a unary constraint that evaluates a typed predicate against the value of a single variable.
         *
         * @param variable the variable to be constrained
         * @param predicate determines whether the variable's value satisfies the constraint
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder predicateConstraint(@NonNull Variable variable, @NonNull Predicate<T> predicate) {
            return this.constraint(UnaryPredicateConstraint.<T>builder().variable(variable).predicate(predicate).build());
        }

        /**
         * Create an AllDiff constraint on the specified set of variables.
         *
         * @param variables to be constrained
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder allDiffConstraint(@NonNull Set<Variable> variables) {
            return this.constraint(AllDiffConstraint.builder().variables(variables).build());
        }

        /**
         * Constrain a list of boolean variables so that at most one is {@code true}.
         * Implemented as pairwise binary constraints — each pair cannot both be {@code true}.
         * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param variables the boolean variables to constrain
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atMostOneConstraint(@NonNull Set<Variable> variables) {
            return this.constraint(AtMostOneConstraint.builder().variables(variables).build());
        }

        /**
         * Create a binary constraint that a pair of variables cannot take the same value.
         *
         * @param left first variable of the pair
         * @param right second variable of the pair
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder notEqualsConstraint(@NonNull Variable left, @NonNull Variable right) {
            return this.constraint(BinaryNotEqualsConstraint.builder().left(left).right(right).build());
        }

        /**
         * Create a sequence of binary not-equals constraints to ensure that each variable in the specified list
         * cannot take the same value as its neighbours in the list.
         *
         * @param variables a list of variables, neighbours in the list cannot have the same value
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder notEqualsChainConstraint(@NonNull List<Variable> variables) {
            assert variables.size() > 1;
            val firstIter = variables.iterator();
            val secondIter = variables.iterator();
            secondIter.next();
            while (secondIter.hasNext()) {
                this.constraint(BinaryNotEqualsConstraint.builder().left(firstIter.next()).right(secondIter.next()).build());
            }
            return this;
        }

        /**
         * Create a binary constraint on numerical variables. A specified offset is applied to the first variable and the result
         * is compared using the specified operator to the value of the second variable.
         *
         * @param left the first variable
         * @param offset numerical offset, should have same type as variable domain, can be positive or negative, will be added
         * @param operator the type of comparison to perform, eg ==, !=, &lt;, &lt;=, &gt;=, &gt;
         * @param right the second variable
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder offsetConstraint(@NonNull Variable left, @NonNull Number offset, @NonNull Operator operator, @NonNull Variable right) {
            return this.constraint(BinaryOffsetConstraint.builder().left(left).offset(offset).operator(operator).right(right).build());
        }

        /**
         * Create an arbitrary binary constraint using a {@link BiPredicate}.
         *
         * @param left the first variable
         * @param right the second variable
         * @param biPredicate determines whether the specified values of the first and second variables are consistent
         * @return the builder
         */
        public <L, R> ConstraintSatisfactionProblemBuilder biPredicateConstraint(@NonNull Variable left, @NonNull Variable right, @NonNull BiPredicate<L, R> biPredicate) {
            return this.constraint(BinaryPredicateConstraint.<L, R>builder().left(left).right(right).biPredicate(biPredicate).build());
        }

        /**
         * Create an arbitrary constraint using a {@link Predicate<Assignment>}. The predicate will need to dereference
         * the values of the variables it requires for the calculation of the predicate value.
         *
         * @param variables the variables that the predicate will reference
         * @param predicate determines whether the specified assignment is consistent
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder predicateConstraint(@NonNull Set<Variable> variables, @NonNull Predicate<Assignment> predicate) {
            return this.constraint(PredicateConstraint.builder().variables(variables).predicate(predicate).build());
        }
    }
}
