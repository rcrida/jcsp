package io.github.rcrida.jcsp.solver;

import io.github.rcrida.jcsp.ConstraintSatisfactionProblem;
import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.constraints.Constraint;
import io.github.rcrida.jcsp.constraints.nary.NaryConflictTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.NaryTuplesConstraint;
import io.github.rcrida.jcsp.constraints.nary.ParitySystemConstraint;
import io.github.rcrida.jcsp.constraints.nary.ParitySystemConstraint.Row;
import io.github.rcrida.jcsp.variables.Variable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the parity equations a problem states as tables, and adds the {@link ParitySystemConstraint}s
 * that reason about them jointly. The tables themselves stay: the systems are redundant, so nothing
 * a solution must satisfy changes, and the solver works on the augmented copy while the caller's
 * problem is left alone.
 * <p>
 * A table is a parity row when its tuples are exactly all those of one parity over its distinct
 * variables: {@code 2^(k-1)} tuples, each holding only {@code 0}/{@code 1} integers or
 * {@code false}/{@code true}, all with the same number of ones modulo 2 -- the tuples are distinct
 * and there are as many as that parity has, so they are all of them. A conflict table of the
 * opposite parity says the same. Rows sharing variables are grouped, and a group is posted when it
 * has at least {@link #MIN_ROWS} rows, at most {@link #MAX_ROWS} (each propagation re-eliminates the
 * whole system) and at least one row of three or more variables -- groups of pairs are
 * (dis)equalities, which arc consistency already handles.
 */
final class ParitySystems {
    static final int MAX_ARITY = 20;
    static final int MIN_ROWS = 2;
    static final int MAX_ROWS = 512;

    private ParitySystems() {
    }

    /** {@code csp} plus a system for each group of its parity tables, or {@code csp} itself when it has none. */
    static @NonNull ConstraintSatisfactionProblem withParitySystems(@NonNull ConstraintSatisfactionProblem csp) {
        List<Row> rows = new ArrayList<>();
        for (Constraint constraint : csp.getConstraints()) {
            if (constraint instanceof NaryTuplesConstraint table) {
                rowOf(table.getTuples(), false).ifPresent(rows::add);
            } else if (constraint instanceof NaryConflictTuplesConstraint table) {
                rowOf(table.getConflicts(), true).ifPresent(rows::add);
            }
        }
        List<ParitySystemConstraint> systems = systemsOf(rows);
        if (systems.isEmpty()) return csp;
        ConstraintSatisfactionProblem.ConstraintSatisfactionProblemBuilder builder = csp.toBuilder();
        systems.forEach(builder::constraint);
        return builder.build();
    }

    /**
     * The row {@code tuples} state, if they are every tuple of one parity. {@code forbidden} says
     * they are the disallowed combinations, so the row has the other parity.
     */
    static Optional<Row> rowOf(Set<Assignment> tuples, boolean forbidden) {
        if (tuples.isEmpty()) return Optional.empty();
        Set<Variable<?>> variables = tuples.iterator().next().variables();
        int arity = variables.size();
        if (arity < 2 || arity > MAX_ARITY || tuples.size() != 1 << (arity - 1)) return Optional.empty();
        int parity = -1;
        for (Assignment tuple : tuples) {
            int ones = 0;
            for (Variable<?> variable : variables) {
                int bit = bitOf(tuple.getValueOrNull(variable));
                if (bit < 0) return Optional.empty();
                ones += bit;
            }
            if (parity >= 0 && parity != (ones & 1)) return Optional.empty();
            parity = ones & 1;
        }
        return Optional.of(new Row(variables, forbidden ? 1 - parity : parity));
    }

    /** 0 or 1 for {@code false}/{@code 0} or {@code true}/{@code 1}, and {@code -1} for anything else. */
    private static int bitOf(Object value) {
        if (value instanceof Boolean flag) return flag ? 1 : 0;
        if (value instanceof Integer number && (number == 0 || number == 1)) return number;
        return -1;
    }

    /** One system per group of rows sharing variables that is worth posting. */
    static List<ParitySystemConstraint> systemsOf(List<Row> rows) {
        int[] parent = new int[rows.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        Map<Variable<?>, Integer> firstRowOf = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            for (Variable<?> v : rows.get(i).variables()) {
                Integer first = firstRowOf.putIfAbsent(v, i);
                if (first != null) parent[root(parent, i)] = root(parent, first);
            }
        }
        Map<Integer, List<Row>> groups = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            groups.computeIfAbsent(root(parent, i), key -> new ArrayList<>()).add(rows.get(i));
        }
        return groups.values().stream()
                .filter(group -> group.size() >= MIN_ROWS && group.size() <= MAX_ROWS)
                .filter(group -> group.stream().anyMatch(row -> row.variables().size() >= 3))
                .map(ParitySystemConstraint::of)
                .toList();
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
