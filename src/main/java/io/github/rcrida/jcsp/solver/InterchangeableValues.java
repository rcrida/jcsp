package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Valuation;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.constraints.binary.BinaryEqualsConstraint;
import io.github.rcrida.jcsp.constraints.binary.BinaryNotEqualsConstraint;
import io.github.rcrida.jcsp.constraints.nary.AllDiffConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConflictTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.NogoodConstraint;
import io.github.rcrida.jcsp.constraints.nary.RelationLogicConstraint;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Values that every constraint of a problem treats alike, such as the colours of a graph colouring:
 * any permutation of them maps a solution to a solution, so every dead end has a copy under each
 * renaming. Branching on one value no variable holds yet is then as good as branching on any other,
 * and {@link #restrict} keeps only the first.
 * <p>
 * That stays complete under any variable order and any pruning entailed by the problem: if an
 * unheld value has been pruned, no solution extends the assignment with it, so none extends it
 * with any other unheld value either. It drops symmetric solutions, so it is only for a search
 * after one solution.
 */
final class InterchangeableValues {

    private InterchangeableValues() {
    }

    /**
     * Whether {@code csp}'s values are interchangeable: every variable has the same discrete domain
     * of at least two values, and every constraint other than a learned nogood -- which is entailed,
     * so cannot cut a solution -- relates variables only by equality, which no renaming of values
     * changes. A constraint of any other kind, such as an objective cut, makes it {@code false}.
     */
    static boolean hold(ConstraintSatisfactionProblem csp) {
        Set<Object> values = null;
        for (Domain<?> domain : csp.getVariableDomains().values()) {
            if (!(domain instanceof DiscreteDomain<?> discrete)) return false;
            Set<Object> these = new HashSet<>(discrete.toList());
            if (values == null) {
                values = these;
            } else if (!values.equals(these)) {
                return false;
            }
        }
        if (values == null || values.size() < 2) return false;
        for (Constraint constraint : csp.getConstraints()) {
            if (!(constraint instanceof NogoodConstraint) && !invariant(constraint, values)) return false;
        }
        return true;
    }

    private static boolean invariant(Constraint constraint, Set<Object> values) {
        if (constraint instanceof BinaryEqualsConstraint<?> || constraint instanceof BinaryNotEqualsConstraint<?>
                || constraint instanceof AllDiffConstraint<?>) {
            return true;
        }
        if (constraint instanceof RelationLogicConstraint relation) {
            return comparesVariables(relation.getLeft()) && comparesVariables(relation.getRight());
        }
        if (constraint instanceof NaryTuplesConstraint table) {
            return closedUnderRenaming(List.copyOf(table.getVariables()), table.getTuples(), values);
        }
        if (constraint instanceof NaryConflictTuplesConstraint table) {
            return closedUnderRenaming(List.copyOf(table.getVariables()), table.getConflicts(), values);
        }
        return false;
    }

    private static boolean comparesVariables(RelationLogicConstraint.Literal literal) {
        return literal instanceof RelationLogicConstraint.VariableLiteral variableLiteral
                && (variableLiteral.operator() == Operator.EQ || variableLiteral.operator() == Operator.NEQ);
    }

    /**
     * Whether every renaming of {@code values} maps {@code tuples} onto itself. A renaming keeps
     * which positions of a tuple hold equal values, its pattern, and reaches every tuple of that
     * pattern, so the set is closed exactly when, for each pattern it holds, it holds all of them:
     * one per way of giving the pattern's blocks distinct values.
     */
    private static boolean closedUnderRenaming(List<Variable<?>> scope, Set<Valuation> tuples, Set<Object> values) {
        Map<List<Integer>, Integer> perPattern = new HashMap<>();
        for (Valuation tuple : tuples) {
            List<Integer> pattern = new ArrayList<>(scope.size());
            List<Object> blocks = new ArrayList<>();
            for (Variable<?> variable : scope) {
                Object value = tuple.getValueOrNull(variable);
                if (!values.contains(value)) return false;
                int block = blocks.indexOf(value);
                if (block < 0) {
                    block = blocks.size();
                    blocks.add(value);
                }
                pattern.add(block);
            }
            perPattern.merge(pattern, 1, Integer::sum);
        }
        for (Map.Entry<List<Integer>, Integer> entry : perPattern.entrySet()) {
            int blocks = entry.getKey().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            // Stops once past the table's own size, which no pattern of it can reach, before the
            // product of a wide scope over a wide domain can overflow.
            long renamings = 1;
            for (int i = 0; i < blocks && renamings <= tuples.size(); i++) renamings *= values.size() - i;
            if (entry.getValue() != renamings) return false;
        }
        return true;
    }

    /**
     * {@code candidates}, in order, keeping every value some variable of {@code assignment} holds and
     * the first that none does.
     */
    static List<Object> restrict(List<Object> candidates, Valuation assignment) {
        Set<Object> held = new HashSet<>();
        assignment.forEach((variable, value) -> held.add(value));
        List<Object> kept = new ArrayList<>(candidates.size());
        boolean unheldKept = false;
        for (Object value : candidates) {
            if (held.contains(value)) {
                kept.add(value);
            } else if (!unheldKept) {
                kept.add(value);
                unheldKept = true;
            }
        }
        return kept;
    }
}
