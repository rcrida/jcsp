package io.github.rcrida.jcsp.solver.redundant;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.nary.LinearBoundConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.SumBoundConstraint;
import io.github.rcrida.jcsp.constraints.unary.UnaryPredicateConstraint;
import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds the systems of linear equalities over 0/1 variables a problem states, solves each once with
 * {@link BinaryEqualitySystem}, and builds the constraints that say what the answer was, for
 * {@link RedundantConstraints} to add. The equations themselves stay.
 * <p>
 * An equation is an {@code EQ} {@link LinearBoundConstraint} or {@link SumBoundConstraint} with an
 * integral bound and integral coefficients, over variables whose domain holds only the integers 0
 * and 1. Equations sharing a variable (with a nonzero coefficient) form a group, and a group is
 * solved when it has at least {@link #MIN_EQUATIONS} equations -- one is a subset-sum, which
 * {@link io.github.rcrida.jcsp.constraints.nary.SubsetSumCoveragePropagation} handles -- and at most
 * {@link #MAX_VARIABLES} variables. A variable with a zero coefficient everywhere is not in the
 * system.
 * <p>
 * The answer becomes an {@link NaryTuplesConstraint} listing every solution (at most {@link
 * #MAX_SOLUTIONS}), or, when there is none, an always-false {@link UnaryPredicateConstraint}, which
 * node consistency turns into an empty domain. When {@link BinaryEqualitySystem} cannot decide
 * within its budget nothing is added, so the group is left to search.
 */
final class LatticeSystems {
    static final int MIN_EQUATIONS = 2;
    static final int MAX_VARIABLES = 100;
    static final int MAX_SOLUTIONS = 4096;
    /** About a second of enumeration. */
    static final long NODE_BUDGET = 32_000_000L;

    private static final Comparator<Variable<?>> BY_NAME = Comparator.comparing(Variable::getName);

    private LatticeSystems() {
    }

    /** The constraints for each of {@code csp}'s equation groups that {@link BinaryEqualitySystem} decides. */
    static @NonNull List<Constraint> systemsIn(@NonNull ConstraintSatisfactionProblem csp,
                                               @NonNull Cancellation cancellation) {
        return systemsIn(csp, NODE_BUDGET, cancellation);
    }

    /** As {@link #systemsIn(ConstraintSatisfactionProblem, Cancellation)}, with {@code nodeBudget} enumeration nodes per group. */
    static @NonNull List<Constraint> systemsIn(@NonNull ConstraintSatisfactionProblem csp, long nodeBudget,
                                               @NonNull Cancellation cancellation) {
        List<Equation> equations = new ArrayList<>();
        for (Constraint constraint : csp.getConstraints()) {
            equationOf(csp, constraint).ifPresent(equations::add);
        }
        List<Constraint> constraints = new ArrayList<>();
        for (List<Equation> group : groupsOf(equations)) {
            decide(group, nodeBudget, cancellation).ifPresent(constraints::add);
        }
        return constraints;
    }

    /** {@code Σ coefficient · variable = rhs}, over the variables with a nonzero coefficient. */
    private record Equation(Map<Variable<?>, Long> coefficients, long rhs) {
    }

    private static Optional<Equation> equationOf(ConstraintSatisfactionProblem csp, Constraint constraint) {
        Map<Variable<?>, Long> coefficients = new LinkedHashMap<>();
        long rhs;
        if (constraint instanceof LinearBoundConstraint<?> linear
                && linear.getOperator() == Operator.EQ && isIntegral(linear.getBound())) {
            for (Map.Entry<? extends Variable<?>, ? extends Number> entry : linear.getCoefficients().entrySet()) {
                if (!isIntegral(entry.getValue())) return Optional.empty();
                if (entry.getValue().longValue() != 0) coefficients.put(entry.getKey(), entry.getValue().longValue());
            }
            rhs = linear.getBound().longValue();
        } else if (constraint instanceof SumBoundConstraint<?> sum
                && sum.getOperator() == Operator.EQ && isIntegral(sum.getBound())) {
            for (Variable<?> variable : sum.getVariables()) coefficients.put(variable, 1L);
            rhs = sum.getBound().longValue();
        } else {
            return Optional.empty();
        }
        if (coefficients.isEmpty()) return Optional.empty();
        for (Variable<?> variable : coefficients.keySet()) {
            if (!Pigeonholes.isBitDomain(csp.getVariableDomains().get(variable))) return Optional.empty();
        }
        return Optional.of(new Equation(coefficients, rhs));
    }

    private static boolean isIntegral(Number number) {
        return number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long;
    }

    /** The equations connected through shared variables, in order of their first equation. */
    private static List<List<Equation>> groupsOf(List<Equation> equations) {
        int[] parent = new int[equations.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        Map<Variable<?>, Integer> firstEquationOf = new HashMap<>();
        for (int i = 0; i < equations.size(); i++) {
            for (Variable<?> variable : equations.get(i).coefficients().keySet()) {
                Integer first = firstEquationOf.putIfAbsent(variable, i);
                if (first != null) parent[root(parent, i)] = root(parent, first);
            }
        }
        Map<Integer, List<Equation>> groups = new LinkedHashMap<>();
        for (int i = 0; i < equations.size(); i++) {
            groups.computeIfAbsent(root(parent, i), key -> new ArrayList<>()).add(equations.get(i));
        }
        return List.copyOf(groups.values());
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** The constraint that records {@code group}'s solutions, if it is worth solving and is decided. */
    @SuppressWarnings("unchecked")
    private static Optional<Constraint> decide(List<Equation> group, long nodeBudget, Cancellation cancellation) {
        Set<Variable<?>> distinct = new LinkedHashSet<>();
        group.forEach(equation -> distinct.addAll(equation.coefficients().keySet()));
        if (group.size() < MIN_EQUATIONS || distinct.size() > MAX_VARIABLES) return Optional.empty();
        List<Variable<?>> variables = distinct.stream().sorted(BY_NAME).toList();

        long[][] coefficients = new long[group.size()][variables.size()];
        long[] rhs = new long[group.size()];
        for (int i = 0; i < group.size(); i++) {
            for (int j = 0; j < variables.size(); j++) {
                coefficients[i][j] = group.get(i).coefficients().getOrDefault(variables.get(j), 0L);
            }
            rhs[i] = group.get(i).rhs();
        }

        BinaryEqualitySystem.Result result =
                BinaryEqualitySystem.solve(coefficients, rhs, MAX_SOLUTIONS, nodeBudget, cancellation);
        return switch (result.status()) {
            case INCONCLUSIVE -> Optional.empty();
            case INFEASIBLE -> Optional.of(UnaryPredicateConstraint.of((Variable<Object>) variables.get(0), value -> false));
            case SOLUTIONS -> {
                Set<Assignment> tuples = new LinkedHashSet<>();
                for (int[] solution : result.solutions()) {
                    Map<Variable<?>, Object> values = new TreeMap<>(BY_NAME);
                    for (int j = 0; j < variables.size(); j++) values.put(variables.get(j), solution[j]);
                    tuples.add(Assignment.of(values));
                }
                yield Optional.of(NaryTuplesConstraint.of(tuples));
            }
        };
    }
}
