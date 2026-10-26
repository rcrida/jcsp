package io.github.rcrida.jcsp.solver.redundant;

import io.github.rcrida.jcsp.solver.Cancellation;
import org.jspecify.annotations.NonNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decides a system of linear equalities {@code A x = d} over 0/1 variables, and lists every solution
 * when there are few, by the Aardal-Hurkens-Lenstra lattice reformulation.
 * <p>
 * The integer solutions of {@code A x = d} are {@code x0 + B λ}, with {@code B} a basis of the
 * kernel lattice of {@code A}. The embedding below delivers {@code x0} and {@code B} from one
 * {@link IntegerLattice#reduce LLL} reduction, and the reduced {@code B} is short. A 0/1 vector is at
 * squared distance exactly {@code n/4} from {@code (1/2, ..., 1/2)}, and every other integer vector is
 * at least {@code n/4 + 2}, so the solutions are the lattice points of the coset within {@code n/4}
 * of that centre, which a Fincke-Pohst enumeration over the reduced basis finds.
 * <p>
 * Never answers wrongly because it ran out: a node budget, a {@link Cancellation}, too many
 * solutions to list, or a reduced basis that is not shaped as the embedding promises all give
 * {@link Status#INCONCLUSIVE}. Every listed solution is a genuine one. A refutation, or "these are
 * all", rests on the enumeration never pruning a branch it should keep; it prunes in doubles, so
 * its radius carries {@link #MARGIN} to cover the rounding.
 */
final class BinaryEqualitySystem {
    enum Status {
        /** No 0/1 vector satisfies the system. */
        INFEASIBLE,
        /** {@link Result#solutions} holds every solution. */
        SOLUTIONS,
        /** Not decided: see the class comment. */
        INCONCLUSIVE
    }

    /**
     * @param solutions every 0/1 solution when {@code status} is {@link Status#SOLUTIONS}, else empty
     */
    record Result(@NonNull Status status, @NonNull List<int[]> solutions) {
        static final Result INFEASIBLE = new Result(Status.INFEASIBLE, List.of());
        static final Result INCONCLUSIVE = new Result(Status.INCONCLUSIVE, List.of());
    }

    /** Added to the squared radius {@code n/4}; integer vectors off the 0/1 cube are at least 2 beyond it. */
    static final double MARGIN = 1e-6;
    /** Enumeration nodes between looks at the {@link Cancellation}. */
    static final long CANCELLATION_INTERVAL = 1 << 16;
    /** Coefficients above this make the {@code long} sums unsafe. */
    static final long MAX_COEFFICIENT = Integer.MAX_VALUE;

    private BinaryEqualitySystem() {
    }

    /**
     * @param coefficients  {@code A}, {@code m} rows of {@code n} integers; every column must have a
     *                      nonzero entry, which a variable that occurs in the system always has
     * @param rhs           {@code d}
     * @param maxSolutions  more solutions than this is {@link Status#INCONCLUSIVE}
     * @param nodeBudget    enumeration nodes allowed before giving up
     */
    static @NonNull Result solve(long[] @NonNull [] coefficients, long @NonNull [] rhs, int maxSolutions,
                                 long nodeBudget, @NonNull Cancellation cancellation) {
        return solve(coefficients, rhs, maxSolutions, nodeBudget, cancellation,
                BigInteger.ONE.shiftLeft(coefficients[0].length / 2 + 8));
    }

    /**
     * As above, with the embedding weight {@code n1} chosen by the caller: {@code N1 = n1} and
     * {@code N2 = 1024·n1}. The default is {@code 2^(n/2 + 8)}, well past what the embedding needs
     * for the sizes this is used on; a smaller one is how a test makes the reduced basis come out
     * unlike what {@link #split} expects.
     */
    static @NonNull Result solve(long[] @NonNull [] coefficients, long @NonNull [] rhs, int maxSolutions,
                                 long nodeBudget, @NonNull Cancellation cancellation, @NonNull BigInteger n1) {
        int m = coefficients.length;
        int n = coefficients[0].length;
        assert n > 0 && rhs.length == m : "the system must have variables and one right-hand side per equation";
        assert Arrays.stream(coefficients).allMatch(row -> row.length == n) : "rows must have equal length";
        if (!isSafe(coefficients)) return Result.INCONCLUSIVE;

        int rank = rank(coefficients);
        if (rank(augmented(coefficients, rhs)) > rank) return Result.INFEASIBLE;

        BigInteger[][] basis = embedding(coefficients, rhs, n, n1);
        IntegerLattice.reduce(basis);
        Split split = split(basis, coefficients, rhs, n1, n + 1 - rank);
        if (split.verdict() == Verdict.INFEASIBLE) return Result.INFEASIBLE;
        if (split.verdict() == Verdict.UNEXPECTED) return Result.INCONCLUSIVE;

        BigInteger[][] kernel = split.kernel();
        IntegerLattice.reduce(kernel);
        return new Enumeration(n, split.particular(), toLongs(kernel), maxSolutions, nodeBudget, cancellation).run();
    }

    private static boolean isSafe(long[][] coefficients) {
        int n = coefficients[0].length;
        for (int j = 0; j < n; j++) {
            boolean nonzero = false;
            for (long[] row : coefficients) {
                if (Math.abs(row[j]) > MAX_COEFFICIENT) return false;
                nonzero |= row[j] != 0;
            }
            if (!nonzero) return false;
        }
        return true;
    }

    private static long[][] augmented(long[][] coefficients, long[] rhs) {
        long[][] result = new long[coefficients.length][];
        for (int i = 0; i < result.length; i++) {
            result[i] = Arrays.copyOf(coefficients[i], coefficients[i].length + 1);
            result[i][coefficients[i].length] = rhs[i];
        }
        return result;
    }

    /**
     * The rows {@code (e_j, 0, N2·A_j)} for each variable and {@code (0, N1, -N2·d)}, of length
     * {@code n + 1 + m}: a lattice vector whose last {@code m} entries are zero is a solution of
     * {@code A x = t·d} for the {@code t} in its middle entry.
     */
    private static BigInteger[][] embedding(long[][] coefficients, long[] rhs, int n, BigInteger n1) {
        int m = coefficients.length;
        BigInteger n2 = n1.shiftLeft(10);
        BigInteger[][] basis = new BigInteger[n + 1][n + 1 + m];
        for (BigInteger[] row : basis) Arrays.fill(row, BigInteger.ZERO);
        for (int j = 0; j < n; j++) {
            basis[j][j] = BigInteger.ONE;
            for (int i = 0; i < m; i++) basis[j][n + 1 + i] = n2.multiply(BigInteger.valueOf(coefficients[i][j]));
        }
        basis[n][n] = n1;
        for (int i = 0; i < m; i++) basis[n][n + 1 + i] = n2.multiply(BigInteger.valueOf(-rhs[i]));
        return basis;
    }

    enum Verdict { OK, INFEASIBLE, UNEXPECTED }

    /**
     * @param particular a solution of {@code A x = d} over the integers, when {@code verdict} is {@link Verdict#OK}
     * @param kernel     a basis of the integer solutions of {@code A x = 0}, likewise
     */
    record Split(@NonNull Verdict verdict, long[] particular, BigInteger[][] kernel) {
        static final Split INFEASIBLE = new Split(Verdict.INFEASIBLE, null, null);
        static final Split UNEXPECTED = new Split(Verdict.UNEXPECTED, null, null);
    }

    /**
     * Reads the kernel basis and a particular solution off the reduced embedding {@code basis}, or
     * says why it cannot.
     * <p>
     * The rows whose last {@code m} entries are zero span exactly the lattice vectors with that
     * property, provided there are {@code expectedTailZero = n + 1 - rank} of them (a row subset of a
     * basis is saturated, so it is all of that sublattice once it has the right rank). Among them the
     * kernel rows have a zero middle entry, and exactly one other has {@code ±g·N1}: if {@code g > 1}
     * the equations have no integer solution, since a solution {@code x} would put {@code (x, 1)}
     * there with {@code g = 1}. Anything else, or entries too large for a {@code long}, or a row that
     * does not satisfy the equations exactly, is {@link Verdict#UNEXPECTED}.
     */
    static @NonNull Split split(BigInteger[] @NonNull [] basis, long[] @NonNull [] coefficients, long @NonNull [] rhs,
                                @NonNull BigInteger n1, int expectedTailZero) {
        int m = coefficients.length;
        int n = coefficients[0].length;
        List<BigInteger[]> tailZero = new ArrayList<>();
        for (BigInteger[] row : basis) {
            boolean zero = true;
            for (int i = 0; i < m; i++) zero &= row[n + 1 + i].signum() == 0;
            if (zero) tailZero.add(row);
        }
        if (tailZero.size() != expectedTailZero) return Split.UNEXPECTED;

        List<BigInteger[]> kernel = new ArrayList<>();
        BigInteger[] particular = null;
        for (BigInteger[] row : tailZero) {
            if (row[n].signum() == 0) {
                kernel.add(row);
            } else if (particular == null) {
                particular = row;
            } else {
                return Split.UNEXPECTED;
            }
        }
        if (particular == null) return Split.UNEXPECTED;
        BigInteger[] multiple = particular[n].abs().divideAndRemainder(n1);
        if (multiple[1].signum() != 0) return Split.UNEXPECTED;
        if (!multiple[0].equals(BigInteger.ONE)) return Split.INFEASIBLE;

        int sign = particular[n].signum();
        long[] x0 = new long[n];
        for (int j = 0; j < n; j++) {
            if (particular[j].bitLength() > 31) return Split.UNEXPECTED;
            x0[j] = sign * particular[j].longValueExact();
        }
        BigInteger[][] kernelRows = new BigInteger[kernel.size()][];
        for (int k = 0; k < kernelRows.length; k++) {
            BigInteger[] head = Arrays.copyOf(kernel.get(k), n);
            for (BigInteger entry : head) if (entry.bitLength() > 31) return Split.UNEXPECTED;
            if (!satisfies(coefficients, toLong(head), new long[m])) return Split.UNEXPECTED;
            kernelRows[k] = head;
        }
        return satisfies(coefficients, x0, rhs) ? new Split(Verdict.OK, x0, kernelRows) : Split.UNEXPECTED;
    }

    private static long[] toLong(BigInteger[] row) {
        long[] result = new long[row.length];
        for (int j = 0; j < row.length; j++) result[j] = row[j].longValueExact();
        return result;
    }

    private static long[][] toLongs(BigInteger[][] rows) {
        long[][] result = new long[rows.length][];
        for (int k = 0; k < rows.length; k++) result[k] = toLong(rows[k]);
        return result;
    }

    private static boolean satisfies(long[][] coefficients, long[] x, long[] rhs) {
        for (int i = 0; i < coefficients.length; i++) {
            long sum = 0;
            for (int j = 0; j < x.length; j++) sum += coefficients[i][j] * x[j];
            if (sum != rhs[i]) return false;
        }
        return true;
    }

    /** The rank of {@code matrix} over the rationals, by fraction-free (Bareiss) elimination. */
    static int rank(long[] @NonNull [] matrix) {
        int rows = matrix.length;
        int columns = matrix[0].length;
        BigInteger[][] work = new BigInteger[rows][columns];
        for (int i = 0; i < rows; i++) for (int j = 0; j < columns; j++) work[i][j] = BigInteger.valueOf(matrix[i][j]);
        int rank = 0;
        BigInteger previous = BigInteger.ONE;
        for (int column = 0; column < columns && rank < rows; column++) {
            int pivot = rank;
            while (pivot < rows && work[pivot][column].signum() == 0) pivot++;
            if (pivot == rows) continue;
            BigInteger[] swap = work[pivot];
            work[pivot] = work[rank];
            work[rank] = swap;
            for (int i = rank + 1; i < rows; i++) {
                for (int j = column + 1; j < columns; j++) {
                    work[i][j] = work[i][j].multiply(work[rank][column])
                            .subtract(work[i][column].multiply(work[rank][j])).divide(previous);
                }
                work[i][column] = BigInteger.ZERO;
            }
            previous = work[rank][column];
            rank++;
        }
        return rank;
    }

    /**
     * Fincke-Pohst over the reduced kernel basis: the integer {@code λ} with {@code ||x0 + Bλ - c||²}
     * at most {@code n/4}, for the centre {@code c = (1/2, ..., 1/2)}. With {@code t = c - x0} and
     * Gram-Schmidt vectors {@code b*_k}, that distance is
     * {@code ||t⊥||² + Σ_k ||b*_k||² (λ_k + Σ_{i>k} μ_{ik} λ_i - τ_k)²}, where {@code t⊥} is the part
     * of {@code t} outside the span of {@code B}, and each level's {@code λ_k} lies in an interval
     * around the bracketed centre.
     */
    private static final class Enumeration {
        private final int n;
        private final long[] x0;
        private final long[][] kernel;
        private final int dimension;
        private final int maxSolutions;
        private final long nodeBudget;
        private final Cancellation cancellation;
        private final double[][] mu;
        private final double[] norm;
        private final double[] tau;
        private final double radiusSquared;
        private final double residualSquared;
        private final long[] lambda;
        private final List<int[]> solutions = new ArrayList<>();
        private long nodes;
        private boolean aborted;

        Enumeration(int n, long[] x0, long[][] kernel, int maxSolutions, long nodeBudget, Cancellation cancellation) {
            this.n = n;
            this.x0 = x0;
            this.kernel = kernel;
            this.dimension = kernel.length;
            this.maxSolutions = maxSolutions;
            this.nodeBudget = nodeBudget;
            this.cancellation = cancellation;
            this.lambda = new long[dimension];
            this.radiusSquared = n / 4.0 + MARGIN;

            double[][] star = new double[dimension][];
            mu = new double[dimension][dimension];
            norm = new double[dimension];
            tau = new double[dimension];
            double[] t = new double[n];
            for (int j = 0; j < n; j++) t[j] = 0.5 - x0[j];
            double[] perpendicular = t.clone();
            for (int k = 0; k < dimension; k++) {
                star[k] = new double[n];
                for (int j = 0; j < n; j++) star[k][j] = kernel[k][j];
                for (int i = 0; i < k; i++) mu[k][i] = dotWithKernel(k, star[i]) / norm[i];
                for (int i = 0; i < k; i++) {
                    for (int j = 0; j < n; j++) star[k][j] -= mu[k][i] * star[i][j];
                }
                norm[k] = dot(star[k], star[k]);
                tau[k] = dot(t, star[k]) / norm[k];
                for (int j = 0; j < n; j++) perpendicular[j] -= tau[k] * star[k][j];
            }
            residualSquared = dot(perpendicular, perpendicular);
        }

        private double dotWithKernel(int k, double[] other) {
            double sum = 0;
            for (int j = 0; j < n; j++) sum += kernel[k][j] * other[j];
            return sum;
        }

        private static double dot(double[] a, double[] b) {
            double sum = 0;
            for (int j = 0; j < a.length; j++) sum += a[j] * b[j];
            return sum;
        }

        Result run() {
            descend(dimension - 1, residualSquared);
            if (aborted) return Result.INCONCLUSIVE;
            return solutions.isEmpty() ? Result.INFEASIBLE : new Result(Status.SOLUTIONS, List.copyOf(solutions));
        }

        private void descend(int level, double used) {
            nodes++;
            if (nodes > nodeBudget || (nodes % CANCELLATION_INTERVAL == 0 && cancellation.isCancelled())) {
                aborted = true;
                return;
            }
            if (level < 0) {
                record();
                return;
            }
            double centre = tau[level];
            for (int i = level + 1; i < dimension; i++) centre -= mu[i][level] * lambda[i];
            double half = Math.sqrt(Math.max(0, radiusSquared - used) / norm[level]);
            long low = (long) Math.ceil(centre - half);
            long high = (long) Math.floor(centre + half);
            for (long value = low; value <= high && !aborted; value++) {
                lambda[level] = value;
                double offset = value - centre;
                descend(level - 1, used + offset * offset * norm[level]);
            }
        }

        private void record() {
            int[] x = new int[n];
            for (int j = 0; j < n; j++) {
                long v = x0[j];
                for (int k = 0; k < dimension; k++) v += lambda[k] * kernel[k][j];
                if (v != 0 && v != 1) return;
                x[j] = (int) v;
            }
            solutions.add(x);
            if (solutions.size() > maxSolutions) aborted = true;
        }
    }
}
