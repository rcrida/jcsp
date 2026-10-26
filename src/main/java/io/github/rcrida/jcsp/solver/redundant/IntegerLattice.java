package io.github.rcrida.jcsp.solver.redundant;

import org.jspecify.annotations.NonNull;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Lattice basis reduction in exact integer arithmetic: Cohen's integral LLL (algorithm 2.6.7) with
 * the usual {@code δ = 3/4}. Floating point is not safe here -- the reformulation that uses it
 * ({@link BinaryEqualitySystem}) embeds the equations with large weights, and a rounding error in a
 * Gram-Schmidt coefficient can silently return a basis of a different lattice.
 * <p>
 * Everything is kept as the integers {@code d[i]} (the Gram determinants of the first {@code i} rows)
 * and {@code lambda[k][j] = d[j] * mu[k][j]}, so no fraction is ever formed. Indices below are
 * 1-based, as in the algorithm; row {@code k} of the basis is {@code basis[k - 1]}.
 */
final class IntegerLattice {
    private IntegerLattice() {
    }

    /**
     * Replaces the rows of {@code basis} with an LLL-reduced basis of the same lattice, in place: each
     * row is size-reduced against the ones before it and consecutive rows satisfy the Lovász
     * condition. Rows must be linearly independent and of equal length.
     *
     * @throws IllegalArgumentException if the rows are linearly dependent
     */
    static void reduce(@NonNull BigInteger[][] basis) {
        int n = basis.length;
        if (n < 2) return;
        assert Arrays.stream(basis).allMatch(row -> row.length == basis[0].length) : "rows must have equal length";
        BigInteger[][] lambda = new BigInteger[n + 1][n + 1];
        for (BigInteger[] row : lambda) Arrays.fill(row, BigInteger.ZERO);
        BigInteger[] d = new BigInteger[n + 1];
        d[0] = BigInteger.ONE;
        d[1] = dot(basis[0], basis[0]);
        if (d[1].signum() == 0) throw new IllegalArgumentException("basis rows are linearly dependent");
        int k = 2;
        int kmax = 1;
        while (k <= n) {
            if (k > kmax) {
                kmax = k;
                orthogonalize(basis, lambda, d, k);
            }
            sizeReduce(basis, lambda, d, k, k - 1);
            if (violatesLovasz(lambda, d, k)) {
                swap(basis, lambda, d, k, kmax);
                k = Math.max(2, k - 1);
            } else {
                for (int l = k - 2; l >= 1; l--) sizeReduce(basis, lambda, d, k, l);
                k++;
            }
        }
    }

    /** Extends the Gram-Schmidt data to row {@code k}. */
    private static void orthogonalize(BigInteger[][] basis, BigInteger[][] lambda, BigInteger[] d, int k) {
        for (int j = 1; j <= k; j++) {
            BigInteger u = dot(basis[k - 1], basis[j - 1]);
            for (int i = 1; i <= j - 1; i++) {
                u = d[i].multiply(u).subtract(lambda[k][i].multiply(lambda[j][i])).divide(d[i - 1]);
            }
            if (j < k) {
                lambda[k][j] = u;
            } else {
                d[k] = u;
            }
        }
        if (d[k].signum() == 0) throw new IllegalArgumentException("basis rows are linearly dependent");
    }

    /** {@code 3/4 · d[k-1]² - lambda[k][k-1]² > d[k] · d[k-2]}, cleared of fractions. */
    private static boolean violatesLovasz(BigInteger[][] lambda, BigInteger[] d, int k) {
        BigInteger left = d[k].multiply(d[k - 2]).shiftLeft(2);
        BigInteger right = d[k - 1].multiply(d[k - 1]).multiply(BigInteger.valueOf(3))
                .subtract(lambda[k][k - 1].multiply(lambda[k][k - 1]).shiftLeft(2));
        return left.compareTo(right) < 0;
    }

    /** Subtracts the nearest-integer multiple of row {@code l} from row {@code k}, if {@code |mu[k][l]| > 1/2}. */
    private static void sizeReduce(BigInteger[][] basis, BigInteger[][] lambda, BigInteger[] d, int k, int l) {
        if (lambda[k][l].shiftLeft(1).abs().compareTo(d[l]) <= 0) return;
        BigInteger q = nearestQuotient(lambda[k][l], d[l]);
        BigInteger[] row = basis[k - 1];
        BigInteger[] other = basis[l - 1];
        for (int c = 0; c < row.length; c++) row[c] = row[c].subtract(q.multiply(other[c]));
        lambda[k][l] = lambda[k][l].subtract(q.multiply(d[l]));
        for (int i = 1; i <= l - 1; i++) lambda[k][i] = lambda[k][i].subtract(q.multiply(lambda[l][i]));
    }

    /** Exchanges rows {@code k - 1} and {@code k}, updating the Gram-Schmidt data of rows up to {@code kmax}. */
    private static void swap(BigInteger[][] basis, BigInteger[][] lambda, BigInteger[] d, int k, int kmax) {
        BigInteger[] row = basis[k - 1];
        basis[k - 1] = basis[k - 2];
        basis[k - 2] = row;
        for (int j = 1; j <= k - 2; j++) {
            BigInteger t = lambda[k][j];
            lambda[k][j] = lambda[k - 1][j];
            lambda[k - 1][j] = t;
        }
        BigInteger l = lambda[k][k - 1];
        BigInteger b = d[k - 2].multiply(d[k]).add(l.multiply(l)).divide(d[k - 1]);
        for (int i = k + 1; i <= kmax; i++) {
            BigInteger t = lambda[i][k];
            lambda[i][k] = d[k].multiply(lambda[i][k - 1]).subtract(l.multiply(t)).divide(d[k - 1]);
            lambda[i][k - 1] = b.multiply(t).add(l.multiply(lambda[i][k])).divide(d[k]);
        }
        d[k - 1] = b;
    }

    /** The integer nearest {@code numerator / denominator}, for a positive {@code denominator}; halves round up. */
    private static BigInteger nearestQuotient(BigInteger numerator, BigInteger denominator) {
        BigInteger shifted = numerator.shiftLeft(1).add(denominator);
        BigInteger step = denominator.shiftLeft(1);
        return shifted.subtract(shifted.mod(step)).divide(step);
    }

    private static BigInteger dot(BigInteger[] a, BigInteger[] b) {
        BigInteger sum = BigInteger.ZERO;
        for (int i = 0; i < a.length; i++) sum = sum.add(a[i].multiply(b[i]));
        return sum;
    }
}
