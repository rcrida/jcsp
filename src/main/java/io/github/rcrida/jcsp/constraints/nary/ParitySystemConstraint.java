package io.github.rcrida.jcsp.constraints.nary;

import io.github.rcrida.jcsp.assignments.Assignment;
import io.github.rcrida.jcsp.consistency.Propagatable;
import io.github.rcrida.jcsp.domains.DiscreteDomain;
import io.github.rcrida.jcsp.domains.Domain;
import io.github.rcrida.jcsp.variables.Variable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A system of parity equations over boolean variables ({@link Boolean} or 0/1 integers): each
 * {@link Row} says the sum of its variables is even or odd. That is a linear system over GF(2), so {@link #propagate} runs Gauss-Jordan
 * elimination on it, which finds in polynomial time what search over the same rows cannot -- an
 * inconsistent system (any row that reduces to {@code 0 = 1}) and every variable the rows jointly
 * pin down, however many rows apart. Each row on its own is only a table, whose propagation sees
 * one row at a time.
 * <p>
 * Meant to be posted <em>in addition to</em> the rows' own constraints, not instead of them: it
 * adds the joint reasoning and takes nothing away.
 * <p>
 * Each call re-reduces the system over the variables still open, folding the assigned ones into the
 * right-hand sides, so its cost is {@code rows² × columns / 64} word operations: the caller bounds
 * the system's size.
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ParitySystemConstraint extends NaryConstraint implements Propagatable {

    /**
     * {@code variables} sum to an even ({@code parity} 0) or odd ({@code parity} 1) number, counting
     * {@code true} and the integer 1 as 1 and {@code false} and 0 as 0, so one row may mix
     * {@link Boolean} variables with 0/1 integer ones.
     */
    public record Row(@NonNull Set<? extends Variable<?>> variables, int parity) {
        public Row {
            assert !variables.isEmpty() : "a parity row needs at least one variable";
            assert parity == 0 || parity == 1 : "parity must be 0 or 1";
        }
    }

    @Getter
    private final List<Row> rows;

    /** The rows as bit matrix, built on first use; derived from {@link #rows}, so not part of equality. */
    @EqualsAndHashCode.Exclude
    private final AtomicReference<Layout> layout = new AtomicReference<>();

    private record Layout(Variable<?>[] columns, long[][] rowBits, int[] parities) {
    }

    public static ParitySystemConstraint of(@NonNull List<Row> rows) {
        assert !rows.isEmpty() : "a parity system needs at least one row";
        Set<Variable<?>> variables = new LinkedHashSet<>();
        rows.forEach(row -> variables.addAll(row.variables()));
        return ParitySystemConstraint.builder().variables(variables).rows(List.copyOf(rows)).build();
    }

    private Layout layout() {
        Layout existing = layout.get();
        if (existing != null) return existing;
        Variable<?>[] columns = getVariables().toArray(new Variable<?>[0]);
        Map<Variable<?>, Integer> columnOf = new HashMap<>();
        for (int i = 0; i < columns.length; i++) columnOf.put(columns[i], i);
        int words = (columns.length + 63) >>> 6;
        long[][] rowBits = new long[rows.size()][words];
        int[] parities = new int[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            parities[r] = rows.get(r).parity();
            for (Variable<?> v : rows.get(r).variables()) {
                int column = columnOf.get(v);
                rowBits[r][column >>> 6] |= 1L << column;
            }
        }
        layout.compareAndSet(null, new Layout(columns, rowBits, parities));
        return layout.get();
    }

    @Override
    public boolean isSatisfiedBy(@NonNull Assignment assignment) {
        for (Row row : rows) {
            if (!assignment.containsAll(row.variables())) continue;
            int sum = 0;
            for (Variable<?> v : row.variables()) sum += bit(assignment.getValueOrNull(v));
            if ((sum & 1) != row.parity()) return false;
        }
        return true;
    }

    /** {@code true} and 1 are 1; {@code false} and 0 are 0. */
    private static int bit(Object value) {
        return value instanceof Boolean flag ? (flag ? 1 : 0) : (Integer) value;
    }

    /** {@code bit} as a value of the same type as {@code sample}: a {@link Boolean} or an {@link Integer}. */
    private static Object valueLike(Object sample, int bit) {
        return sample instanceof Boolean ? (Object) (bit == 1) : (Object) bit;
    }

    /**
     * Folds each assigned variable into its rows' right-hand sides, reduces what is left to
     * reduced row-echelon form, and reads off the result: a zero row with right-hand side 1 is a
     * contradiction, and a row left with one variable pins it. Rows with more variables left give
     * nothing more, which is exactly what elimination cannot derive.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Map<Variable<?>, Domain<?>>> propagate(@NonNull Map<Variable<?>, Domain<?>> domains) {
        Layout layout = layout();
        int columns = layout.columns().length;
        int words = (columns + 63) >>> 6;
        long[] open = new long[words];
        long[] ones = new long[words];
        for (int c = 0; c < columns; c++) {
            DiscreteDomain<?> domain = (DiscreteDomain<?>) domains.get(layout.columns()[c]);
            if (!domain.isSingleton()) {
                open[c >>> 6] |= 1L << c;
            } else if (bit(domain.singleValue().orElseThrow()) == 1) {
                ones[c >>> 6] |= 1L << c;
            }
        }

        int m = rows.size();
        long[][] bits = new long[m][];
        int[] rhs = layout.parities().clone();
        for (int r = 0; r < m; r++) {
            long[] source = layout.rowBits()[r];
            int onesInRow = 0;
            bits[r] = new long[words];
            for (int w = 0; w < words; w++) {
                onesInRow += Long.bitCount(source[w] & ones[w]);
                bits[r][w] = source[w] & open[w];
            }
            rhs[r] ^= onesInRow & 1;
        }

        int rank = 0;
        for (int c = 0; c < columns && rank < m; c++) {
            if ((open[c >>> 6] & (1L << c)) == 0) continue;
            int pivot = rank;
            while (pivot < m && (bits[pivot][c >>> 6] & (1L << c)) == 0) pivot++;
            if (pivot == m) continue;
            swap(bits, rhs, rank, pivot);
            for (int r = 0; r < m; r++) {
                if (r != rank && (bits[r][c >>> 6] & (1L << c)) != 0) {
                    for (int w = 0; w < words; w++) bits[r][w] ^= bits[rank][w];
                    rhs[r] ^= rhs[rank];
                }
            }
            rank++;
        }

        for (int r = rank; r < m; r++) {
            if (rhs[r] == 1) return Optional.empty();
        }
        Map<Variable<?>, Domain<?>> updated = new HashMap<>();
        for (int r = 0; r < rank; r++) {
            int column = soleColumn(bits[r]);
            if (column < 0) continue;
            DiscreteDomain<Object> domain = (DiscreteDomain<Object>) domains.get(layout.columns()[column]);
            Object sample = domain.asCollection().iterator().next();
            if (!domain.contains(valueLike(sample, rhs[r]))) return Optional.empty();
            updated.put(layout.columns()[column], domain.toBuilder().delete(valueLike(sample, 1 - rhs[r])).build());
        }
        return Optional.of(updated);
    }

    private static void swap(long[][] bits, int[] rhs, int a, int b) {
        long[] row = bits[a];
        bits[a] = bits[b];
        bits[b] = row;
        int parity = rhs[a];
        rhs[a] = rhs[b];
        rhs[b] = parity;
    }

    /** The only set bit of {@code row}, or {@code -1} when it has none or several. */
    private static int soleColumn(long[] row) {
        int found = -1;
        for (int w = 0; w < row.length; w++) {
            if (row[w] == 0) continue;
            if (found >= 0 || Long.bitCount(row[w]) > 1) return -1;
            found = (w << 6) + Long.numberOfTrailingZeros(row[w]);
        }
        return found;
    }

    @Override
    public String getRelation() {
        return "ParitySystem[" + rows.size() + " rows, " + getVariables().size() + " variables]";
    }
}
