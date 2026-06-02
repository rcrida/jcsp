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
import io.github.rcrida.jcsp.constraints.binary.BinaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryLogicConstraint;
import io.github.rcrida.jcsp.constraints.LogicOperator;
import io.github.rcrida.jcsp.constraints.binary.BinaryConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryElementConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryOffsetConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryPredicateConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtLeastNConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostNConstraint;
import io.github.rcrida.jcsp.constraints.nary.CountConstraint;
import io.github.rcrida.jcsp.constraints.nary.GlobalCardinalityConstraint;
import io.github.rcrida.jcsp.constraints.nary.LexConstraint;
import io.github.rcrida.jcsp.constraints.nary.DecreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.IncreasingConstraint;
import io.github.rcrida.jcsp.constraints.nary.LinearConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumConstraint;
import io.github.rcrida.jcsp.constraints.nary.AtMostOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.ExactlyOneConstraint;
import io.github.rcrida.jcsp.constraints.nary.ImplicationConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConstraint;
import io.github.rcrida.jcsp.constraints.nary.PredicateConstraint;
import io.github.rcrida.jcsp.constraints.nary.ReifiedConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryComparatorConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryPredicateConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryValueConstraint;
import io.github.rcrida.jcsp.domains.BooleanDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.domains.IntRangeDomain;
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
import java.util.stream.IntStream;

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
    Map<Variable<?>, Domain<?>> variableDomains;
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
    ConstraintSatisfactionProblem(@Singular("variableDomainEntry") Map<Variable<?>, Domain<?>> variableDomains, @Singular Set<Constraint> constraints, @Nullable ConstraintGraph constraintGraph) {
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

    private static void validateConstraints(Map<Variable<?>, Domain<?>> variableDomains, Set<Constraint> constraints) {
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
    public Map<Variable<?>, Set<Variable<?>>> getNeighbours() {
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
    @SuppressWarnings("unchecked")
    public <T> Optional<Domain<T>> findDomain(@NonNull Variable<T> variable) {
        return Optional.ofNullable((Domain<T>) variableDomains.get(variable));
    }

    /**
     * @param variable whose domain we are interested in
     * @return the domain of the specified variable
     * @throws java.util.NoSuchElementException if the problem does not contain the variable
     */
    @SuppressWarnings("unchecked")
    public <T> Domain<T> getDomain(@NonNull Variable<T> variable) {
        return (Domain<T>) Optional.ofNullable(variableDomains.get(variable)).orElseThrow();
    }

    /**
     * Check whether the specified variable is allowed to take the specified value
     *
     * @param variable the variable of interest
     * @param value is this value allowed for the variable?
     * @return true if the problem contains the variable and the domain of the variable contains the value
     */
    public boolean isAllowedValue(@NonNull Variable<?> variable, @NonNull Object value) {
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
    public Set<Variable<?>> getUnsplittableVariables() {
        return getConstraints().stream()
                .filter(c -> c instanceof NaryConstraint)
                .map(c -> (NaryConstraint) c)
                .filter(c -> c.getAsBinaryConstraints().isEmpty())
                .flatMap(c -> c.getVariables().stream())
                .collect(Collectors.toSet());
    }

    /**
     * Decomposes the current problem into independent sub-problems if it has more than one
     * connected component. Returns {@link Optional#empty()} when the problem is fully connected,
     * avoiding the cost of reconstructing a CSP that is equivalent to this one.
     *
     * @return the set of independent sub-problems, or empty if the problem cannot be decomposed
     */
    @NonNull
    public Optional<Set<ConstraintSatisfactionProblem>> decomposeSubproblems() {
        val neighbours = getNeighbours();
        val allVariables = neighbours.keySet();
        if (allVariables.isEmpty()) return Optional.empty();

        // First pass: cheap BFS to detect whether more than one component exists.
        // Avoids the expensive constraint-filtering reconstruction for single-component problems.
        val visited = new HashSet<Variable<?>>();
        val checkQueue = new ArrayDeque<Variable<?>>();
        val first = allVariables.iterator().next();
        checkQueue.add(first);
        visited.add(first);
        while (!checkQueue.isEmpty()) {
            val v = checkQueue.poll();
            for (val neighbour : neighbours.get(v)) {
                if (visited.add(neighbour)) checkQueue.add(neighbour);
            }
        }
        if (visited.size() == allVariables.size()) return Optional.empty();

        // Second pass: build sub-CSPs for each component.
        val unassignedVariables = new HashSet<>(allVariables);
        val subproblems = new HashSet<ConstraintSatisfactionProblem>();
        while (!unassignedVariables.isEmpty()) {
            val subCsp = ConstraintSatisfactionProblem.builder();
            val queue = new ArrayDeque<Variable<?>>();
            addUnassignedVariable(queue, unassignedVariables.iterator().next(), unassignedVariables);
            while (!queue.isEmpty()) {
                val variable = queue.poll();
                subCsp.variableDomainEntry(variable, getDomain(variable));
                subCsp.constraints(getConstraints().stream()
                        .filter(c -> c.getVariables().contains(variable))
                        .collect(Collectors.toSet()));
                neighbours.get(variable).stream()
                        .filter(unassignedVariables::contains)
                        .forEach(neighbour -> addUnassignedVariable(queue, neighbour, unassignedVariables));
            }
            subproblems.add(subCsp.build());
        }
        return Optional.of(subproblems);
    }

    private void addUnassignedVariable(@NonNull Queue<Variable<?>> queue, @NonNull Variable<?> variable, @NonNull Set<Variable<?>> unassignedVariables) {
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
    public ConstraintSatisfactionProblem withVariableSubset(@NonNull Predicate<Variable<?>> variablePredicate) {
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
        private int atLeastNChainCount = 0;

        public <T> Variable<T> createVariable(String name, Domain<T> domain) {
            final var variable = variableFactory.<T>create(name);
            variableDomain(variable, domain);
            return variable;
        }

        @SuppressWarnings("unchecked")
        public <T> Variable<T>[] create1dVariableArray(@NonNull String[] labels, @NonNull String namePrefix, @NonNull Domain<T> domain) {
            final var variables = new Variable[labels.length];
            for (int i = 0; i < labels.length; i++) {
                variables[i] = createVariable(String.format("%s%s", namePrefix, labels[i]), domain);
            }
            return variables;
        }

        @SuppressWarnings("unchecked")
        public <T> Variable<T>[][] create2dVariableArray(@NonNull String[] rows, @NonNull String[] columns, @NonNull String namePrefix, @NonNull Domain<T> domain) {
            final var variables = new Variable[rows.length][columns.length];
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < columns.length; j++) {
                    variables[i][j] = createVariable(String.format("%s%s%s", namePrefix, rows[i], columns[j]), domain);
                }
            }
            return variables;
        }

        /**
         * Register a variable with its domain, enforcing that the domain value type matches the variable type.
         *
         * @param variable the variable to register
         * @param domain the domain of allowed values for the variable
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder variableDomain(@NonNull Variable<T> variable, @NonNull Domain<T> domain) {
            return this.variableDomainEntry(variable, domain);
        }

        public ConstraintSatisfactionProblemBuilder deleteVariable(@NonNull Variable<?> variable) {
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
        public <T> ConstraintSatisfactionProblemBuilder equalsConstraint(@NonNull Variable<T> variable, @NonNull T value) {
            return this.constraint(UnaryValueConstraint.of(variable, value));
        }

        /**
         * Create a binary constraint that constraints two specified variables to have the same value.
         *
         * @param left first variable of the pair
         * @param right second variable of the pair
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder equalsConstraint(@NonNull Variable<T> left, @NonNull Variable<T> right) {
            return this.constraint(BinaryEqualsConstraint.of(left, right));
        }

        /**
         * Create a unary constraint that constrains the specified variable to not take a specified value.
         *
         * @param variable to be constrained
         * @param value the value the variable must not take
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder notEqualsConstraint(@NonNull Variable<T> variable, @NonNull T value) {
            return this.constraint(UnaryNotEqualsConstraint.of(variable, value));
        }

        /**
         * Create a unary constraint that evaluates a typed predicate against the value of a single variable.
         *
         * @param variable the variable to be constrained
         * @param predicate determines whether the variable's value satisfies the constraint
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder predicateConstraint(@NonNull Variable<T> variable, @NonNull Predicate<T> predicate) {
            return this.constraint(UnaryPredicateConstraint.of(variable, predicate));
        }

        /**
         * Create a unary comparator constraint: {@code variable <op> value}.
         * Works with any {@link Number} type that implements {@link Comparable}.
         *
         * @param variable the number variable to constrain
         * @param operator the comparison operator (e.g. {@link Operator#GEQ}, {@link Operator#LT})
         * @param value    the fixed value to compare against
         * @return the builder
         */
        public <N extends Number & Comparable<N>> ConstraintSatisfactionProblemBuilder comparatorConstraint(
                @NonNull Variable<N> variable, @NonNull Operator operator, @NonNull N value) {
            return this.constraint(UnaryComparatorConstraint.of(variable, operator, value));
        }

        /**
         * Create a binary constraint that compares two variables of the same type: {@code left <op> right}.
         * Works with any {@link Comparable} type.
         *
         * @param left     the left variable
         * @param operator the comparison operator (e.g. {@link Operator#LEQ}, {@link Operator#GT})
         * @param right    the right variable
         * @return the builder
         */
        public <T extends Comparable<T>> ConstraintSatisfactionProblemBuilder comparatorConstraint(
                @NonNull Variable<T> left, @NonNull Operator operator, @NonNull Variable<T> right) {
            return this.constraint(BinaryComparatorConstraint.of(left, operator, right));
        }

        /**
         * Create an AllDiff constraint on the specified set of variables, all sharing the same value type.
         *
         * @param variables to be constrained
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder allDiffConstraint(@NonNull Set<Variable<T>> variables) {
            return this.constraint(AllDiffConstraint.<T>builder().variables(variables).build());
        }

        /**
         * Constrain a set of boolean variables so that at most one is {@code true}.
         * Implemented as pairwise binary constraints — each pair cannot both be {@code true}.
         * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param variables the boolean variables to constrain
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atMostOneConstraint(@NonNull Set<Variable<Boolean>> variables) {
            return this.constraint(AtMostOneConstraint.builder().variables(variables).build());
        }

        /**
         * Create a binary boolean connective constraint: {@code left <op> right}, where
         * {@code op} is one of {@link LogicOperator}: AND, OR, XOR, NAND, NOR, XNOR.
         * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param left     the left boolean variable
         * @param operator the boolean connective to apply
         * @param right    the right boolean variable
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder logicConstraint(@NonNull Variable<Boolean> left,
                                                                    @NonNull LogicOperator operator,
                                                                    @NonNull Variable<Boolean> right) {
            return this.constraint(BinaryLogicConstraint.of(left, operator, right));
        }

        /**
         * Constrain a set of boolean variables so that at most {@code n} are {@code true}.
         * For N=1, prefer {@link #atMostOneConstraint(Set)}, which provides an AC3-compatible
         * binary decomposition. Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param variables the boolean variables to constrain
         * @param n         the maximum number of variables that may be {@code true}
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atMostNConstraint(@NonNull Set<Variable<Boolean>> variables, int n) {
            return this.constraint(AtMostNConstraint.builder().variables(variables).n(n).build());
        }

        /**
         * Constrain a set of boolean variables so that at least {@code n} are {@code true}.
         * For partial assignments the constraint is satisfied as long as reaching {@code n} true
         * values is still possible; it only fails when all variables are assigned and fewer than
         * {@code n} are {@code true}. Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * <p><b>Solver recommendation:</b> preferred for local search via {@link io.github.rcrida.jcsp.solver.LocalSolver}, where
         * it participates directly in conflict detection and value weighting without the overhead of
         * auxiliary variables. For backtracking search via {@link io.github.rcrida.jcsp.solver.Solver.Factory},
         * consider {@link #atLeastNConstraintWithCounting(Set, int)} to enable AC3 and node consistency
         * propagation through a carry-chain.
         *
         * @param variables the boolean variables to constrain
         * @param n         the minimum number of variables that must be {@code true}
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atLeastNConstraint(@NonNull Set<Variable<Boolean>> variables, int n) {
            return this.constraint(AtLeastNConstraint.builder().variables(variables).n(n).build());
        }

        /**
         * Constrain a set of boolean variables so that exactly one is {@code true}.
         * Implemented as pairwise binary constraints — each pair cannot both be {@code true},
         * and an overall constraint when all variables are assigned that exactly one is {@code true}.
         * Suitable for use with {@link io.github.rcrida.jcsp.domains.BooleanDomain}.
         *
         * @param variables the boolean variables to constrain
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder exactlyOneConstraint(@NonNull Set<Variable<Boolean>> variables) {
            if (variables.size() == 1) {
                return this.constraint(UnaryValueConstraint.of(variables.iterator().next(), true));
            }
            return this.constraint(ExactlyOneConstraint.builder().variables(variables).build());
        }

        /**
         * Create a binary constraint that a pair of variables cannot take the same value.
         *
         * @param left first variable of the pair
         * @param right second variable of the pair
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder notEqualsConstraint(@NonNull Variable<T> left, @NonNull Variable<T> right) {
            return this.constraint(BinaryNotEqualsConstraint.of(left, right));
        }

        /**
         * Create a sequence of binary not-equals constraints to ensure that each variable in the specified list
         * cannot take the same value as its neighbours in the list.
         *
         * @param variables a list of variables, neighbours in the list cannot have the same value
         * @return the builder
         */
        /**
         * Constrain a sequence of variables to be non-decreasing: {@code vars[0] <= vars[1] <= ... <= vars[n-1]}.
         * Equivalent to MiniZinc's {@code increasing(vars)}.
         *
         * @param variables ordered list of variables to constrain
         * @return the builder
         */
        public <T extends Comparable<T>> ConstraintSatisfactionProblemBuilder increasingConstraint(@NonNull List<Variable<T>> variables) {
            return this.constraint(IncreasingConstraint.of(variables));
        }

        /**
         * Constrain a sequence of variables to be non-increasing: {@code vars[0] >= vars[1] >= ... >= vars[n-1]}.
         * Equivalent to MiniZinc's {@code decreasing(vars)}.
         *
         * @param variables ordered list of variables to constrain
         * @return the builder
         */
        public <T extends Comparable<T>> ConstraintSatisfactionProblemBuilder decreasingConstraint(@NonNull List<Variable<T>> variables) {
            return this.constraint(DecreasingConstraint.of(variables));
        }

        /**
         * Constrain two equal-length sequences of variables to be lexicographically ordered:
         * {@code left <op> right}. Use {@link Operator#LT} for strict lex-less,
         * {@link Operator#LEQ} for lex-less-or-equal. Equivalent to MiniZinc's
         * {@code lex_less(left, right)} and {@code lex_lesseq(left, right)}.
         *
         * @param left     the left sequence
         * @param operator the comparison operator (typically {@link Operator#LT} or {@link Operator#LEQ})
         * @param right    the right sequence; must be the same length as left
         * @return the builder
         */
        public <T extends Comparable<T>> ConstraintSatisfactionProblemBuilder lexConstraint(
                @NonNull List<Variable<T>> left, @NonNull Operator operator, @NonNull List<Variable<T>> right) {
            return this.constraint(LexConstraint.of(left, operator, right));
        }

        /**
         * Create a sequence of binary not-equals constraints to ensure that each variable in the specified list
         * cannot take the same value as its neighbours in the list.
         *
         * @param variables a list of variables, neighbours in the list cannot have the same value
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder notEqualsChainConstraint(@NonNull List<Variable<T>> variables) {
            assert variables.size() > 1;
            val firstIter = variables.iterator();
            val secondIter = variables.iterator();
            secondIter.next();
            while (secondIter.hasNext()) {
                this.constraint(BinaryNotEqualsConstraint.of(firstIter.next(), secondIter.next()));
            }
            return this;
        }

        /**
         * Create a binary constraint on numerical variables. A specified offset is applied to the first variable and the result
         * is compared using the specified operator to the value of the second variable.
         *
         * @param left the first variable
         * @param offset numerical offset, same type as variable domain, can be positive or negative, will be added
         * @param operator the type of comparison to perform, eg ==, !=, &lt;, &lt;=, &gt;=, &gt;
         * @param right the second variable
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder offsetConstraint(@NonNull Variable<N> left, @NonNull N offset, @NonNull Operator operator, @NonNull Variable<N> right) {
            return this.constraint(BinaryOffsetConstraint.of(left, offset, operator, right));
        }

        /**
         * Create a binary array-element constraint: {@code result = array[index]}.
         * The index variable is 1-based. Out-of-bounds indices violate the constraint.
         * Equivalent to MiniZinc's {@code element(index, array, result)} constraint.
         *
         * @param index  variable holding the 1-based array index
         * @param result variable constrained to equal {@code array[index]}
         * @param array  fixed array of values
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder elementConstraint(@NonNull Variable<Integer> index, @NonNull Variable<T> result, @NonNull List<T> array) {
            return this.constraint(BinaryElementConstraint.of(index, result, array));
        }

        /**
         * Create a constraint that compares the sum of a set of numeric variables to a fixed bound:
         * {@code v1 + v2 + ... + vn <op> bound}.
         *
         * @param variables the numeric variables to sum
         * @param operator  the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param bound     the value to compare the sum against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder sumConstraint(@NonNull Set<Variable<N>> variables, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(SumConstraint.of(variables, operator, bound));
        }

        /**
         * Create a weighted-sum (linear) constraint: {@code a1*v1 + a2*v2 + ... <op> bound}.
         * Coefficients and variables are supplied as a map. Equivalent to MiniZinc's
         * {@code linear(coefficients, variables, bound)} constraint.
         *
         * @param coefficients map from variable to its numeric coefficient
         * @param operator     the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param bound        the value to compare the weighted sum against
         * @return the builder
         */
        public <N extends Number> ConstraintSatisfactionProblemBuilder linearConstraint(@NonNull Map<Variable<N>, N> coefficients, @NonNull Operator operator, @NonNull N bound) {
            return this.constraint(LinearConstraint.of(coefficients, operator, bound));
        }

        /**
         * Create a constraint that counts how many variables in a set take a specific value,
         * and compares that count to a bound: {@code count(vars, value) <op> n}.
         *
         * @param variables the variables to count over
         * @param value     the value whose occurrences are counted
         * @param operator  the comparison operator (e.g. {@link Operator#EQ}, {@link Operator#LEQ})
         * @param n         the bound to compare the count against
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder countConstraint(@NonNull Set<Variable<T>> variables, @NonNull T value, @NonNull Operator operator, int n) {
            return this.constraint(CountConstraint.of(variables, value, operator, n));
        }

        /**
         * Create a global cardinality constraint: each value in the map must appear exactly
         * the specified number of times across the variables. Values not in the map are
         * unconstrained (open GCC). Equivalent to MiniZinc's
         * {@code global_cardinality(vars, values, counts)} constraint.
         *
         * @param variables   the variables to constrain
         * @param cardinality map from value to required occurrence count
         * @return the builder
         */
        public <T> ConstraintSatisfactionProblemBuilder globalCardinalityConstraint(@NonNull Set<Variable<T>> variables, @NonNull Map<T, Integer> cardinality) {
            return this.constraint(GlobalCardinalityConstraint.of(variables, cardinality));
        }

        /**
         * Create a constraint defined by a set of permitted assignments: the combined values
         * of the constrained variables must match one of the allowed tuples.
         * All tuples must contain exactly the same variable set.
         * Equivalent to MiniZinc's {@code table(x, t)} constraint.
         *
         * @param tuples the allowed assignments; all must share the same variable set
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder tuplesConstraint(@NonNull Set<Assignment> tuples) {
            return this.constraint(NaryTuplesConstraint.of(tuples));
        }

        /**
         * Create an arbitrary binary constraint using a {@link BiPredicate}.
         *
         * @param left the first variable
         * @param right the second variable
         * @param biPredicate determines whether the specified values of the first and second variables are consistent
         * @return the builder
         */
        public <L, R> ConstraintSatisfactionProblemBuilder biPredicateConstraint(@NonNull Variable<L> left, @NonNull Variable<R> right, @NonNull BiPredicate<L, R> biPredicate) {
            return this.constraint(BinaryPredicateConstraint.of(left, right, biPredicate));
        }

        /**
         * Create an arbitrary constraint using a {@link Predicate<Assignment>}. The predicate will need to dereference
         * the values of the variables it requires for the calculation of the predicate value.
         *
         * @param variables the variables that the predicate will reference
         * @param predicate determines whether the specified assignment is consistent
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder predicateConstraint(@NonNull Set<? extends Variable<?>> variables, @NonNull Predicate<Assignment> predicate) {
            return this.constraint(PredicateConstraint.builder().variables(variables).predicate(predicate).build());
        }

        /**
         * Create a fully reified constraint: {@code indicator <-> body}.
         * The indicator is {@code true} exactly when the body constraint is satisfied.
         *
         * @param indicator boolean variable that captures the body's satisfaction state
         * @param body      the constraint being reified
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder reifyConstraint(@NonNull Variable<Boolean> indicator, @NonNull Constraint body) {
            return this.constraint(ReifiedConstraint.of(indicator, body));
        }

        /**
         * Create a half-reified (implication) constraint: {@code indicator -> body}.
         * When the indicator is {@code true} the body must be satisfied; {@code false} is unconstrained.
         *
         * @param indicator boolean variable that activates the body constraint
         * @param body      the constraint activated by the indicator
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder impliesConstraint(@NonNull Variable<Boolean> indicator, @NonNull Constraint body) {
            return this.constraint(ImplicationConstraint.of(indicator, body));
        }

        /**
         * Encodes {@code atLeastN(vars, n)} as a carry-chain over auxiliary integer counting
         * variables, enabling richer propagation than the plain {@link AtLeastNConstraint}.
         *
         * <p>Introduces {@code k+1} integer variables {@code c[0]..c[k]} with domain {@code {0..k}}
         * and {@code k} boolean negation indicators. Constraints:
         * <ul>
         *   <li>{@code c[0] = 0}</li>
         *   <li>for each {@code v_i}: {@code v_i -> c[i] = c[i-1] + 1} and {@code !v_i -> c[i] = c[i-1]}</li>
         *   <li>{@code c[k] >= n}</li>
         * </ul>
         *
         * <p><b>Solver recommendation:</b> use with {@link io.github.rcrida.jcsp.solver.Solver.Factory}
         * (backtracking search), where node consistency and AC3 propagate through the chain and prune
         * the search space. For local search via {@link io.github.rcrida.jcsp.solver.LocalSolver}, prefer the plain
         * {@link #atLeastNConstraint(Set, int)} — the extra chain variables increase repair cost
         * without providing propagation benefit.
         *
         * @param vars the boolean variables to count
         * @param n    minimum number that must be {@code true}
         * @return the builder
         */
        public ConstraintSatisfactionProblemBuilder atLeastNConstraintWithCounting(@NonNull Set<Variable<Boolean>> vars, int n) {
            val varList = List.copyOf(vars);
            int k = varList.size();
            int id = atLeastNChainCount++;

            val counterLabels = IntStream.rangeClosed(0, k).mapToObj(i -> "[" + i + "]").toArray(String[]::new);
            val counters = create1dVariableArray(counterLabels, "$c_" + id + "_", IntRangeDomain.of(0, k));
            equalsConstraint(counters[0], 0);

            val negLabels = IntStream.range(0, k).mapToObj(i -> "[" + i + "]").toArray(String[]::new);
            val negations = create1dVariableArray(negLabels, "$neg_" + id + "_", BooleanDomain.INSTANCE);

            for (int i = 0; i < k; i++) {
                val v    = varList.get(i);
                val prev = counters[i];
                val curr = counters[i + 1];
                val neg  = negations[i];

                reifyConstraint(neg, UnaryNotEqualsConstraint.of(v, true));

                impliesConstraint(v, BinaryOffsetConstraint.of(prev, 1, Operator.EQ, curr));
                impliesConstraint(neg, BinaryEqualsConstraint.of(curr, prev));
            }

            return comparatorConstraint(counters[k], Operator.GEQ, n);
        }
    }
}
