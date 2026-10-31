package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.constraints.Operator;
import io.github.rcrida.jcsp.domains.BoundedDomain;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.rcrida.jcsp.constraints.nary.LexConstraint.domainMax;
import static io.github.rcrida.jcsp.constraints.nary.LexConstraint.domainMin;

/**
 * A whole chain of rows ordered lexicographically at once: {@code rows[0] <op> rows[1] <op> ... <op>
 * rows[m-1]}, with {@code <op>} one of {@link Operator#LT}, {@link Operator#LEQ}, {@link
 * Operator#GT} or {@link Operator#GEQ}. Satisfied exactly when every consecutive pair is, as a
 * chain of {@link LexConstraint}s is, and like those it is optimistic for a partial assignment.
 * <p>
 * It exists because a chain of pairwise constraints only ever lets neighbouring rows talk to each
 * other, one pair at a time. Here the rows are propagated together: see {@link #propagate}.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LexChainConstraint<T extends Comparable<T>> extends NaryConstraint implements Propagatable {
    private static final Set<Operator> ORDERINGS = EnumSet.of(Operator.LT, Operator.LEQ, Operator.GT, Operator.GEQ);

    @Singular("row") private final List<List<Variable<T>>> rows;
    @NonNull private final Operator operator;

    public static <T extends Comparable<T>> LexChainConstraint<T> of(
            @NonNull List<? extends List<? extends Variable<T>>> rows, @NonNull Operator operator) {
        assert !rows.isEmpty() : "LexChainConstraint needs at least one row";
        assert rows.stream().allMatch(row -> row.size() == rows.get(0).size()) : "LexChainConstraint needs equal-length rows";
        assert ORDERINGS.contains(operator) : "LexChainConstraint orders rows: LT, LEQ, GT or GEQ, got " + operator;
        List<List<Variable<T>>> copy = new ArrayList<>();
        Set<Variable<?>> variables = new HashSet<>();
        for (List<? extends Variable<T>> row : rows) {
            copy.add(List.copyOf(row));
            variables.addAll(row);
        }
        return LexChainConstraint.<T>builder().variables(variables).rows(copy).operator(operator).build();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (int i = 0; i + 1 < rows.size(); i++) {
            if (!pairSatisfied(rows.get(i), rows.get(i + 1), assignment)) return false;
        }
        return true;
    }

    /** {@code left <op> right} for one pair, optimistic about an unassigned position, as {@link LexConstraint} is. */
    private boolean pairSatisfied(List<Variable<T>> left, List<Variable<T>> right, Assignment assignment) {
        for (int j = 0; j < left.size(); j++) {
            Optional<T> l = assignment.getValue(left.get(j));
            Optional<T> r = assignment.getValue(right.get(j));
            if (l.isEmpty() || r.isEmpty()) return true;
            int cmp = l.get().compareTo(r.get());
            if (cmp != 0) return operator.compare(Integer.signum(cmp), 0);
        }
        return operator.compare(0, 0);
    }

    /**
     * Narrows every row to the lexicographic interval the chain leaves it, to a fixpoint, or finds
     * the chain infeasible.
     * <p>
     * Put the chain in ascending order (the rows reversed for {@code >=} and {@code >}), so each row
     * is at most the next, strictly for {@code <} and {@code >}. A row can then be no smaller than
     * the smallest vector its own domains allow that is at least as large as {@code lower[i-1]}, and
     * no larger than the largest allowed vector that is at most {@code upper[i+1]}. Both are computed
     * in one sweep each, down the chain and back up it, and read domain <em>values</em> rather than
     * bounds, so a gap in a domain can make a tie impossible, which turns "at most" into "below" at
     * that position.
     * <p>
     * Every solution has row {@code i} between {@code lower[i]} and {@code upper[i]}, by induction
     * along the chain, so a row is narrowed to that interval: the positions where the two bounds
     * agree are fixed to that value, and at the first position where they differ the domain is
     * clipped to the values between them. Nothing is inferred beyond that position.
     * <p>
     * A variable that occurs in several rows, or twice in one, is treated independently at each
     * occurrence, which relaxes the chain: it can only make this weaker, never unsound. Domains that
     * are {@link BoundedDomain}s are not propagated at all.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Map<Variable<?>, Domain<?>> working = UNCHANGED;
        int n = rows.get(0).size();
        if (n == 0) return operator.compare(0, 0) ? Optional.of(working) : Optional.empty();
        boolean strict = operator == Operator.LT || operator == Operator.GT;
        boolean reversed = operator == Operator.GEQ || operator == Operator.GT;
        int m = rows.size();
        boolean shared = getVariables().size() < m * n;
        boolean changed = true;
        while (changed) {
            changed = false;
            Domain<T>[][] dom = (Domain<T>[][]) new Domain<?>[m][];
            for (int i = 0; i < m; i++) {
                List<Variable<T>> row = rows.get(reversed ? m - 1 - i : i);
                dom[i] = (Domain<T>[]) new Domain<?>[n];
                for (int j = 0; j < n; j++) {
                    Domain<T> d = current(row.get(j), working, domains);
                    if (d instanceof BoundedDomain<?>) return Optional.of(Map.of());
                    dom[i][j] = d;
                }
            }
            Object[][] lower = new Object[m][];
            Object[][] upper = new Object[m][];
            lower[0] = extremes(dom[0], false);
            for (int i = 1; i < m; i++) {
                lower[i] = nearest(dom[i], lower[i - 1], strict, true);
                if (lower[i] == null) return Optional.empty();
            }
            // The upper sweep cannot fail once the lower one has succeeded: the lower vectors are a
            // valid chain, so each row can take at least those under the next row's upper vector.
            upper[m - 1] = extremes(dom[m - 1], true);
            for (int i = m - 2; i >= 0; i--) {
                upper[i] = nearest(dom[i], upper[i + 1], strict, false);
            }
            for (int i = 0; i < m; i++) {
                int p = firstDifference(lower[i], upper[i]);
                List<Variable<T>> row = rows.get(reversed ? m - 1 - i : i);
                for (int j = 0; j < n && j <= p; j++) {
                    T lo = (T) lower[i][j];
                    T hi = j < p ? lo : (T) upper[i][j];
                    Domain<T> before = current(row.get(j), working, domains);
                    Domain<T> after = restrict(before, lo, hi);
                    if (after.size() != before.size()) {
                        if (after.isEmpty()) return Optional.empty();
                        working = withEntry(working, row.get(j), after);
                        changed = true;
                    }
                }
            }
            // Clipping a row to [lower, upper] leaves them its smallest and largest vectors, and the
            // other rows' bounds are computed from those, so another pass finds nothing new -- unless
            // a variable occurs more than once, when one occurrence's clipping can move another's bounds.
            changed &= shared;
        }
        return Optional.of(working);
    }

    /** What {@link #propagate} starts from, so a call that narrows nothing allocates no map. */
    private static final Map<Variable<?>, Domain<?>> UNCHANGED = Collections.emptyMap();

    private static Map<Variable<?>, Domain<?>> withEntry(Map<Variable<?>, Domain<?>> working, Variable<?> variable,
                                                         Domain<?> domain) {
        Map<Variable<?>, Domain<?>> writable = working == UNCHANGED ? new HashMap<>() : working;
        writable.put(variable, domain);
        return writable;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> Domain<T> current(Variable<T> variable, Map<Variable<?>, Domain<?>> working,
                                                               Map<Variable<?>, Domain<?>> domains) {
        Domain<?> narrowed = working.get(variable);
        return (Domain<T>) (narrowed != null ? narrowed : domains.get(variable));
    }

    /** The vector of every position's smallest (or, with {@code largest}, largest) value. */
    private Object[] extremes(Domain<T>[] row, boolean largest) {
        Object[] vector = new Object[row.length];
        for (int j = 0; j < row.length; j++) vector[j] = largest ? domainMax(row[j]) : domainMin(row[j]);
        return vector;
    }

    /**
     * The smallest vector the row's domains allow that is at least {@code bound} (strictly above it
     * when {@code strict}), or with {@code up == false} the largest that is at most it (strictly below);
     * {@code null} when there is none.
     * <p>
     * The bound itself qualifies when the row can take every one of its values and the order is not
     * strict. Otherwise the answer shares the longest prefix with the bound that the row can take
     * and steps past it at the next position, to the nearest value on the right side of the bound,
     * then takes the extreme value everywhere after: a longer shared prefix gives a vector nearer
     * the bound than any that departs earlier.
     */
    @SuppressWarnings("unchecked")
    private Object[] nearest(Domain<T>[] row, Object[] bound, boolean strict, boolean up) {
        int n = row.length;
        int prefix = 0;
        while (prefix < n && row[prefix].contains(bound[prefix])) prefix++;
        if (prefix == n && !strict) return bound.clone();
        for (int k = Math.min(prefix, n - 1); k >= 0; k--) {
            T step = up ? smallestAbove(row[k], (T) bound[k]) : largestBelow(row[k], (T) bound[k]);
            if (step != null) {
                Object[] vector = new Object[n];
                System.arraycopy(bound, 0, vector, 0, k);
                vector[k] = step;
                for (int j = k + 1; j < n; j++) vector[j] = up ? domainMin(row[j]) : domainMax(row[j]);
                return vector;
            }
        }
        return null;
    }

    /**
     * The first position at which {@code lower} and {@code upper} differ, or their length if none.
     * {@code lower} is never above {@code upper} here: the lower vectors are themselves a valid chain
     * once they exist, and each upper vector is the largest that fits under the next, so it is at
     * least the lower one by induction from the end of the chain.
     */
    private int firstDifference(Object[] lower, Object[] upper) {
        int j = 0;
        while (j < lower.length && lower[j].equals(upper[j])) j++;
        return j;
    }

    private static <T extends Comparable<T>> T smallestAbove(Domain<T> domain, T value) {
        if (domainMax(domain).compareTo(value) <= 0) return null;
        if (domainMin(domain).compareTo(value) > 0) return domainMin(domain);
        T best = null;
        for (T candidate : ((DiscreteDomain<T>) domain).asCollection()) {
            if (candidate.compareTo(value) > 0 && (best == null || candidate.compareTo(best) < 0)) best = candidate;
        }
        return best;
    }

    private static <T extends Comparable<T>> T largestBelow(Domain<T> domain, T value) {
        if (domainMin(domain).compareTo(value) >= 0) return null;
        if (domainMax(domain).compareTo(value) < 0) return domainMax(domain);
        T best = null;
        for (T candidate : ((DiscreteDomain<T>) domain).asCollection()) {
            if (candidate.compareTo(value) < 0 && (best == null || candidate.compareTo(best) > 0)) best = candidate;
        }
        return best;
    }

    /** {@code domain} without any value outside {@code [lo, hi]}; the same object when there is none. */
    private static <T extends Comparable<T>> Domain<T> restrict(Domain<T> domain, T lo, T hi) {
        if (domainMin(domain).compareTo(lo) >= 0 && domainMax(domain).compareTo(hi) <= 0) return domain;
        DiscreteDomain<T> discrete = (DiscreteDomain<T>) domain;
        DiscreteDomain.Builder<T> builder = discrete.toBuilder();
        for (T value : discrete.asCollection()) {
            if (value.compareTo(lo) < 0 || value.compareTo(hi) > 0) builder.delete(value);
        }
        return builder.build();
    }

    /**
     * Cites every variable of the chain when each is a singleton, which is then a complete assignment
     * the chain rejects; otherwise there is no reason to give and the caller falls back to the
     * assignment as a whole. Sound if not minimal: the verdict is a function of the domains, so
     * singletons fix everything it read.
     */
    @Override
    public Optional<NogoodConstraint> explainInfeasible(@NonNull Map<Variable<?>, Domain<?>> domains) {
        return GroundNogoodConstraint.fromReason(Propagatable.allSingletonReason(getVariables(), domains));
    }

    @Override
    public String getRelation() {
        return rows.stream()
                .map(row -> row.stream().map(Object::toString).collect(Collectors.joining(", ", "[", "]")))
                .collect(Collectors.joining(" " + operator.symbol + " "));
    }
}
