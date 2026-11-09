package io.github.rcrida.jcsp.solver.neighbourhood;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.solver.LinearObjective;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * {@link NeighbourhoodGenerator.Factory#COST_WEIGHTED}: frees decisions one at a time, each by a small
 * tournament.
 * <p>
 * A pool of candidates is drawn, half the time from the decisions that share constraints with the one
 * freed just before (more likely the more narrowly a constraint relates them: a shared constraint counts
 * {@code 1 / arity}) and topped up at random, and the winner is the candidate with the highest
 * <em>cost</em> divided by one more than the number of times it has been freed before. A decision's cost
 * is the {@code |coefficient * value|} summed over the objective terms it shares a constraint with in the
 * incumbent, so the decisions that spend the objective are freed more often, and the divisor keeps the
 * same few from being freed on every run. A solve whose objective is not a {@link LinearObjective} has no
 * costs, and the tournament then only spreads the freeing around.
 * <p>
 * Constraints over more than {@value #MAX_RELATION_ARITY} variables relate nothing in particular to their
 * members and are ignored when working out what is related.
 */
final class CostWeightedNeighbourhood implements NeighbourhoodGenerator {
    private static final int TOURNAMENT = 10;
    private static final int MAX_RELATION_ARITY = 12;

    private final ConstraintSatisfactionProblem csp;
    private final Map<Variable<? extends Number>, Double> coefficients;
    private final Map<Variable<?>, Integer> freedBefore = new HashMap<>();
    /** The structure is fixed for a solve, so each variable's neighbours are worked out once. */
    private final Map<Variable<?>, Set<Variable<?>>> related = new HashMap<>();
    private @Nullable Assignment costedFor;
    private Map<Variable<?>, Double> costs = Map.of();

    CostWeightedNeighbourhood(@NonNull ConstraintSatisfactionProblem csp,
                              @NonNull ToDoubleFunction<Assignment> objective) {
        this.csp = csp;
        this.coefficients = objective instanceof LinearObjective linear ? linear.getCoefficients() : Map.of();
    }

    @Override
    public Set<Variable<?>> free(@NonNull List<Variable<?>> decisions, @NonNull Assignment incumbent, int count,
                                 @NonNull Random random) {
        if (incumbent != costedFor) {
            costs = costsOf(decisions, incumbent);
            costedFor = incumbent;
        }
        Set<Variable<?>> remaining = new LinkedHashSet<>(decisions);
        Set<Variable<?>> freed = new LinkedHashSet<>();
        Variable<?> previous = null;
        while (freed.size() < count) {
            List<Variable<?>> pool = new ArrayList<>();
            if (previous != null && random.nextBoolean()) {
                pool.addAll(neighboursOf(previous, remaining, random));
            }
            topUp(pool, remaining, random);
            Variable<?> best = winnerOf(pool, random);
            freed.add(best);
            remaining.remove(best);
            freedBefore.merge(best, 1, Integer::sum);
            previous = best;
        }
        return freed;
    }

    /** Up to a tournament's worth of {@code remaining} decisions related to {@code from}, drawn in proportion to how closely. */
    private List<Variable<?>> neighboursOf(Variable<?> from, Set<Variable<?>> remaining, Random random) {
        Map<Variable<?>, Double> weight = new LinkedHashMap<>();
        for (Constraint constraint : csp.getConstraintsTouching(from)) {
            Set<Variable<?>> variables = constraint.getVariables();
            if (variables.size() > MAX_RELATION_ARITY) continue;
            for (Variable<?> other : variables) {
                if (remaining.contains(other)) weight.merge(other, 1.0 / variables.size(), Double::sum);
            }
        }
        // Weighted sampling without replacement: an exponential draw scaled by the weight, smallest first.
        return weight.entrySet().stream()
                .map(entry -> new Drawn(entry.getKey(), -Math.log(1 - random.nextDouble()) / entry.getValue()))
                .sorted(Comparator.comparingDouble(Drawn::key))
                .limit(TOURNAMENT)
                .<Variable<?>>map(Drawn::variable)
                .toList();
    }

    private record Drawn(Variable<?> variable, double key) {}

    private static void topUp(List<Variable<?>> pool, Set<Variable<?>> remaining, Random random) {
        List<Variable<?>> shuffled = new ArrayList<>(remaining);
        Collections.shuffle(shuffled, random);
        for (Variable<?> candidate : shuffled) {
            if (pool.size() >= TOURNAMENT) break;
            if (!pool.contains(candidate)) pool.add(candidate);
        }
    }

    /** The pool member with the best cost per time freed, each score nudged by up to 10% so ties and near-ties are not always settled the same way. */
    private Variable<?> winnerOf(List<Variable<?>> pool, Random random) {
        Variable<?> best = pool.get(0);
        double bestScore = -1;
        for (Variable<?> candidate : pool) {
            double score = (costs.getOrDefault(candidate, 0.0) + 1e-9)
                    / (1 + freedBefore.getOrDefault(candidate, 0)) * (0.9 + 0.2 * random.nextDouble());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private Map<Variable<?>, Double> costsOf(List<Variable<?>> decisions, Assignment incumbent) {
        Map<Variable<?>, Double> terms = new HashMap<>();
        coefficients.forEach((variable, coefficient) -> terms.put(variable,
                Math.abs(coefficient * incumbent.getValue(variable).orElseThrow().doubleValue())));
        Map<Variable<?>, Double> byDecision = new HashMap<>();
        for (Variable<?> decision : decisions) {
            double cost = 0;
            for (Variable<?> neighbour : relatedTo(decision)) cost += terms.getOrDefault(neighbour, 0.0);
            byDecision.put(decision, cost);
        }
        return byDecision;
    }

    private Set<Variable<?>> relatedTo(Variable<?> variable) {
        return related.computeIfAbsent(variable, v -> {
            Set<Variable<?>> neighbours = new LinkedHashSet<>();
            for (Constraint constraint : csp.getConstraintsTouching(v)) {
                if (constraint.getVariables().size() > MAX_RELATION_ARITY) continue;
                for (Variable<?> other : constraint.getVariables()) {
                    if (other != v) neighbours.add(other);
                }
            }
            return neighbours;
        });
    }
}
