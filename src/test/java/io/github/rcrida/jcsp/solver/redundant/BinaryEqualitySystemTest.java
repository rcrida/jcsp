package io.github.rcrida.jcsp.solver.redundant;

import io.github.rcrida.jcsp.solver.Cancellation;
import io.github.rcrida.jcsp.solver.redundant.BinaryEqualitySystem.Result;
import io.github.rcrida.jcsp.solver.redundant.BinaryEqualitySystem.Split;
import io.github.rcrida.jcsp.solver.redundant.BinaryEqualitySystem.Status;
import io.github.rcrida.jcsp.solver.redundant.BinaryEqualitySystem.Verdict;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryEqualitySystemTest {
    private static final long BUDGET = 50_000_000L;

    private static Result solve(long[][] a, long[] d) {
        return BinaryEqualitySystem.solve(a, d, Integer.MAX_VALUE, BUDGET, Cancellation.NEVER);
    }

    /** Every 0/1 solution by trying all {@code 2^n} vectors, as sets of bit masks. */
    private static Set<Long> bruteForce(long[][] a, long[] d) {
        int n = a[0].length;
        Set<Long> solutions = new HashSet<>();
        for (long mask = 0; mask < 1L << n; mask++) {
            boolean ok = true;
            for (int i = 0; i < a.length && ok; i++) {
                long sum = 0;
                for (int j = 0; j < n; j++) if ((mask >> j & 1) == 1) sum += a[i][j];
                ok = sum == d[i];
            }
            if (ok) solutions.add(mask);
        }
        return solutions;
    }

    private static Set<Long> masks(Result result) {
        Set<Long> masks = new HashSet<>();
        for (int[] x : result.solutions()) {
            long mask = 0;
            for (int j = 0; j < x.length; j++) mask |= (long) x[j] << j;
            masks.add(mask);
        }
        assertThat(masks).as("no solution is listed twice").hasSize(result.solutions().size());
        return masks;
    }

    private static long[][] randomCoefficients(Random random, int m, int n, int magnitude, boolean negatives) {
        while (true) {
            long[][] a = new long[m][n];
            for (long[] row : a) {
                for (int j = 0; j < n; j++) row[j] = random.nextInt(magnitude + 1) * (negatives && random.nextBoolean() ? -1 : 1);
            }
            boolean everyColumnUsed = true;
            for (int j = 0; j < n; j++) {
                boolean used = false;
                for (long[] row : a) used |= row[j] != 0;
                everyColumnUsed &= used;
            }
            if (everyColumnUsed) return a;
        }
    }

    // ── the property: it agrees with brute force, solution for solution ──

    @Test
    void solve_randomSystems_agreeWithBruteForce() {
        Random random = new Random(20261026L);
        int infeasible = 0;
        int feasible = 0;
        for (int trial = 0; trial < 400; trial++) {
            int m = 1 + random.nextInt(4);
            int n = m + 2 + random.nextInt(11);
            long[][] a = randomCoefficients(random, m, n, trial % 3 == 0 ? 3 : 25, trial % 4 == 0);
            if (trial % 5 == 0 && m > 1) {
                a[m - 1] = a[0].clone();
                for (int j = 0; j < n; j++) if (a[0][j] == 0) a[0][j] = a[m - 1][j] = 1;
            }
            long[] d = new long[m];
            for (int i = 0; i < m; i++) {
                long total = 0;
                for (long c : a[i]) total += c;
                d[i] = switch (trial % 3) {
                    case 0 -> total / 2;
                    case 1 -> random.nextInt((int) Math.max(1, Math.abs(total) + 1)) * (total < 0 ? -1 : 1);
                    default -> 0;
                };
            }
            if (trial % 3 == 2) {
                long[] planted = new long[n];
                for (int j = 0; j < n; j++) planted[j] = random.nextInt(2);
                for (int i = 0; i < m; i++) {
                    long sum = 0;
                    for (int j = 0; j < n; j++) sum += a[i][j] * planted[j];
                    d[i] = sum;
                }
            }
            Set<Long> expected = bruteForce(a, d);

            Result result = solve(a, d);

            String context = "trial " + trial + " m=" + m + " n=" + n + " a=" + Arrays.deepToString(a) + " d=" + Arrays.toString(d);
            if (expected.isEmpty()) {
                assertThat(result.status()).as(context).isEqualTo(Status.INFEASIBLE);
                infeasible++;
            } else {
                assertThat(result.status()).as(context).isEqualTo(Status.SOLUTIONS);
                assertThat(masks(result)).as(context).isEqualTo(expected);
                feasible++;
            }
        }
        assertThat(infeasible).as("the generator exercises both outcomes").isGreaterThan(40);
        assertThat(feasible).isGreaterThan(40);
    }

    // ── known systems ──

    /** {@code MarketSplit-01}: 30 variables, 4 equations, exactly one solution. */
    @Test
    void solve_marketSplit01_hasTheOneSolution() {
        long[][] a = {
                {79, 22, 49, 32, 75, 71, 17, 55, 28, 65, 13, 23, 98, 4, 17, 44, 63, 87, 18, 8, 49, 44, 12, 44, 39, 21, 45, 0, 79, 87},
                {3, 10, 9, 4, 94, 84, 76, 12, 92, 4, 29, 57, 80, 27, 61, 97, 23, 77, 36, 41, 85, 85, 37, 49, 81, 77, 22, 26, 77, 53},
                {65, 32, 64, 27, 36, 58, 11, 12, 22, 55, 69, 51, 12, 49, 30, 26, 98, 53, 55, 34, 46, 92, 71, 84, 41, 52, 61, 15, 30, 90},
                {68, 95, 22, 32, 22, 58, 43, 86, 23, 65, 93, 92, 17, 6, 93, 99, 84, 91, 53, 39, 77, 51, 31, 48, 35, 24, 52, 48, 91, 82}};
        long[] d = {644, 754, 720, 860};

        Result result = solve(a, d);

        assertThat(result.status()).isEqualTo(Status.SOLUTIONS);
        assertThat(result.solutions()).hasSize(1);
        assertThat(result.solutions().get(0)).containsExactly(
                1, 0, 0, 0, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1);
    }

    @Test
    void solve_contradictoryEquations_areInfeasibleOverTheRationals() {
        assertThat(solve(new long[][]{{1, 1}, {1, 1}}, new long[]{1, 2}).status()).isEqualTo(Status.INFEASIBLE);
    }

    @Test
    void solve_noIntegerSolution_isInfeasible() {
        assertThat(solve(new long[][]{{2, 2}}, new long[]{1}).status()).isEqualTo(Status.INFEASIBLE);
    }

    @Test
    void solve_integerSolutionsThatAreNotBinary_areInfeasible() {
        assertThat(solve(new long[][]{{1, 0}, {0, 1}}, new long[]{2, 0}).status()).isEqualTo(Status.INFEASIBLE);
    }

    @Test
    void solve_equationsThatDetermineEveryVariable_listTheOneSolution() {
        Result result = solve(new long[][]{{1, 0}, {0, 1}}, new long[]{1, 0});

        assertThat(result.status()).isEqualTo(Status.SOLUTIONS);
        assertThat(result.solutions()).hasSize(1);
        assertThat(result.solutions().get(0)).containsExactly(1, 0);
    }

    // ── what it will not claim ──

    @Test
    void solve_moreSolutionsThanAllowed_isInconclusive() {
        long[][] a = {{1, 1, 1, 1}};
        long[] d = {2};

        assertThat(BinaryEqualitySystem.solve(a, d, 5, BUDGET, Cancellation.NEVER).status()).isEqualTo(Status.INCONCLUSIVE);
        assertThat(BinaryEqualitySystem.solve(a, d, 6, BUDGET, Cancellation.NEVER).status()).isEqualTo(Status.SOLUTIONS);
        assertThat(BinaryEqualitySystem.solve(a, d, 6, BUDGET, Cancellation.NEVER).solutions()).hasSize(6);
    }

    /** m=5, n=40, no solution: the enumeration is long enough to hit a small budget and the cancellation check. */
    private static long[][] hardCoefficients() {
        Random random = new Random(1L);
        long[][] a = new long[5][40];
        for (long[] row : a) for (int j = 0; j < 40; j++) row[j] = random.nextInt(100);
        return a;
    }

    private static long[] halfSums(long[][] a) {
        long[] d = new long[a.length];
        for (int i = 0; i < a.length; i++) d[i] = Arrays.stream(a[i]).sum() / 2;
        return d;
    }

    @Test
    void solve_nodeBudgetExhausted_isInconclusive() {
        long[][] a = hardCoefficients();

        assertThat(BinaryEqualitySystem.solve(a, halfSums(a), Integer.MAX_VALUE, 1_000, Cancellation.NEVER).status())
                .isEqualTo(Status.INCONCLUSIVE);
    }

    @Test
    void solve_longEnumerationNotCancelled_runsToTheEnd() {
        long[][] a = hardCoefficients();

        assertThat(BinaryEqualitySystem.solve(a, halfSums(a), Integer.MAX_VALUE, BUDGET, Cancellation.NEVER).status())
                .isEqualTo(Status.INFEASIBLE);
    }

    @Test
    void solve_noVariables_throwsAssertionError() {
        assertThatThrownBy(() -> solve(new long[][]{{}}, new long[]{0})).isInstanceOf(AssertionError.class);
    }

    @Test
    void solve_rightHandSideOfTheWrongLength_throwsAssertionError() {
        assertThatThrownBy(() -> solve(new long[][]{{1, 1}}, new long[]{1, 2})).isInstanceOf(AssertionError.class);
    }

    @Test
    void solve_rowsOfDifferentLengths_throwsAssertionError() {
        assertThatThrownBy(() -> solve(new long[][]{{1, 1}, {1}}, new long[]{1, 1})).isInstanceOf(AssertionError.class);
    }

    @Test
    void solve_cancelled_isInconclusive() {
        long[][] a = hardCoefficients();
        Cancellation cancelled = new Cancellation();
        cancelled.cancel();

        assertThat(BinaryEqualitySystem.solve(a, halfSums(a), Integer.MAX_VALUE, Long.MAX_VALUE, cancelled).status())
                .isEqualTo(Status.INCONCLUSIVE);
    }

    @Test
    void solve_aCoefficientTooLargeForLongSums_isInconclusive() {
        assertThat(solve(new long[][]{{1L << 40, 1}}, new long[]{1}).status()).isEqualTo(Status.INCONCLUSIVE);
    }

    @Test
    void solve_aVariableInNoEquation_isInconclusive() {
        assertThat(solve(new long[][]{{1, 0}}, new long[]{1}).status()).isEqualTo(Status.INCONCLUSIVE);
    }

    @Test
    void solve_anEmbeddingWeightTooSmall_isInconclusive() {
        long[][] a = {{3, 5, 7, 2, 9, 4}, {6, 1, 8, 3, 2, 5}};

        assertThat(BinaryEqualitySystem.solve(a, new long[]{12, 10}, Integer.MAX_VALUE, BUDGET, Cancellation.NEVER,
                BigInteger.ONE).status()).isEqualTo(Status.INCONCLUSIVE);
    }

    // ── rank ──

    @Test
    void rank_independentRows() {
        assertThat(BinaryEqualitySystem.rank(new long[][]{{1, 2, 3}, {4, 5, 6}})).isEqualTo(2);
    }

    @Test
    void rank_dependentRowsAndAZeroColumn() {
        assertThat(BinaryEqualitySystem.rank(new long[][]{{1, 0, 2}, {2, 0, 4}, {0, 0, 1}})).isEqualTo(2);
    }

    @Test
    void rank_aZeroMatrix() {
        assertThat(BinaryEqualitySystem.rank(new long[][]{{0, 0}, {0, 0}})).isZero();
    }

    @Test
    void rank_moreRowsThanColumns() {
        assertThat(BinaryEqualitySystem.rank(new long[][]{{1}, {2}, {3}})).isEqualTo(1);
    }

    // ── reading a reduced embedding: hand-made bases for A = [1 1], d = [1], N1 = 100 ──
    // rows are (x1, x2, middle, tail)

    private static final long[][] A = {{1, 1}};
    private static final long[] D = {1};
    private static final BigInteger N1 = BigInteger.valueOf(100);

    private static BigInteger[][] basis(long[]... rows) {
        BigInteger[][] basis = new BigInteger[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            basis[i] = Arrays.stream(rows[i]).mapToObj(BigInteger::valueOf).toArray(BigInteger[]::new);
        }
        return basis;
    }

    private static Split split(int expectedTailZero, long[]... rows) {
        return BinaryEqualitySystem.split(basis(rows), A, D, N1, expectedTailZero);
    }

    @Test
    void split_wellFormedBasis_givesTheParticularSolutionAndKernel() {
        Split split = split(2, new long[]{0, 0, 0, 5}, new long[]{1, -1, 0, 0}, new long[]{-1, 0, -100, 0});

        assertThat(split.verdict()).isEqualTo(Verdict.OK);
        assertThat(split.particular()).containsExactly(1, 0);
        assertThat(split.kernel()).hasDimensions(1, 2);
        assertThat(split.kernel()[0]).containsExactly(BigInteger.ONE, BigInteger.ONE.negate());
    }

    @Test
    void split_positiveMiddleEntry_keepsTheSign() {
        Split split = split(2, new long[]{1, -1, 0, 0}, new long[]{0, 1, 100, 0});

        assertThat(split.verdict()).isEqualTo(Verdict.OK);
        assertThat(split.particular()).containsExactly(0, 1);
    }

    @Test
    void split_wrongNumberOfTailZeroRows_isUnexpected() {
        assertThat(split(3, new long[]{1, -1, 0, 0}, new long[]{0, 1, 100, 0}).verdict()).isEqualTo(Verdict.UNEXPECTED);
    }

    @Test
    void split_twoRowsCarryingTheMiddleEntry_isUnexpected() {
        assertThat(split(2, new long[]{1, 0, 100, 0}, new long[]{0, 1, 100, 0}).verdict()).isEqualTo(Verdict.UNEXPECTED);
    }

    @Test
    void split_noRowCarriesTheMiddleEntry_isUnexpected() {
        assertThat(split(2, new long[]{1, -1, 0, 0}, new long[]{2, -2, 0, 0}).verdict()).isEqualTo(Verdict.UNEXPECTED);
    }

    @Test
    void split_middleEntryNotAMultipleOfTheWeight_isUnexpected() {
        assertThat(split(2, new long[]{1, -1, 0, 0}, new long[]{0, 1, 150, 0}).verdict()).isEqualTo(Verdict.UNEXPECTED);
    }

    @Test
    void split_middleEntryAWholeMultipleAboveOne_meansNoIntegerSolution() {
        assertThat(split(2, new long[]{1, -1, 0, 0}, new long[]{0, 1, 200, 0}).verdict()).isEqualTo(Verdict.INFEASIBLE);
    }

    @Test
    void split_particularEntryTooLargeForALong_isUnexpected() {
        assertThat(split(2, new long[]{1, -1, 0, 0}, new long[]{1L << 40, 0, 100, 0}).verdict()).isEqualTo(Verdict.UNEXPECTED);
    }

    @Test
    void split_kernelEntryTooLargeForALong_isUnexpected() {
        assertThat(split(2, new long[]{1L << 40, -(1L << 40), 0, 0}, new long[]{1, 0, 100, 0}).verdict())
                .isEqualTo(Verdict.UNEXPECTED);
    }

    @Test
    void split_kernelRowThatDoesNotSolveTheHomogeneousSystem_isUnexpected() {
        assertThat(split(2, new long[]{1, 1, 0, 0}, new long[]{1, 0, 100, 0}).verdict()).isEqualTo(Verdict.UNEXPECTED);
    }

    @Test
    void split_particularRowThatDoesNotSolveTheSystem_isUnexpected() {
        assertThat(split(2, new long[]{1, -1, 0, 0}, new long[]{0, 0, 100, 0}).verdict()).isEqualTo(Verdict.UNEXPECTED);
    }
}
