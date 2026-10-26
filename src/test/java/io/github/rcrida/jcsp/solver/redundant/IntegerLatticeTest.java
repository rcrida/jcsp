package io.github.rcrida.jcsp.solver.redundant;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegerLatticeTest {

    private static BigInteger[][] rows(long[][] values) {
        BigInteger[][] basis = new BigInteger[values.length][];
        for (int i = 0; i < values.length; i++) {
            basis[i] = new BigInteger[values[i].length];
            for (int j = 0; j < values[i].length; j++) basis[i][j] = BigInteger.valueOf(values[i][j]);
        }
        return basis;
    }

    private static BigInteger[][] copy(BigInteger[][] basis) {
        BigInteger[][] copy = new BigInteger[basis.length][];
        for (int i = 0; i < basis.length; i++) copy[i] = basis[i].clone();
        return copy;
    }

    private static BigInteger[][] random(Random random, int rows, int columns, int magnitude) {
        BigInteger[][] basis = new BigInteger[rows][columns];
        for (BigInteger[] row : basis) {
            for (int j = 0; j < columns; j++) row[j] = BigInteger.valueOf(random.nextInt(2 * magnitude + 1) - magnitude);
        }
        return basis;
    }

    /** The Gram determinant {@code det(B Bᵀ)}, by fraction-free (Bareiss) elimination. */
    private static BigInteger gramDeterminant(BigInteger[][] basis) {
        int n = basis.length;
        BigInteger[][] g = new BigInteger[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                BigInteger sum = BigInteger.ZERO;
                for (int c = 0; c < basis[i].length; c++) sum = sum.add(basis[i][c].multiply(basis[j][c]));
                g[i][j] = sum;
            }
        }
        BigInteger previous = BigInteger.ONE;
        for (int k = 0; k < n - 1; k++) {
            assertThat(g[k][k].signum()).as("a leading minor of a Gram matrix of independent rows is positive").isPositive();
            for (int i = k + 1; i < n; i++) {
                for (int j = k + 1; j < n; j++) {
                    g[i][j] = g[i][j].multiply(g[k][k]).subtract(g[i][k].multiply(g[k][j])).divide(previous);
                }
            }
            previous = g[k][k];
        }
        return g[n - 1][n - 1];
    }

    /** Gram-Schmidt in doubles, enough to check the two LLL conditions to a tolerance. */
    private static void assertReduced(BigInteger[][] basis) {
        int n = basis.length;
        int columns = basis[0].length;
        double[][] b = new double[n][columns];
        for (int i = 0; i < n; i++) for (int c = 0; c < columns; c++) b[i][c] = basis[i][c].doubleValue();
        double[][] star = new double[n][];
        double[] norm = new double[n];
        double[][] mu = new double[n][n];
        for (int i = 0; i < n; i++) {
            star[i] = b[i].clone();
            for (int j = 0; j < i; j++) {
                double dot = 0;
                for (int c = 0; c < columns; c++) dot += b[i][c] * star[j][c];
                mu[i][j] = dot / norm[j];
                for (int c = 0; c < columns; c++) star[i][c] -= mu[i][j] * star[j][c];
            }
            for (int c = 0; c < columns; c++) norm[i] += star[i][c] * star[i][c];
        }
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) assertThat(Math.abs(mu[i][j])).as("size-reduced mu[%d][%d]", i, j).isLessThanOrEqualTo(0.5 + 1e-9);
            double bound = (0.75 - mu[i][i - 1] * mu[i][i - 1]) * norm[i - 1];
            assertThat(norm[i]).as("Lovász at %d", i).isGreaterThanOrEqualTo(bound - 1e-9 * Math.max(1, norm[i - 1]));
        }
    }

    @Test
    void reduce_emptyOrSingleRow_isUnchanged() {
        BigInteger[][] none = new BigInteger[0][];
        IntegerLattice.reduce(none);
        BigInteger[][] one = rows(new long[][]{{3, 4}});
        IntegerLattice.reduce(one);

        assertThat(none).isEmpty();
        assertThat(one[0]).containsExactly(BigInteger.valueOf(3), BigInteger.valueOf(4));
    }

    @Test
    void reduce_orthonormalBasis_isUnchanged() {
        BigInteger[][] basis = rows(new long[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}});
        IntegerLattice.reduce(basis);

        assertThat(basis).isDeepEqualTo(rows(new long[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}));
    }

    @Test
    void reduce_longSkewedRow_isSizeReducedAgainstTheShortOne() {
        BigInteger[][] basis = rows(new long[][]{{1, 0}, {1000, 1}});
        IntegerLattice.reduce(basis);

        assertThat(basis).isDeepEqualTo(rows(new long[][]{{1, 0}, {0, 1}}));
    }

    @Test
    void reduce_negativeMultiple_isSizeReducedToo() {
        BigInteger[][] basis = rows(new long[][]{{1, 0}, {-1000, 1}});
        IntegerLattice.reduce(basis);

        assertThat(basis).isDeepEqualTo(rows(new long[][]{{1, 0}, {0, 1}}));
    }

    @Test
    void reduce_swapsARowThatIsMuchLongerThanItsSuccessor() {
        BigInteger[][] basis = rows(new long[][]{{100, 0}, {1, 1}});
        IntegerLattice.reduce(basis);

        assertThat(basis).isDeepEqualTo(rows(new long[][]{{1, 1}, {50, -50}}));
        assertReduced(basis);
    }

    @Test
    void reduce_randomBases_keepTheLatticeAndSatisfyTheLllConditions() {
        Random random = new Random(20261026L);
        for (int trial = 0; trial < 60; trial++) {
            int rows = 2 + random.nextInt(9);
            int columns = rows + random.nextInt(4);
            BigInteger[][] basis = random(random, rows, columns, trial % 2 == 0 ? 9 : 1_000_000);
            BigInteger before = gramDeterminant(copy(basis));

            IntegerLattice.reduce(basis);

            assertThat(gramDeterminant(copy(basis))).as("trial %d: the same lattice", trial).isEqualTo(before);
            assertReduced(basis);
        }
    }

    @Test
    void reduce_wideBasisWithHugeEntries_staysExact() {
        Random random = new Random(7L);
        BigInteger[][] basis = random(random, 14, 16, 1 << 29);
        BigInteger before = gramDeterminant(copy(basis));

        IntegerLattice.reduce(basis);

        assertThat(gramDeterminant(copy(basis))).isEqualTo(before);
        assertReduced(basis);
    }

    @Test
    void reduce_rowsOfDifferentLengths_throwsAssertionError() {
        assertThatThrownBy(() -> IntegerLattice.reduce(rows(new long[][]{{1, 2}, {3}})))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void reduce_zeroFirstRow_throws() {
        assertThatThrownBy(() -> IntegerLattice.reduce(rows(new long[][]{{0, 0}, {1, 2}})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reduce_repeatedRow_throws() {
        assertThatThrownBy(() -> IntegerLattice.reduce(rows(new long[][]{{1, 2, 3}, {4, 5, 6}, {1, 2, 3}})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reduce_rowThatIsACombinationOfEarlierOnes_throws() {
        assertThatThrownBy(() -> IntegerLattice.reduce(rows(new long[][]{{1, 0, 2}, {0, 1, 3}, {2, 3, 13}})))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
