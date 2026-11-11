package io.github.rcrida.jcsp.constraints.nary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Value-level generalized-arc-consistency narrowing for {@code Σ coeffs[i]*vars[i] == bound} over
 * discrete domains — strictly stronger than {@link LinearBoundPropagation}'s own bounds-only
 * {@code EQ} pass, which only reasons about {@code totalMin}/{@code totalMax} and can leave a
 * value in a variable's domain that is numerically within range but not actually reachable by any
 * combination of the other variables' current values (e.g. {@code x1 ∈ {0,3}, x2 ∈ {0,5},
 * x1+x2==4}: bounds consistency sees the achievable range {@code [0,8]}, which contains {@code 4},
 * but no real combination of live values sums to it). {@link LinearBoundPropagation}'s {@code
 * LEQ}/{@code GEQ} passes need no such treatment for the same reason {@link
 * ExtremumPropagation#narrowMaxEqCoverage} documents for {@code max}/{@code min}: an inequality
 * only needs <em>some</em> achievable value to exist, and each variable's own true extreme is
 * always a real, currently-present value — no coverage gap is possible there. Only {@code EQ}
 * needs this.
 * <p>
 * {@link #computeSubsetSumCoverage} is a forward-backward DP over reachable partial sums, the same
 * shape as {@link RegularConstraint}'s forward-backward DP over reachable/productive automaton
 * states, just over achievable sums instead of DFA states: {@code forward[i]} is the set of sums
 * achievable using terms {@code 0..i-1}, {@code backward[i]} the mirror image using terms {@code
 * i..n-1}. A candidate value {@code v} for term {@code i} is GAC-supported iff {@code bound -
 * coeffs[i]*v} is expressible as {@code s1 + s2} for some {@code s1} reachable in {@code
 * forward[i]} and {@code s2} reachable in {@code backward[i+1]}.
 * <p>
 * Deliberately a pure array-level module (no {@link io.github.rcrida.jcsp.domains.Domain}/{@link
 * io.github.rcrida.jcsp.variables.Variable} dependency) — {@link LinearBoundPropagation} owns
 * extracting live values from real domains and narrowing them back, the same split {@link
 * ExtremumPropagation}'s own {@code narrowMaxEqCoverage}/{@code propagateEqCoverage} pair uses,
 * just carried one step further here since this class needs no {@code Domain}-level counterpart at
 * all.
 */
final class SubsetSumCoveragePropagation {
    private SubsetSumCoveragePropagation() {}

    /**
     * Cap on the achievable-sum range ({@code totalMax - totalMin + 1}) {@link
     * #computeSubsetSumCoverage} is willing to build reachability bitsets over. A nonzero integer
     * coefficient maps each of a variable's own distinct live values to a distinct point within
     * this range, so this single cap also bounds live-value enumeration cost per variable —
     * no separate per-domain cardinality guard is needed. Starting value chosen to comfortably
     * cover the XCSP3 competition corpus instances this pass was added for (targets in the
     * hundreds); revisit after benchmarking a wider corpus.
     */
    static final int MAX_REACHABLE_RANGE = 200_000;

    /**
     * Whether {@link #computeSubsetSumCoverage} is worth attempting: the achievable-sum range is
     * small enough for its reachability bitsets to stay bounded. Does <em>not</em> check the
     * operator itself — {@link LinearBoundPropagation} only calls this once it has already
     * restricted to the {@code EQ} case, the only one with a coverage gap to close.
     *
     * @param totalMin the constraint's own achievable minimum sum (as {@link
     *                  LinearBoundPropagation} already computes for its bounds pass)
     * @param totalMax the constraint's own achievable maximum sum
     */
    static boolean eligible(int totalMin, int totalMax) {
        long range = (long) totalMax - totalMin + 1;
        return range <= MAX_REACHABLE_RANGE;
    }

    /**
     * Forward-backward subset-sum DP. {@code values.get(i)} is term {@code i}'s own currently-live
     * values (parallel to {@code coeffs}); {@code minContribs[i]}/{@code maxContribs[i]} are each
     * term's own achievable contribution range ({@code coeffs[i]*v} minimized/maximized over its
     * <em>declared</em> domain — safe to pass a wider range than {@code values.get(i)}'s actual
     * live set, e.g. from before a sibling bounds-consistency pass narrowed it, since every real
     * {@code v} in {@code values.get(i)} was already in that wider domain and so still satisfies
     * {@code coeffs[i]*v >= minContribs[i]}, the invariant the DP's own offset arithmetic relies
     * on to keep every bitset index non-negative).
     *
     * @return {@link Optional#empty()} if no combination reaches {@code bound} at all (a case
     *         bounds consistency alone can miss for gapped domains); otherwise the exact retained
     *         value set for every term, parallel to {@code values} — a term whose retained set
     *         comes back empty also signals infeasibility, since that term's own values were
     *         tested against a bound-consistent, in-range {@code bound}, so an empty result there
     *         is real, not an artifact of the initial range check
     */
    static Optional<List<Set<Integer>>> computeSubsetSumCoverage(
            List<int[]> values, int[] coeffs, int[] minContribs, int[] maxContribs, int bound) {
        int n = coeffs.length;

        // cumMin[i]/cumMax[i]: achievable range using terms 0..i-1 (forward[i]'s own offset/width).
        int[] cumMin = new int[n + 1];
        int[] cumMax = new int[n + 1];
        for (int i = 0; i < n; i++) {
            cumMin[i + 1] = cumMin[i] + minContribs[i];
            cumMax[i + 1] = cumMax[i] + maxContribs[i];
        }
        // sufMin[i]/sufMax[i]: achievable range using terms i..n-1 (backward[i]'s own offset/width).
        int[] sufMin = new int[n + 1];
        int[] sufMax = new int[n + 1];
        for (int i = n - 1; i >= 0; i--) {
            sufMin[i] = sufMin[i + 1] + minContribs[i];
            sufMax[i] = sufMax[i + 1] + maxContribs[i];
        }

        long[][] forward = new long[n + 1][];
        forward[0] = new long[] {1L};
        for (int i = 0; i < n; i++) {
            forward[i + 1] = shiftedUnion(forward[i], deltas(values.get(i), coeffs[i], minContribs[i]),
                    Math.abs(coeffs[i]), cumMax[i + 1] - cumMin[i + 1] + 1);
        }

        long[][] backward = new long[n + 1][];
        backward[n] = new long[] {1L};
        for (int i = n - 1; i >= 0; i--) {
            backward[i] = shiftedUnion(backward[i + 1], deltas(values.get(i), coeffs[i], minContribs[i]),
                    Math.abs(coeffs[i]), sufMax[i] - sufMin[i] + 1);
        }

        List<Set<Integer>> kept = new ArrayList<>(n);
        boolean anyEmpty = false;
        for (int i = 0; i < n; i++) {
            // Every s1 + s2 with s1 reachable in forward[i] and s2 in backward[i + 1], built once so
            // that each value's support is one bit test.
            int width = (cumMax[i] - cumMin[i] + 1) + (sufMax[i + 1] - sufMin[i + 1] + 1) - 1;
            long[] pairSums = pairSums(forward[i], backward[i + 1], width);
            int offset = cumMin[i] + sufMin[i + 1];

            Set<Integer> keptForTerm = new HashSet<>();
            for (int v : values.get(i)) {
                int index = bound - coeffs[i] * v - offset;
                if (index >= 0 && index < width && isSet(pairSums, index)) keptForTerm.add(v);
            }
            if (keptForTerm.isEmpty()) anyEmpty = true;
            kept.add(keptForTerm);
        }

        return anyEmpty ? Optional.empty() : Optional.of(kept);
    }

    /** Each value's contribution above the term's minimum, ascending and without duplicates. */
    private static int[] deltas(int[] values, int coeff, int minContrib) {
        int[] deltas = new int[values.length];
        for (int k = 0; k < values.length; k++) deltas[k] = coeff * values[k] - minContrib;
        return Arrays.stream(deltas).sorted().distinct().toArray();
    }

    /**
     * The union of {@code reachable} shifted by each of {@code deltas}, as a bit set {@code width}
     * bits wide. Deltas {@code step} apart are taken as one run and shifted by doubling, so a term
     * over an integer range costs about {@code log2} of its size in whole-word shifts rather than one
     * bit-by-bit pass per value.
     */
    private static long[] shiftedUnion(long[] reachable, int[] deltas, int step, int width) {
        long[] result = new long[wordsFor(width)];
        long[] run = new long[result.length];
        for (int start = 0, end; start < deltas.length; start = end) {
            end = start + 1;
            while (end < deltas.length && deltas[end] - deltas[end - 1] == step) end++;
            Arrays.fill(run, 0L);
            System.arraycopy(reachable, 0, run, 0, reachable.length);
            // run |= run << step, << 2·step, ... until it holds shifts 0, step, ..., (count - 1)·step.
            int count = end - start;
            int covered = 1;
            while (covered * 2 <= count) {
                orShifted(run, run, covered * step);
                covered *= 2;
            }
            if (covered < count) orShifted(run, run, (count - covered) * step);
            orShifted(run, result, deltas[start]);
        }
        return result;
    }

    /** {@code {a + b}} over the bits {@code a} of {@code left} and {@code b} of {@code right}, {@code width} bits wide. */
    private static long[] pairSums(long[] left, long[] right, int width) {
        boolean leftSmaller = cardinality(left) <= cardinality(right);
        return shiftedUnion(leftSmaller ? right : left, setBits(leftSmaller ? left : right), 1, width);
    }

    /**
     * {@code target |= source << shift}, dropping bits past {@code target}'s end. Walks downwards so
     * that {@code source} may be {@code target}: every write lands at or above the word being read,
     * and a word below has not been read yet.
     */
    private static void orShifted(long[] source, long[] target, int shift) {
        int wordShift = shift >>> 6;
        int bitShift = shift & 63;
        for (int i = Math.min(source.length - 1, target.length - 1 - wordShift); i >= 0; i--) {
            long word = source[i];
            int j = i + wordShift;
            target[j] |= word << bitShift;
            if (bitShift != 0 && j + 1 < target.length) target[j + 1] |= word >>> (64 - bitShift);
        }
    }

    private static int wordsFor(int bits) {
        return (bits + 63) >>> 6;
    }

    private static boolean isSet(long[] bits, int index) {
        return (bits[index >>> 6] & (1L << index)) != 0;
    }

    private static int cardinality(long[] bits) {
        int count = 0;
        for (long word : bits) count += Long.bitCount(word);
        return count;
    }

    /** The indices of {@code bits}' set bits, ascending. */
    private static int[] setBits(long[] bits) {
        int[] indices = new int[cardinality(bits)];
        int k = 0;
        for (int w = 0; w < bits.length; w++) {
            for (long word = bits[w]; word != 0; word &= word - 1) {
                indices[k++] = (w << 6) + Long.numberOfTrailingZeros(word);
            }
        }
        return indices;
    }
}
